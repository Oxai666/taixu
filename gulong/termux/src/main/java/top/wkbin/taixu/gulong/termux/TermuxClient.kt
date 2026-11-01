package top.wkbin.taixu.gulong.termux

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

// --- Termux（外部 App）协议常量 ---------------------------------------------------------
internal const val TERMUX_PACKAGE = "com.termux"
internal const val TERMUX_RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService"
internal const val TERMUX_RUN_COMMAND_ACTION = "com.termux.RUN_COMMAND"
internal const val TERMUX_RUN_COMMAND_PERMISSION = "com.termux.permission.RUN_COMMAND"
internal const val TERMUX_BIN_DIR = "/data/data/com.termux/files/usr/bin"
internal const val TERMUX_HOME_DIR = "/data/data/com.termux/files/home"

internal const val EXTRA_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT"
internal const val EXTRA_RESULT_BUNDLE = "result"
internal const val RESULT_KEY_STDOUT = "stdout"
internal const val RESULT_KEY_STDERR = "stderr"
internal const val RESULT_KEY_EXIT_CODE = "exitCode"
internal const val RESULT_KEY_ERR = "err"
internal const val RESULT_KEY_ERRMSG = "errmsg"

private const val TAG = "GulongTermux"

/**
 * 外部 Termux 应用的 RUN_COMMAND 通道。
 *
 * 这不是内置沙箱——它操控的是用户手机上**已经装好的 Termux**，通过 Intent 起
 * `com.termux.app.RunCommandService`，再用一次性广播把结果 Bundle 收回来。
 *
 * 三个必须先满足的前置条件：
 *  1. Termux 已安装（官方 GitHub release 版，Play/F-Droid 版已停更）
 *  2. 本 App 已获 `com.termux.permission.RUN_COMMAND` 运行时权限
 *  3. Termux 侧 `~/.termux/termux.properties` 里 `allow-external-apps=true`，且改完强制停止重开
 *
 * 第 3 条不满足时的表现极具误导性：Intent 不报错、服务正常启动，但结果广播永远不来，
 * 最终只能等到超时。所以 [verify] 必须做一次端到端冒烟测试，而不是只查包名和权限。
 */
object TermuxClient {

    enum class State { NOT_INSTALLED, NO_PERMISSION, READY }

    sealed class VerifyResult {
        data object NotInstalled : VerifyResult()
        data object NoPermission : VerifyResult()
        data object AllowExternalAppsMissing : VerifyResult()
        data object Ok : VerifyResult()
        data class UnexpectedOutput(val stdout: String) : VerifyResult()
        data class OtherError(val message: String) : VerifyResult()
    }

    /** 最近一次端到端冒烟测试成功的时间戳（进程内 + 可持久化）。 */
    @Volatile
    var lastVerifiedOkAtMs: Long = 0L
        private set

    /** 持久化写入口：由设置层注入，让每次成功/清除都落盘。 */
    @Volatile
    var persister: ((Long) -> Unit)? = null

    fun markVerifiedOk() {
        lastVerifiedOkAtMs = System.currentTimeMillis()
        persister?.invoke(lastVerifiedOkAtMs)
    }

    fun clearVerified() {
        lastVerifiedOkAtMs = 0L
        persister?.invoke(lastVerifiedOkAtMs)
    }

    fun restoreVerifiedAt(ms: Long) {
        lastVerifiedOkAtMs = ms
    }

    /** 只看「装了没」「权限有没有」，无法判断 allow-external-apps——那条得靠 [verify]。 */
    fun state(ctx: Context): State {
        val pm = ctx.packageManager
        val installed = try {
            pm.getPackageInfo(TERMUX_PACKAGE, 0); true
        } catch (_: Throwable) {
            false
        }
        if (!installed) return State.NOT_INSTALLED
        val granted = ContextCompat.checkSelfPermission(ctx, TERMUX_RUN_COMMAND_PERMISSION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) return State.NO_PERMISSION
        return State.READY
    }

    /**
     * 跑一条极小的 `echo` 冒烟测试，走完整条链路并等结果。
     * 只有最终 stdout 里带回我们的标记才算真的通——这同时验证了清单权限、运行时权限、
     * termux.properties 里的 allow-external-apps、以及 Termux 允许起后台会话。
     */
    suspend fun verify(ctx: Context, timeoutMs: Long = TermuxRuntime.verifyTimeoutMs): VerifyResult {
        when (state(ctx)) {
            State.NOT_INSTALLED -> return VerifyResult.NotInstalled
            State.NO_PERMISSION -> return VerifyResult.NoPermission
            State.READY -> Unit
        }
        val result = runCommandCapture(
            ctx = ctx,
            executable = "$TERMUX_BIN_DIR/bash",
            arguments = arrayOf("-c", "echo GULONG_OK"),
            workingDir = TERMUX_HOME_DIR,
            timeoutMs = timeoutMs,
        )
        return when (result) {
            is CaptureResult.Success ->
                if (result.stdout.contains("GULONG_OK")) VerifyResult.Ok
                else VerifyResult.UnexpectedOutput(result.stdout)
            is CaptureResult.Timeout -> VerifyResult.AllowExternalAppsMissing
            is CaptureResult.Denied -> VerifyResult.NoPermission
            is CaptureResult.OtherError -> VerifyResult.OtherError(result.message)
        }
    }
}

/** 一次 Termux 命令调用的结果。 */
sealed class CaptureResult {
    data class Success(
        val stdout: String,
        val stderr: String,
        val exitCode: Int,
    ) : CaptureResult()

    data object Timeout : CaptureResult()
    data object Denied : CaptureResult()
    data class OtherError(val message: String) : CaptureResult()
}

/**
 * 把 [executable]/[arguments] 包一层，让真实命令经 `bash -c` 跑，跑完无论退出码多少
 * 都打印一条本次调用专属的 [marker]。
 *
 * `bash -c <script> <name> <args...>` 会把 `$0` 绑成 `<name>`、`$@` 绑成其余参数，
 * 所以原可执行文件正好落在 `$0` 槽位，中间不需要 `--` 分隔符
 * （加 `--` 会把可执行文件顶到 `$1`，跑错东西）。
 * 参数始终保持独立 argv 项、绝不拼进脚本文本，空格、`$(...)`、`;` 都能原样存活。纯函数。
 */
internal fun buildMarkerWrappedArgv(
    bashPath: String,
    executable: String,
    arguments: Array<String>,
    marker: String,
): Pair<String, Array<String>> {
    val script = "\"\$0\" \"\$@\"; ec=\$?; printf '\\n%s' '$marker'; exit \$ec"
    return bashPath to (arrayOf("-c", script, executable, *arguments))
}

/**
 * 判断一个结果 Bundle 是不是真的「跑完了」，而不是 Termux 立刻发出的启动 ACK。
 *
 * 启动 ACK 的形状是 `err=-1, exitCode=0`、stdout/stderr 全空——与「成功但无输出」逐字节相同，
 * 只靠字段根本分不开。所以只要 `err != -1` 或 `exitCode != 0` 就一定是终局（拒权或真失败）；
 * 否则（ACK 形状）只有 stdout 带上了本次调用的 [marker] 才算终局。
 *
 * Termux 的 StreamGobbler 每重组一行就补一个 `\n`（marker 行也一样），
 * 所以 marker 永远不会是字面最后一个字符——比较前先 trim 尾部空白。纯函数。
 */
internal fun isTerminalResult(err: Int, exitCode: Int, stdout: String, marker: String): Boolean {
    if (err != -1 || exitCode != 0) return true
    return stdout.trimEnd().endsWith(marker)
}

/**
 * 剥掉 [buildMarkerWrappedArgv] 追加的 `"\n" + marker`，还原命令真实 stdout。
 * marker 不在时（例如脚本根本没跑到 printf 的错误包）原样返回 [stdout]。纯函数。
 */
internal fun stripTerminationMarker(stdout: String, marker: String): String {
    val trimmed = stdout.trimEnd()
    val suffix = "\n$marker"
    return if (trimmed.endsWith(suffix)) trimmed.removeSuffix(suffix) else stdout
}

/**
 * 下发一条 Termux 命令并挂起到完成（或超时），返回捕获的输出。
 *
 * 实现要点：注册一次性 BroadcastReceiver，把它的 PendingIntent 交给 Termux，
 * 在 CompletableDeferred 上等 Termux 把广播打回来。内部**始终**用后台模式，
 * 因为结果 Bundle 只对后台命令回传。
 */
suspend fun runCommandCapture(
    ctx: Context,
    executable: String,
    arguments: Array<String>,
    workingDir: String,
    timeoutMs: Long = TermuxRuntime.commandTimeoutMs,
): CaptureResult {
    val resultDeferred = CompletableDeferred<Bundle>()
    val resultAction = "${ctx.packageName}.GULONG_TERMUX_RESULT_${UUID.randomUUID()}"
    // 每次调用专属的完成标记：Termux 的启动 ACK 与「成功但空输出」逐字节相同，
    // 没有这个标记就会在 ACK 上提前返回，命令还在跑却报成功。
    val marker = UUID.randomUUID().toString()

    val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            // Termux 0.118.x 会把 PendingIntent 打两次：
            //  - 一次几乎立刻到达的「已启动」ACK，intent.extras == null
            //  - 一次真正完成，extra 里带 stdout / stderr / exitCode
            // 早期版本用 FLAG_ONE_SHOT，结果 ACK 把一次性消耗掉、真结果永远收不到。
            // 现在忽略空广播，只在看到可用载荷时才 complete。
            val keys = intent.extras?.keySet()?.joinToString(",")
            val bundle = intent.getBundleExtra(EXTRA_RESULT_BUNDLE)
            android.util.Log.i(
                TAG,
                "broadcast: action=${intent.action} hasExtras=${intent.extras != null} extraKeys=[$keys] hasResultBundle=${bundle != null}",
            )
            if (bundle == null && intent.extras == null) return  // 空 ACK，继续等真结果
            if (bundle != null) {
                // 绝不打印 stdout/stderr 内容：命令输出可能带凭据，会落进 release 版 logcat。
                android.util.Log.i(
                    TAG,
                    "result bundle keys=${bundle.keySet().joinToString(",")} exit=${bundle.getInt(RESULT_KEY_EXIT_CODE, -999)} err=${bundle.getInt(RESULT_KEY_ERR, -999)} errmsg='${bundle.getString(RESULT_KEY_ERRMSG, "<null>")}'",
                )
            }
            // 部分 Termux 变体把键直接摊在广播 intent 上而不是嵌在 "result" 下，两种形状都兼容。
            val effective = bundle ?: intent.extras ?: Bundle()
            if (!isTerminalResult(
                    err = effective.getInt(RESULT_KEY_ERR, -1),
                    exitCode = effective.getInt(RESULT_KEY_EXIT_CODE, -1),
                    stdout = effective.getString(RESULT_KEY_STDOUT).orEmpty(),
                    marker = marker,
                )
            ) return
            if (resultDeferred.isActive) resultDeferred.complete(effective)
        }
    }

    val filter = IntentFilter(resultAction)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ctx.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
    } else {
        @Suppress("UnspecifiedRegisterReceiverFlag")
        ctx.registerReceiver(receiver, filter)
    }

    val pi = try {
        val resultIntent = Intent(resultAction).setPackage(ctx.packageName)
        PendingIntent.getBroadcast(
            ctx,
            resultAction.hashCode(),
            resultIntent,
            // Termux 会打两次（启动 ACK + 最终结果）：FLAG_ONE_SHOT 会吃掉 ACK 并丢掉真结果；
            // FLAG_MUTABLE 让 Termux 能往它发出去的 intent 上追加自己的 extras。
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    } catch (t: Throwable) {
        try { ctx.unregisterReceiver(receiver) } catch (_: Throwable) {}
        return CaptureResult.OtherError("PendingIntent creation failed: ${t.message}")
    }

    // 始终经 bash 下发，保证上面那句 marker printf 一定被执行——不管调用方点的什么可执行文件。
    val (wrappedExecutable, wrappedArguments) = buildMarkerWrappedArgv(
        bashPath = "$TERMUX_BIN_DIR/bash",
        executable = executable,
        arguments = arguments,
        marker = marker,
    )
    val intent = Intent().apply {
        setClassName(TERMUX_PACKAGE, TERMUX_RUN_COMMAND_SERVICE)
        action = TERMUX_RUN_COMMAND_ACTION
        putExtra("com.termux.RUN_COMMAND_PATH", wrappedExecutable)
        putExtra("com.termux.RUN_COMMAND_ARGUMENTS", wrappedArguments)
        putExtra("com.termux.RUN_COMMAND_WORKDIR", workingDir)
        putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
        putExtra(EXTRA_PENDING_INTENT, pi)
    }

    return try {
        ctx.startService(intent)
        val bundle = withTimeoutOrNull(timeoutMs) { resultDeferred.await() }
        if (bundle == null) {
            CaptureResult.Timeout
        } else {
            // 按 Termux RUN_COMMAND wiki：`err = -1`（= Activity.RESULT_OK）表示「无内部错误」，
            // 即成功哨兵值。任何非 -1 都是 Termux 内部真失败
            // （服务起不来、被手动结束、被系统杀等）。
            val errCode = bundle.getInt(RESULT_KEY_ERR, -1)
            if (errCode != -1) {
                val errMsg = bundle.getString(RESULT_KEY_ERRMSG).orEmpty()
                if (errMsg.contains("PermissionDenied", ignoreCase = true) ||
                    errMsg.contains("not allowed", ignoreCase = true)
                ) {
                    CaptureResult.Denied
                } else {
                    CaptureResult.OtherError("err=$errCode: $errMsg")
                }
            } else {
                CaptureResult.Success(
                    stdout = stripTerminationMarker(bundle.getString(RESULT_KEY_STDOUT).orEmpty(), marker),
                    stderr = bundle.getString(RESULT_KEY_STDERR).orEmpty(),
                    exitCode = bundle.getInt(RESULT_KEY_EXIT_CODE, -1),
                )
            }
        }
    } catch (t: SecurityException) {
        CaptureResult.Denied
    } catch (t: Throwable) {
        CaptureResult.OtherError(t.message ?: t::class.java.simpleName)
    } finally {
        try { ctx.unregisterReceiver(receiver) } catch (_: Throwable) {}
        try { pi.cancel() } catch (_: Throwable) {}
    }
}

/**
 * 交互模式：开一个用户可见的 Termux 会话，不做输出捕获（读不回来）。
 * 适合用户明确要「亲眼看着跑」或命令需要交互式提示的情况。
 */
fun runInteractiveSession(
    ctx: Context,
    executable: String,
    arguments: Array<String>,
    workingDir: String,
): Result<Unit> = try {
    val intent = Intent().apply {
        setClassName(TERMUX_PACKAGE, TERMUX_RUN_COMMAND_SERVICE)
        action = TERMUX_RUN_COMMAND_ACTION
        putExtra("com.termux.RUN_COMMAND_PATH", executable)
        putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arguments)
        putExtra("com.termux.RUN_COMMAND_WORKDIR", workingDir)
        putExtra("com.termux.RUN_COMMAND_BACKGROUND", false)
        putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0")
    }
    ctx.startService(intent)
    Result.success(Unit)
} catch (t: SecurityException) {
    Result.failure(t)
} catch (t: Throwable) {
    Result.failure(t)
}

package top.wkbin.taixu.harness

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import top.wkbin.taixu.gulong.termux.CaptureResult
import top.wkbin.taixu.gulong.termux.TERMUX_BIN_DIR
import top.wkbin.taixu.gulong.termux.TermuxClient
import top.wkbin.taixu.gulong.termux.TermuxDefaults
import top.wkbin.taixu.gulong.termux.TermuxRuntime
import top.wkbin.taixu.gulong.termux.runCommandCapture
import top.wkbin.taixu.gulong.termux.runInteractiveSession
import top.wkbin.taixu.gulong.termux.takeFirstUtf8Bytes
import top.wkbin.taixu.gulong.termux.wrapDetachedCommand
import top.wkbin.taixu.harness.core.ToolBackend

data class TermuxToolRequest(
    val args: JsonObject,
    val sessionId: String,
    val progressReporter: (suspend (String) -> Unit)? = null,
    val metadata: MutableMap<String, String>? = null,
)

/**
 * Agent 工具后端：外部 Termux 应用命令通道（termux_run_command 工具）。
 *
 * 与 base/process 的区别（这是理解本后端的关键）：
 * - base / process 跑在**内置 PRoot 沙箱**里，是 App 自己的 Linux 环境；
 * - 本后端调用的是**用户手机上已安装的 Termux**，通过 RUN_COMMAND Intent 下发，
 *   用的是用户自己的 rootfs、自己的包管理器、自己的家目录。
 *   所以这里装的东西用户能在 Termux 里直接看到，反之亦然。
 *
 * 职责边界：
 * - 只做参数解析、命令组装与结果信封格式化；
 * - 审批、脱敏、审计等横切关注点由 ToolExecutor 管道层处理；
 * - 前置条件不满足（没装 / 没权限 / 没开 allow-external-apps）都返回可行动的恢复指引，
 *   而不是一句泛泛的失败。
 */
class TermuxToolBackend(
    private val context: Context,
) : ToolBackend<TermuxToolRequest, Pair<Boolean, String>> {

    override suspend fun execute(request: TermuxToolRequest): Pair<Boolean, String> {
        val args = request.args
        val rawCommand = args["command"]?.jsonPrimitive?.contentOrNull
        val executable = args["executable"]?.jsonPrimitive?.contentOrNull
        val workingDir = args["working_dir"]?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.isNotBlank() }
            ?: TermuxRuntime.defaultWorkingDir
        val interactive = args["interactive"]?.jsonPrimitive?.booleanOrNull ?: false
        val background = args["background"]?.jsonPrimitive?.booleanOrNull ?: false

        if (rawCommand.isNullOrBlank() && executable.isNullOrBlank()) {
            return false to envelope {
                put("error", "either 'command' or 'executable' is required")
            }
        }
        if (!rawCommand.isNullOrBlank() && !executable.isNullOrBlank()) {
            return false to envelope {
                put("error", "command and executable are mutually exclusive")
            }
        }

        // 前置检查：装没装、有没有 RUN_COMMAND 权限。这两条能立刻判定，
        // allow-external-apps 判不了（只能靠真跑一条冒烟命令），所以不在这里拦。
        when (TermuxClient.state(context)) {
            TermuxClient.State.NOT_INSTALLED -> return false to envelope {
                put("error", "termux_not_installed")
                put(
                    "recovery",
                    "请用户从官方 GitHub Releases 安装 Termux（github.com/termux/termux-app/releases）。" +
                        "不要装 Play 商店或 F-Droid 的版本——那两个已停止维护，RUN_COMMAND 行为不一致。",
                )
            }
            TermuxClient.State.NO_PERMISSION -> return false to envelope {
                put("error", "termux_permission_not_granted")
                put(
                    "recovery",
                    "需要在系统里授予本应用 com.termux.permission.RUN_COMMAND。可在应用的 Termux 设置页打开开关触发授权弹窗，" +
                        "或执行：adb shell pm grant ${context.packageName} com.termux.permission.RUN_COMMAND",
                )
            }
            TermuxClient.State.READY -> Unit
        }

        // 组装真实命令：command 模式统一走 bash -c，并注入 apt/dpkg 非交互前导，
        // 避免 pkg upgrade 卡在 debconf 的「保留现有配置？」提示上直到超时。
        val (resolvedExe, resolvedArgs) = if (!rawCommand.isNullOrBlank()) {
            val preamble = if (TermuxRuntime.aptWrapEnabled) {
                "export DEBIAN_FRONTEND=noninteractive NEEDRESTART_MODE=a; " +
                    "apt(){ command apt -o Dpkg::Options::='--force-confdef' -o Dpkg::Options::='--force-confold' \"\$@\"; }; " +
                    "apt-get(){ command apt-get -o Dpkg::Options::='--force-confdef' -o Dpkg::Options::='--force-confold' \"\$@\"; }; " +
                    "export -f apt apt-get; "
            } else ""
            val body = if (background) wrapDetachedCommand(rawCommand) else rawCommand
            "$TERMUX_BIN_DIR/bash" to arrayOf("-c", preamble + body)
        } else {
            val argv = args["arguments"]?.let { element ->
                if (element is JsonArray) element.jsonArray.mapNotNull { it.jsonPrimitive.contentOrNull }
                else emptyList()
            } ?: emptyList()
            requireNotNull(executable) to argv.toTypedArray()
        }

        if (interactive) {
            // 交互模式：开一个用户可见的 Termux 会话，输出读不回来。
            return runInteractiveSession(context, resolvedExe, resolvedArgs, workingDir).fold(
                onSuccess = {
                    true to envelope {
                        put("success", true)
                        put("mode", "interactive")
                        put("note", "已在用户可见的 Termux 会话里启动；该模式不回传输出，用户能直接看到结果。")
                    }
                },
                onFailure = { failure ->
                    false to envelope {
                        if (failure is SecurityException) {
                            put("error", "termux_permission_denied")
                            put(
                                "recovery",
                                "在 Termux 里执行：mkdir -p ~/.termux && echo 'allow-external-apps=true' >> ~/.termux/termux.properties，" +
                                    "然后强制停止 Termux 并重新打开，再重试。",
                            )
                        } else {
                            put("error", "dispatch_failed")
                            put("reason", failure.message ?: failure::class.java.simpleName)
                        }
                    }
                },
            )
        }

        // 捕获模式（默认）：后台执行并把 stdout/stderr/exit_code 回给模型。
        val timeoutSeconds = args["timeout_seconds"]?.jsonPrimitive?.intOrNull
        val timeoutMs = when {
            timeoutSeconds == null || timeoutSeconds == 0 -> TermuxRuntime.commandTimeoutMs
            else -> timeoutSeconds.coerceIn(1, TermuxDefaults.MAX_COMMAND_TIMEOUT_SECONDS).toLong() * 1000
        }

        request.progressReporter?.invoke("正在通过 Termux 执行命令…")

        return try {
            when (val result = runCommandCapture(context, resolvedExe, resolvedArgs, workingDir, timeoutMs)) {
                is CaptureResult.Success -> {
                    val maxOut = TermuxRuntime.maxStdoutBytes
                    val maxErr = TermuxRuntime.maxStderrBytes
                    true to envelope {
                        put("success", result.exitCode == 0)
                        put("mode", "capture")
                        put("exit_code", result.exitCode)
                        val outBytes = result.stdout.toByteArray(Charsets.UTF_8).size
                        put(
                            "stdout",
                            if (outBytes > maxOut) {
                                takeFirstUtf8Bytes(result.stdout, maxOut) + "\n…[truncated; ${outBytes - maxOut} bytes more]"
                            } else result.stdout,
                        )
                        if (result.stderr.isNotBlank()) {
                            val errBytes = result.stderr.toByteArray(Charsets.UTF_8).size
                            put(
                                "stderr",
                                if (errBytes > maxErr) takeFirstUtf8Bytes(result.stderr, maxErr) + "\n…[truncated]"
                                else result.stderr,
                            )
                        }
                        if (result.exitCode != 0) put("note", "非零退出码，先看 stderr。")
                    }
                }
                is CaptureResult.Timeout -> false to envelope {
                    put("error", "timeout")
                    put(
                        "recovery",
                        "命令在 ${timeoutMs / 1000}s 内没有回结果。两种可能：命令本身太慢（调大 timeout_seconds），" +
                            "或 Termux 侧没开 allow-external-apps（那样结果广播永远不来，只能等到超时）。" +
                            "后者让用户在 Termux 里执行：mkdir -p ~/.termux && echo 'allow-external-apps=true' >> ~/.termux/termux.properties，然后强制停止 Termux 重开。",
                    )
                }
                is CaptureResult.Denied -> false to envelope {
                    put("error", "termux_permission_denied")
                    put(
                        "recovery",
                        "让用户在 Termux 里执行：mkdir -p ~/.termux && echo 'allow-external-apps=true' >> ~/.termux/termux.properties，" +
                            "从应用信息里强制停止 Termux 再重新打开，然后重试。",
                    )
                }
                is CaptureResult.OtherError -> false to envelope {
                    put("error", "termux_run_failed")
                    put("reason", result.message)
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            false to envelope {
                put("error", "termux_run_failed")
                put("reason", failure.message ?: failure::class.java.simpleName)
            }
        }
    }

    private fun envelope(block: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): String =
        buildJsonObject(block).toString()

    companion object {
        /** 供外部（设置页状态卡）复用的连通性冒烟测试。 */
        suspend fun verify(context: Context, timeoutMs: Long = TermuxRuntime.verifyTimeoutMs): TermuxClient.VerifyResult =
            TermuxClient.verify(context, timeoutMs)
    }
}

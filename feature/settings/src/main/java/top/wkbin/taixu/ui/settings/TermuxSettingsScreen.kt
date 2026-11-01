package top.wkbin.taixu.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.wkbin.taixu.gulong.termux.TermuxClient
import top.wkbin.taixu.gulong.termux.TermuxDefaults
import top.wkbin.taixu.gulong.termux.TermuxRuntime
import top.wkbin.taixu.gulong.termux.TermuxSettingsStore
import top.wkbin.taixu.ui.components.RuntimeFilledTextField
import top.wkbin.taixu.ui.components.RuntimeSettingsScaffold
import top.wkbin.taixu.ui.components.RuntimeSwitch
import top.wkbin.taixu.ui.components.SectionHeader

/**
 * 古龙 · Termux 设置页：管的是「怎么调外部 Termux」，不是内置 PRoot 沙箱。
 * 分区：状态 / 超时 / 默认值与限制 / 设置帮助；前三项落到 [TermuxRuntime]。
 */
@Composable
fun TermuxSettingsScreen(
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { TermuxSettingsStore(ctx) }

    var integrationState by remember { mutableStateOf(TermuxClient.State.NOT_INSTALLED) }
    var lastVerifiedOk by remember { mutableStateOf(false) }
    var verifyHint by remember { mutableStateOf<String?>(null) }

    // 输入框的草稿值：失焦时才提交，避免每次按键都写盘 + 夹取
    var commandTimeoutText by remember { mutableStateOf("") }
    var verifyTimeoutText by remember { mutableStateOf("") }
    var workingDirText by remember { mutableStateOf("") }
    var maxStdoutText by remember { mutableStateOf("") }
    var maxStderrText by remember { mutableStateOf("") }
    var aptWrap by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        store.loadIntoRuntime()
        integrationState = TermuxClient.state(ctx)
        lastVerifiedOk = TermuxClient.lastVerifiedOkAtMs > 0L
        commandTimeoutText = (TermuxRuntime.commandTimeoutMs / 1_000L).toString()
        verifyTimeoutText = (TermuxRuntime.verifyTimeoutMs / 1_000L).toString()
        workingDirText = TermuxRuntime.defaultWorkingDir
        maxStdoutText = TermuxRuntime.maxStdoutBytes.toString()
        maxStderrText = TermuxRuntime.maxStderrBytes.toString()
        aptWrap = TermuxRuntime.aptWrapEnabled
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                integrationState = TermuxClient.state(ctx)
                lastVerifiedOk = TermuxClient.lastVerifiedOkAtMs > 0L
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val denied = result.filterValues { !it }.keys
        integrationState = TermuxClient.state(ctx)
        verifyHint = if (denied.isEmpty()) "RUN_COMMAND 权限已授予" else "RUN_COMMAND 权限被拒绝，Termux 通道不可用"
    }

    fun openTermux() {
        runCatching {
            val launch = ctx.packageManager.getLaunchIntentForPackage("com.termux")
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ?: Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://github.com/termux/termux-app/releases")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            ctx.startActivity(launch)
        }
    }

    RuntimeSettingsScaffold(title = "Termux", onBack = onBack) {
        item { SectionHeader(title = "状态") }

        item {
            StatusRow(
                title = "Termux 应用",
                desc = if (integrationState == TermuxClient.State.NOT_INSTALLED) {
                    "未安装 —— 点此去官方 Releases 下载"
                } else {
                    "已安装 —— 点击打开"
                },
                color = if (integrationState == TermuxClient.State.NOT_INSTALLED) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                onClick = ::openTermux,
            )
        }
        item {
            StatusRow(
                title = "RUN_COMMAND 权限",
                desc = if (integrationState == TermuxClient.State.NO_PERMISSION) {
                    "未授权 —— 点击请求"
                } else if (integrationState == TermuxClient.State.NOT_INSTALLED) {
                    "Termux 未安装，先装 Termux"
                } else {
                    "已授权"
                },
                color = if (integrationState == TermuxClient.State.READY) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                onClick = {
                    val perms = arrayOf("com.termux.permission.RUN_COMMAND")
                    if (integrationState == TermuxClient.State.NOT_INSTALLED) {
                        verifyHint = "先安装 Termux，再回来授这个权限"
                    } else {
                        permLauncher.launch(perms)
                    }
                },
            )
        }
        item {
            StatusRow(
                title = "打开 Termux",
                desc = "启动 Termux 应用（手动进去看一眼最直观）",
                color = null,
                onClick = ::openTermux,
            )
        }
        item {
            StatusRow(
                title = "连通性测试",
                desc = when {
                    verifyHint != null -> verifyHint!!
                    lastVerifiedOk -> "上次测试通过 —— 点击重测"
                    integrationState == TermuxClient.State.READY -> "尚未验证 —— 点击运行冒烟测试"
                    else -> "前置条件未满足 —— 点击查看原因"
                },
                color = when {
                    lastVerifiedOk -> MaterialTheme.colorScheme.primary
                    integrationState == TermuxClient.State.READY -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.error
                },
                onClick = {
                    scope.launch {
                        val r = TermuxClient.verify(ctx, TermuxRuntime.verifyTimeoutMs)
                        verifyHint = when (r) {
                            TermuxClient.VerifyResult.Ok -> {
                                TermuxClient.markVerifiedOk()
                                "通过：调 Termux 跑 echo 并拿回了输出"
                            }
                            TermuxClient.VerifyResult.NotInstalled -> "失败：Termux 未安装"
                            TermuxClient.VerifyResult.NoPermission -> "失败：缺 RUN_COMMAND 权限"
                            TermuxClient.VerifyResult.AllowExternalAppsMissing ->
                                "失败：Termux 侧没开 allow-external-apps，或改完没强制停止重开"
                            is TermuxClient.VerifyResult.UnexpectedOutput -> "异常输出：${r.stdout.take(80)}"
                            is TermuxClient.VerifyResult.OtherError -> "失败：${r.message}"
                        }
                        lastVerifiedOk = TermuxClient.lastVerifiedOkAtMs > 0L
                        integrationState = TermuxClient.state(ctx)
                    }
                },
            )
        }
        item { SectionHeader(title = "超时") }

        item {
            NumberRow(
                title = "单条命令超时",
                desc = "等待 Termux 返回结果的秒数（${TermuxDefaults.MIN_COMMAND_TIMEOUT_MS / 1000}–${TermuxDefaults.MAX_COMMAND_TIMEOUT_MS / 1000} 秒）",
                value = commandTimeoutText,
                unit = "秒",
                onChange = { commandTimeoutText = it },
                onCommit = {
                    val v = commandTimeoutText.toLongOrNull()
                    if (v != null) store.setCommandTimeoutMs(v * 1_000L)
                    commandTimeoutText = (TermuxRuntime.commandTimeoutMs / 1_000L).toString()
                },
            )
        }
        item {
            NumberRow(
                title = "连通性测试超时",
                desc = "冒烟测试的等待秒数（${TermuxDefaults.MIN_VERIFY_TIMEOUT_MS / 1000}–${TermuxDefaults.MAX_VERIFY_TIMEOUT_MS / 1000} 秒）",
                value = verifyTimeoutText,
                unit = "秒",
                onChange = { verifyTimeoutText = it },
                onCommit = {
                    val v = verifyTimeoutText.toLongOrNull()
                    if (v != null) store.setVerifyTimeoutMs(v * 1_000L)
                    verifyTimeoutText = (TermuxRuntime.verifyTimeoutMs / 1_000L).toString()
                },
            )
        }
        item { SectionHeader(title = "默认值与限制") }

        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("默认工作目录", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "命令未指定 working_dir 时用这里；默认 ${TermuxDefaults.DEFAULT_WORKING_DIR}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                RuntimeFilledTextField(
                    value = workingDirText,
                    onValueChange = { workingDirText = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    supportingText = {
                        Text("失焦即保存；留空则回落到默认目录", style = MaterialTheme.typography.bodySmall)
                    },
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (workingDirText.isNotBlank()) store.setDefaultWorkingDir(workingDirText)
                            workingDirText = TermuxRuntime.defaultWorkingDir
                        }
                        .padding(vertical = 4.dp),
                ) {
                    Text("保存工作目录", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        item {
            NumberRow(
                title = "stdout 截断上限",
                desc = "每条命令回给 AI 的最大字节数（${TermuxDefaults.MIN_MAX_STDOUT}–${TermuxDefaults.MAX_MAX_STDOUT}）",
                value = maxStdoutText,
                unit = "字节",
                onChange = { maxStdoutText = it },
                onCommit = {
                    maxStdoutText.toIntOrNull()?.let { store.setMaxStdoutBytes(it) }
                    maxStdoutText = TermuxRuntime.maxStdoutBytes.toString()
                },
            )
        }
        item {
            NumberRow(
                title = "stderr 截断上限",
                desc = "错误输出的最大字节数（${TermuxDefaults.MIN_MAX_STDERR}–${TermuxDefaults.MAX_MAX_STDERR}）",
                value = maxStderrText,
                unit = "字节",
                onChange = { maxStderrText = it },
                onCommit = {
                    maxStderrText.toIntOrNull()?.let { store.setMaxStderrBytes(it) }
                    maxStderrText = TermuxRuntime.maxStderrBytes.toString()
                },
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("apt / dpkg 非交互包装", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "命令前注入 DEBIAN_FRONTEND=noninteractive，避免升级卡在 debconf 提问上",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                RuntimeSwitch(
                    checked = aptWrap,
                    onCheckedChange = {
                        aptWrap = it
                        store.setAptWrapEnabled(it)
                    },
                )
            }
        }
        item { SectionHeader(title = "设置帮助") }

        item {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Text("如何启用 allow-external-apps", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "在 Termux 里执行：\nmkdir -p ~/.termux && echo 'allow-external-apps=true' >> ~/.termux/termux.properties\n\n" +
                        "然后从 Android 应用信息里强制停止 Termux 并重开，再回来点上面的「连通性测试」。\n" +
                        "这条不生效的表现很有迷惑性：发送端不报错，但结果永远等不回来，只表现为超时。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun StatusRow(
    title: String,
    desc: String,
    color: Color?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (color != null) {
            Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
            Box(modifier = Modifier.width(10.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NumberRow(
    title: String,
    desc: String,
    value: String,
    unit: String,
    onChange: (String) -> Unit,
    onCommit: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            RuntimeFilledTextField(
                value = value,
                onValueChange = { new -> onChange(new.filter { it.isDigit() }.take(6)) },
                modifier = Modifier.width(150.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                suffix = { Text(unit, style = MaterialTheme.typography.bodySmall) },
            )
            Box(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .clickable(onClick = onCommit)
                    .padding(vertical = 4.dp),
            ) {
                Text("应用", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

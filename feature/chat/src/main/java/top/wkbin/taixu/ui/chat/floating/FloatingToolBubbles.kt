package top.wkbin.taixu.ui.chat.floating

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.wkbin.taixu.harness.AssistantText
import top.wkbin.taixu.harness.ToolResult
import top.wkbin.taixu.ui.chat.MarkdownText
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName

/**
 * 悬浮小窗里的「工具回执气泡」与「思考过程气泡」。
 *
 * 两者都默认收起、点标题栏才展开：悬浮窗本来就窄（310dp），
 * 一轮里工具调用动辄十几次、思考动辄上千字，全展开会把对话正文挤出视野，
 * 变成「一屏全是过程、看不见结果」。
 *
 * 抽成独立文件还有个现实原因：FloatingChatView 有 652 行棘轮，塞进去会顶破。
 */

@Composable
internal fun FloatingToolResultBubble(msg: ToolResult) {
    var expanded by remember(msg.id) { mutableStateOf(false) }
    val ok = msg.success
    val accent = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    val hasBody = msg.output.isNotBlank()
    Surface(
        color = if (ok) MaterialTheme.colorScheme.surfaceContainerLow
        else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.clickable(enabled = hasBody) { expanded = !expanded },
    ) {
        Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RuntimeIcon(
                    if (ok) RuntimeIconName.Check else RuntimeIconName.Close,
                    Modifier.size(11.dp),
                    accent,
                )
                Text(
                    text = buildString {
                        append(if (ok) "工具结果 " else "工具失败 ")
                        msg.durationMs?.let { append("${it}ms") }
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = accent,
                )
                if (hasBody) {
                    RuntimeIcon(
                        if (expanded) RuntimeIconName.ChevronUp else RuntimeIconName.ChevronDown,
                        Modifier.size(11.dp),
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (expanded && hasBody) {
                Text(
                    text = msg.output.take(2000),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/** 思考过程：默认只显示一行摘要，点开才看全文。 */
@Composable
internal fun FloatingReasoningBubble(thinking: String) {
    var expanded by remember(thinking.take(64)) { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .widthIn(max = 260.dp)
            .clickable { expanded = !expanded },
    ) {
        Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "思考过程",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                    color = MaterialTheme.colorScheme.tertiary,
                )
                RuntimeIcon(
                    if (expanded) RuntimeIconName.ChevronUp else RuntimeIconName.ChevronDown,
                    Modifier.size(11.dp),
                    MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = if (expanded) thinking.take(2000) else thinking.take(80),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 助手正文：走完整 Markdown 渲染，与主聊天区一致。 */
@Composable
internal fun FloatingAssistantBody(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.widthIn(max = 260.dp),
    ) {
        MarkdownText(
            text,
            Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
        )
    }
}

/**
 * 每条助手回复底部的用量行：上下文 / 缓存 / 输出 / 耗时。
 *
 * 数字全部来自 [AssistantText] 上由 provider 回传的 usage 字段，
 * 不是估算值；字段缺失（老数据或 provider 不回传）就不显示该段，不填空值糊弄。
 */
@Composable
internal fun FloatingUsageLine(msg: AssistantText) {
    val bits = buildList {
        msg.promptTokens?.let { add("上下文 $it") }
        msg.cachedTokens?.takeIf { it > 0 }?.let { add("缓存 $it") }
        msg.completionTokens?.let { add("输出 $it") }
        msg.totalMs?.let { add(String.format(java.util.Locale.US, "%.1fs", it / 1000.0)) }
    }
    if (bits.isEmpty()) return
    Text(
        text = bits.joinToString(" · "),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

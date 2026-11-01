package top.wkbin.taixu.ui.chat.floating

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.wkbin.taixu.harness.ToolResult
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName

/**
 * 悬浮小窗里的「工具回执气泡」。
 *
 * 抽成独立文件有两个原因：
 *  1. FloatingChatView 有 652 行棘轮，塞进去会顶破；
 *  2. 这段是纯展示逻辑，和窗体的拖拽/输入无关，放一起反而不好读。
 */
@Composable
internal fun FloatingToolResultBubble(msg: ToolResult) {
    val ok = msg.success
    Surface(
        color = if (ok) MaterialTheme.colorScheme.surfaceContainerLow
        else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
        shape = RoundedCornerShape(6.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RuntimeIcon(
                    if (ok) RuntimeIconName.Check else RuntimeIconName.Close,
                    Modifier.size(11.dp),
                    if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                Text(
                    text = buildString {
                        append(if (ok) "✓ " else "✗ ")
                        msg.durationMs?.let { append("${it}ms") }
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
            if (msg.output.isNotBlank()) {
                Text(
                    text = msg.output.take(600),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/** 悬浮小窗里的「思考过程气泡」：推理模型的 reasoning_content。 */
@Composable
internal fun FloatingReasoningBubble(thinking: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.widthIn(max = 260.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
            Text(
                text = "思考过程",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                color = MaterialTheme.colorScheme.tertiary,
            )
            Text(
                text = thinking.take(800),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 12,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

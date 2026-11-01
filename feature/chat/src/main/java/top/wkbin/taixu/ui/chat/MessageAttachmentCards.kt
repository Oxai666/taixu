package top.wkbin.taixu.ui.chat

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.io.File
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.theme.semanticColors

/**
 * 历史消息里的附件卡片。
 *
 * 消息只存了拼好的文本（「[文件] 名字 (大小) -> Linux 沙箱绝对路径: /attachments/xxx」），
 * 没有附件对象本身，所以这里从文本里把条目解出来重建成卡片 ——
 * 目的是让气泡里显示的是「文件卡片」而不是那一坨提示词。
 */
internal data class ParsedAttachment(
    val name: String,
    val sizeText: String,
    val guestPath: String,
    val isImage: Boolean,
)

private val PATH_RE = Regex("""/attachments/\S+""")
private val NAME_RE = Regex("""\[[^\]]+\]\s*(.+?)\s*\(""")
private val SIZE_RE = Regex("""\(([^)]+)\)""")

internal fun parseAttachmentEntries(text: String): List<ParsedAttachment> {
    if (!text.contains("/attachments/")) return emptyList()
    return text.lineSequence().mapNotNull { line ->
        val path = PATH_RE.find(line)?.value ?: return@mapNotNull null
        val isImage = line.contains("[图片]") || line.contains("[image]", ignoreCase = true)
        val name = NAME_RE.find(line)?.groupValues?.getOrNull(1)?.trim()
            ?.takeIf { it.isNotEmpty() } ?: path.substringAfterLast('/')
        val size = SIZE_RE.find(line)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        ParsedAttachment(name, size, path, isImage)
    }.toList()
}

/** 哪些类型能就地预览：纯文本类与 Markdown；其余给一句"无法预览"。 */
internal fun isPreviewable(att: ParsedAttachment): Boolean {
    if (att.isImage) return true
    val ext = att.name.substringAfterLast('.', "").lowercase()
    return ext in setOf(
        "md", "markdown", "txt", "log", "json", "jsonl", "yaml", "yml", "xml", "csv", "tsv",
        "kt", "kts", "java", "py", "js", "ts", "tsx", "jsx", "sh", "bash", "gradle", "toml",
        "ini", "conf", "properties", "sql", "html", "css", "c", "h", "cpp", "go", "rs", "dart",
    )
}

/** 沙箱 /attachments 对应的宿主目录。 */
internal fun sandboxAttachmentFile(context: Context, guestPath: String): File? {
    val name = guestPath.substringAfterLast('/')
    if (name.isBlank()) return null
    val dir = File(context.filesDir, "linux-runtime/attachments")
    val f = File(dir, name)
    return f.takeIf { it.exists() }
}

@Composable
internal fun MessageAttachmentRow(
    items: List<ParsedAttachment>,
    onOpen: (ParsedAttachment) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    LazyRow(
        modifier = modifier.fillMaxWidth().padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items, key = { it.guestPath }) { item ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                modifier = Modifier.clickable { onOpen(item) },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(
                                if (item.isImage) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                else semanticColors().success.copy(alpha = 0.15f),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        RuntimeIcon(
                            name = if (item.isImage) RuntimeIconName.Image else RuntimeIconName.Attach,
                            modifier = Modifier.size(13.dp),
                            tint = if (item.isImage) MaterialTheme.colorScheme.primary else semanticColors().success,
                        )
                    }
                    Column(modifier = Modifier.padding(end = 4.dp)) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(end = 0.dp),
                        )
                        if (item.sizeText.isNotBlank()) {
                            Text(
                                text = item.sizeText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 文本类附件的就地预览：等宽、可滚动、上限 200KB 防止大文件卡死。 */
@Composable
internal fun TextAttachmentPreviewDialog(name: String, content: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    content,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .verticalScroll(rememberScrollState()),
                )
            }
        }
    }
}

/**
 * 用户消息里的附件区：卡片 + 点击预览。
 *
 * 图片不在这里预览（它走气泡上方的缩略图 + ImagePreviewOverlay）；
 * 文本类就地弹窗；二进制类型静默忽略，不给「无法预览」的噪音。
 */
@Composable
internal fun UserMessageAttachments(text: String) {
    val context = LocalContext.current
    val items = remember(text) { parseAttachmentEntries(text) }
    var preview by remember { mutableStateOf<Pair<String, String>?>(null) }
    if (items.isEmpty()) return
    MessageAttachmentRow(items, onOpen = { att ->
        if (!att.isImage && isPreviewable(att)) {
            sandboxAttachmentFile(context, att.guestPath)?.let { f ->
                runCatching { preview = att.name to f.readText().take(200_000) }
            }
        }
    })
    preview?.let { (name, content) ->
        TextAttachmentPreviewDialog(name, content) { preview = null }
    }
}

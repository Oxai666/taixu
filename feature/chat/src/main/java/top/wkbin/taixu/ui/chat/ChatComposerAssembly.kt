package top.wkbin.taixu.ui.chat

import android.content.Context
import top.wkbin.taixu.feature.chat.R

/**
 * 组装输入框提交正文：用户文本 + 附件挂载说明（来自 [ChatViewModel.sendFromComposer]，
 * 抽出以控制主 ViewModel 文件尺寸）。
 */
internal fun buildComposerMessage(
    context: Context,
    trimmedInput: String,
    attachments: List<ChatAttachment>,
): String {
    val nonImageFiles = attachments.filter { !it.isImage }
    return buildString {
        // 纯图片消息不拼任何文字：图片本身已经内联在消息里，模型直接就能看见，
        // 再加一句「请分析附带的图片」是纯噪音，还会把它推去「找文件」。
        if (trimmedInput.isNotBlank()) {
            append(trimmedInput)
        } else if (nonImageFiles.isNotEmpty()) {
            append(context.getString(R.string.chat_attachment_files_prompt))
        }
        if (nonImageFiles.isNotEmpty()) {
            append(context.getString(R.string.chat_attachment_mount_header))
            nonImageFiles.forEachIndexed { i, att ->
                val guestPath = att.guestFilePath ?: "/attachments/${att.name}"
                val kind = context.getString(R.string.chat_attachment_file)
                append(context.getString(R.string.chat_attachment_line, i + 1, kind, att.name, AttachmentHelper.formatFileSize(att.sizeBytes), guestPath))
                att.extractedTextGuestPath?.let {
                    append(context.getString(R.string.chat_attachment_extracted, it))
                }
            }
            append(context.getString(R.string.chat_attachment_access_hint))
        }
    }
}

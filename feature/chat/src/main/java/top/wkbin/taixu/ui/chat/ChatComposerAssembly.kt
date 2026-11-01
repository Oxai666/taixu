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
    val imageUrls = attachments.mapNotNull { it.base64DataUrl }
    val nonImageFiles = attachments.filter { !it.isImage }
    return buildString {
        if (trimmedInput.isNotBlank()) {
            append(trimmedInput)
        } else if (nonImageFiles.isNotEmpty()) {
            append(context.getString(R.string.chat_attachment_files_prompt))
        } else if (imageUrls.isNotEmpty()) {
            append(context.getString(R.string.chat_attachment_images_prompt))
        }
        // 图片走 image_url 内联，沙箱里根本没有文件——再说「挂载到沙箱、用 base 读」
        // 就是给出一个必然失败的指令（模型会去 read 一个不存在的路径）。
        val imageCount = attachments.count { it.isImage }
        if (imageCount > 0) {
            append(context.getString(R.string.chat_attachment_images_inline, imageCount))
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

package top.wkbin.taixu.ui.chat

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.core.database.AiModelEntity
import top.wkbin.taixu.core.model.ApprovalMode
import top.wkbin.taixu.feature.chat.R
import top.wkbin.taixu.ui.components.ModelBadge
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconButton
import top.wkbin.taixu.ui.components.RuntimeIconName

/**
 * 输入区工具栏：模型徽标、审批模式、思考强度、附件「+」。
 *
 * 思考强度（Brain 图标）从顶栏挪到这里、紧挨「+」——它和「这条消息怎么发出去」是同一类决策，
 * 顶栏那排全是会话级入口（分支/记忆/大纲），把推理强度混在里面反而要多走一层。
 */
@Composable
internal fun ComposerQuickActions(
    activeModel: AiModelEntity?,
    onOpenModels: () -> Unit,
    onOpenAttachSheet: () -> Unit,
    approvalMode: ApprovalMode,
    onOpenApprovalModes: () -> Unit,
) {
    val openReasoning = LocalReasoningOpener.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        RuntimeIconButton(
            onClick = onOpenModels,
            modifier = Modifier.size(48.dp),
            contentDescription = stringResource(R.string.chat_select_model),
        ) {
            ModelBadge(
                modelId = activeModel?.model,
                providerIdOrName = activeModel?.provider.orEmpty(),
                size = 26.dp,
            )
        }
        val modeLabel = stringResource(when (approvalMode) {
            ApprovalMode.FULL_ACCESS -> R.string.chat_approval_full_access
            ApprovalMode.ASSISTED -> R.string.chat_approval_assisted
            ApprovalMode.REQUEST -> R.string.chat_approval_request
        })
        val modeColor = when (approvalMode) {
            ApprovalMode.FULL_ACCESS -> MaterialTheme.colorScheme.error
            ApprovalMode.ASSISTED -> MaterialTheme.colorScheme.primary
            ApprovalMode.REQUEST -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        RuntimeIconButton(
            onClick = onOpenApprovalModes,
            modifier = Modifier.size(48.dp),
            contentDescription = modeLabel,
        ) {
            RuntimeIcon(RuntimeIconName.Shield, Modifier.size(22.dp), modeColor)
        }
        // 思考强度：紧挨「+」，与附件同一排
        RuntimeIconButton(
            onClick = openReasoning,
            modifier = Modifier.size(48.dp),
            contentDescription = "思考强度",
        ) {
            RuntimeIcon(RuntimeIconName.Brain, Modifier.size(22.dp), MaterialTheme.colorScheme.onSurfaceVariant)
        }
        RuntimeIconButton(
            onClick = onOpenAttachSheet,
            modifier = Modifier.size(48.dp),
            contentDescription = stringResource(R.string.chat_add_attachment),
        ) {
            RuntimeIcon(RuntimeIconName.Plus, Modifier.size(20.dp), MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

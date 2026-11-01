package top.wkbin.taixu.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontFamily
import top.wkbin.taixu.ui.components.RuntimeFilledTextField

/**
 * 模型档案里的「余额查询」两个字段。
 *
 * 为什么单独成一个文件：ModelEditorScreen 有 1315 行棘轮，两个输入框连修饰带占位符
 * 塞进去会顶破。它们又是一组语义完整的小配置（接口路径 + 取值路径），
 * 抽出来反而更好读。
 *
 * 两项都留空 = 走内置探测链（/user/balance → /v1/credits 依次尝试），
 * 所以对多数服务商是一片空白也能用。
 */
@Composable
internal fun ModelBalanceFields(
    apiPath: String,
    onApiPathChange: (String) -> Unit,
    resultPath: String,
    onResultPathChange: (String) -> Unit,
    shape: Shape,
    colors: TextFieldColors,
    modifier: Modifier = Modifier,
) {
    val mono = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
    RuntimeFilledTextField(
        value = apiPath, onValueChange = onApiPathChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text("余额接口路径（留空=自动探测）") },
        placeholder = { Text("/user/balance") }, singleLine = true,
        shape = shape, colors = colors, textStyle = mono,
    )
    RuntimeFilledTextField(
        value = resultPath, onValueChange = onResultPathChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text("余额取值路径（响应 JSON 路径）") },
        placeholder = { Text("balance_infos[0].total_balance") }, singleLine = true,
        shape = shape, colors = colors, textStyle = mono,
    )
}

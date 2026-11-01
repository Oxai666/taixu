package top.wkbin.taixu.ui.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toPath
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon

/**
 * 档位专属图形：与思考强度面板里那颗会变形的 Hero 同源，取的是**该档位的最终形态**（不播放变形动画）。
 *
 * 为什么要这个而不是通用图标：面板里六档的视觉语言就是这六个逐渐「锋利」的图形
 * （圆 → 四边 → 六边 → 九边 → 十二边 → 爆破星），工具条上继续用一颗大脑，
 * 等于面板说一套、按钮说另一套。这里直接把同一形状按当前档位静态画出来，两处视觉统一。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ReasoningLevelGlyph(
    index: Int,
    glyphSize: Dp,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val idx = index.coerceIn(0, 5)
    val shape = remember(idx) { levelShapeAt(idx) }
    Box(
        modifier = modifier
            .size(glyphSize)
            .drawBehind {
                val path = Path()
                // 用同形 Morph 取 progress=0，等价于「该档位的终态路径」，避免自己做多边形拼装
                Morph(shape, shape).toPath(0f, path)
                withTransform({
                    rotate(idx * 30f)
                    // MaterialShapes 归一化到 1×1，按实际尺寸放大
                    scale(size.width, size.height, pivot = Offset.Zero)
                }) {
                    drawPath(path, color)
                }
            },
    )
}

/** 与 ChatSheets 里 reasoningLevelShape 保持一致的六档形状表（那边是 private，这里单列一份）。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun levelShapeAt(index: Int): RoundedPolygon = when (index) {
    0 -> MaterialShapes.Circle
    1 -> MaterialShapes.Cookie4Sided
    2 -> MaterialShapes.Cookie6Sided
    3 -> MaterialShapes.Cookie9Sided
    4 -> MaterialShapes.Cookie12Sided
    else -> MaterialShapes.SoftBurst
}

/** 档位索引 → 按钮配色，与面板 Hero 的配色梯度对齐。 */
@Composable
internal fun reasoningGlyphTint(index: Int): Color = when (index) {
    0 -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    1 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    2 -> MaterialTheme.colorScheme.primary
    3 -> MaterialTheme.colorScheme.tertiary
    4 -> MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
    else -> MaterialTheme.colorScheme.error
}

/** 由 reasoningMode / reasoningEffort 反推档位索引，与面板的 currentIndex 判定一致。 */
internal fun reasoningLevelIndex(mode: String?, effort: String?): Int = when {
    mode != "enabled" -> 0
    effort == "low" -> 1
    effort == "medium" -> 2
    effort == "high" -> 3
    effort == "xhigh" -> 4
    effort == "max" || effort == "extreme" -> 5
    else -> 2
}

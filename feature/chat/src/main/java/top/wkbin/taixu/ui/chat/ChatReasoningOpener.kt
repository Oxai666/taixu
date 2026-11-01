package top.wkbin.taixu.ui.chat

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 打开「思考强度」滑块的入口。
 *
 * 为什么用 CompositionLocal 而不是一路传参：
 * 弹层状态 `showReasoningSlider` 住在 ChatScreen 顶层，而按钮在最底层的
 * ComposerQuickActions 里，中间隔着 ChatPaneContent 等三四层。
 * 为这一个回调把签名单路改穿，既要动一堆 composable 的参数列表，也容易漏改；
 * 用 CompositionLocal provide 一次、底层直接取，改动面最小且语义明确——它就相当于
 * 「本屏内的全局动作」。
 */
val LocalReasoningOpener = staticCompositionLocalOf<() -> Unit> { {} }

package top.wkbin.taixu.gulong.termux

/**
 * Termux 外部调用：全部可配置项的权威默认值与夹取函数。
 *
 * 每一项上限都是硬上限——任何工具调用都必须有超时兜底，不存在「无限等待」的档位。
 * 这里的常量直接对应「古龙 · Termux 设置页」上的滑杆与输入框。
 */
object TermuxDefaults {

    // --- 单条命令捕获超时 ---------------------------------------------------------------
    /** 等待一条 Termux 后台命令完成的默认毫秒数。 */
    const val DEFAULT_COMMAND_TIMEOUT_MS = 60_000L   // 60 秒
    const val MIN_COMMAND_TIMEOUT_MS     =  5_000L   //  5 秒
    /** 硬上限 10 分钟（600 秒）。 */
    const val MAX_COMMAND_TIMEOUT_MS     = 600_000L  // 10 分钟

    // --- 单轮墙钟预算 ------------------------------------------------------------------
    /** 单个用户回合内所有工具调用可消耗的总墙钟时间。 */
    const val DEFAULT_TURN_BUDGET_MS = 10L * 60L * 1_000L  // 10 分钟
    const val MIN_TURN_BUDGET_MS     =  1L * 60L * 1_000L  //  1 分钟
    const val MAX_TURN_BUDGET_MS     = 60L * 60L * 1_000L  // 60 分钟

    // --- 单轮工具步数上限 --------------------------------------------------------------
    /** 一次回答内最多可连续发起的工具调用次数。 */
    const val DEFAULT_MAX_TOOL_STEPS = 32
    const val MIN_MAX_TOOL_STEPS     =  1
    const val MAX_MAX_TOOL_STEPS     = 500

    // --- 联通性冒烟测试超时 ------------------------------------------------------------
    const val DEFAULT_VERIFY_TIMEOUT_MS =  8_000L   //  8 秒
    const val MIN_VERIFY_TIMEOUT_MS     =  3_000L   //  3 秒
    const val MAX_VERIFY_TIMEOUT_MS     = 30_000L   // 30 秒

    // --- 默认工作目录 ------------------------------------------------------------------
    const val DEFAULT_WORKING_DIR = "/data/data/com.termux/files/home"

    // --- stdout / stderr 截断上限（字节）-----------------------------------------------
    const val DEFAULT_MAX_STDOUT =  8_000
    const val MIN_MAX_STDOUT     =  1_000
    const val MAX_MAX_STDOUT     = 64_000

    const val DEFAULT_MAX_STDERR =  2_000
    const val MIN_MAX_STDERR     =    500
    const val MAX_MAX_STDERR     = 16_000

    // --- apt / dpkg 非交互包装 ---------------------------------------------------------
    /** 默认开启：命令前注入 DEBIAN_FRONTEND=noninteractive，避免 debconf 交互卡死。 */
    const val DEFAULT_APT_WRAP_ENABLED = true

    // --- 单次调用 timeout_seconds 上限（暴露给模型的参数）------------------------------
    const val MAX_COMMAND_TIMEOUT_SECONDS = 600

    // --- 夹取函数 ----------------------------------------------------------------------

    fun clampCommandTimeoutMs(ms: Long): Long =
        ms.coerceIn(MIN_COMMAND_TIMEOUT_MS, MAX_COMMAND_TIMEOUT_MS)

    fun clampTurnBudgetMs(ms: Long): Long =
        ms.coerceIn(MIN_TURN_BUDGET_MS, MAX_TURN_BUDGET_MS)

    fun clampMaxToolSteps(steps: Int): Int =
        steps.coerceIn(MIN_MAX_TOOL_STEPS, MAX_MAX_TOOL_STEPS)

    fun clampVerifyTimeoutMs(ms: Long): Long =
        ms.coerceIn(MIN_VERIFY_TIMEOUT_MS, MAX_VERIFY_TIMEOUT_MS)

    fun clampMaxStdout(bytes: Int): Int =
        bytes.coerceIn(MIN_MAX_STDOUT, MAX_MAX_STDOUT)

    fun clampMaxStderr(bytes: Int): Int =
        bytes.coerceIn(MIN_MAX_STDERR, MAX_MAX_STDERR)

    /**
     * 工作目录非空规则：空串传给 Termux 的 RUN_COMMAND 会静默回落到 Termux 内部默认值，
     * 结果难以预料。这里空值直接回落到默认目录。
     */
    fun clampWorkingDir(dir: String): String =
        dir.ifBlank { DEFAULT_WORKING_DIR }
}

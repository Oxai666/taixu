package top.wkbin.taixu.gulong.termux

/**
 * 进程级 @Volatile 运行时配置持有器。
 *
 * 由设置层（DataStore 收集器）推入，让非 suspend 调用方在任意时刻都能读到最新已持久化的值，
 * 不需要协程上下文。初值即默认值，保证「首次调用、设置还没加载完」也有合理行为。
 */
object TermuxRuntime {
    @Volatile var commandTimeoutMs: Long    = TermuxDefaults.DEFAULT_COMMAND_TIMEOUT_MS
    @Volatile var verifyTimeoutMs: Long     = TermuxDefaults.DEFAULT_VERIFY_TIMEOUT_MS
    @Volatile var defaultWorkingDir: String = TermuxDefaults.DEFAULT_WORKING_DIR
    @Volatile var maxStdoutBytes: Int       = TermuxDefaults.DEFAULT_MAX_STDOUT
    @Volatile var maxStderrBytes: Int       = TermuxDefaults.DEFAULT_MAX_STDERR
    @Volatile var aptWrapEnabled: Boolean   = TermuxDefaults.DEFAULT_APT_WRAP_ENABLED

    /** 全部恢复出厂值，供「重置设置」使用。 */
    fun resetToDefaults() {
        commandTimeoutMs = TermuxDefaults.DEFAULT_COMMAND_TIMEOUT_MS
        verifyTimeoutMs = TermuxDefaults.DEFAULT_VERIFY_TIMEOUT_MS
        defaultWorkingDir = TermuxDefaults.DEFAULT_WORKING_DIR
        maxStdoutBytes = TermuxDefaults.DEFAULT_MAX_STDOUT
        maxStderrBytes = TermuxDefaults.DEFAULT_MAX_STDERR
        aptWrapEnabled = TermuxDefaults.DEFAULT_APT_WRAP_ENABLED
    }
}

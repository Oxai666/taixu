package top.wkbin.taixu.gulong.termux

import android.content.Context

/**
 * Termux 通道设置的持久化层。
 *
 * 为什么用 SharedPreferences 而不是主 DataStore：
 * 这些参数只服务 Termux 这一条通道，改动频率极低、不参与任何 Flow 监听，
 * 塞进主 DataStore 需要改 schema、迁移、序列化器三处，代价与收益不成比例。
 * 写入后立即推入 [TermuxRuntime]，让工具调用侧无需协程上下文即可读到最新值。
 */
class TermuxSettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("gulong_termux_settings", Context.MODE_PRIVATE)

    /** 一次性把磁盘值装进 [TermuxRuntime]。应用启动时调用。 */
    fun loadIntoRuntime() {
        TermuxRuntime.commandTimeoutMs =
            TermuxDefaults.clampCommandTimeoutMs(prefs.getLong(KEY_COMMAND_TIMEOUT, TermuxDefaults.DEFAULT_COMMAND_TIMEOUT_MS))
        TermuxRuntime.verifyTimeoutMs =
            TermuxDefaults.clampVerifyTimeoutMs(prefs.getLong(KEY_VERIFY_TIMEOUT, TermuxDefaults.DEFAULT_VERIFY_TIMEOUT_MS))
        TermuxRuntime.defaultWorkingDir =
            TermuxDefaults.clampWorkingDir(prefs.getString(KEY_WORKING_DIR, null) ?: TermuxDefaults.DEFAULT_WORKING_DIR)
        TermuxRuntime.maxStdoutBytes =
            TermuxDefaults.clampMaxStdout(prefs.getInt(KEY_MAX_STDOUT, TermuxDefaults.DEFAULT_MAX_STDOUT))
        TermuxRuntime.maxStderrBytes =
            TermuxDefaults.clampMaxStderr(prefs.getInt(KEY_MAX_STDERR, TermuxDefaults.DEFAULT_MAX_STDERR))
        TermuxRuntime.aptWrapEnabled = prefs.getBoolean(KEY_APT_WRAP, TermuxDefaults.DEFAULT_APT_WRAP_ENABLED)
        TermuxClient.restoreVerifiedAt(prefs.getLong(KEY_VERIFIED_AT, 0L))
        TermuxClient.persister = { ms -> prefs.edit().putLong(KEY_VERIFIED_AT, ms).apply() }
    }

    // --- 单项写入：均先夹取再落盘，再同步进运行时 -------------------------------------

    fun setCommandTimeoutMs(ms: Long) {
        val v = TermuxDefaults.clampCommandTimeoutMs(ms)
        prefs.edit().putLong(KEY_COMMAND_TIMEOUT, v).apply()
        TermuxRuntime.commandTimeoutMs = v
    }

    fun setVerifyTimeoutMs(ms: Long) {
        val v = TermuxDefaults.clampVerifyTimeoutMs(ms)
        prefs.edit().putLong(KEY_VERIFY_TIMEOUT, v).apply()
        TermuxRuntime.verifyTimeoutMs = v
    }

    fun setDefaultWorkingDir(dir: String) {
        val v = TermuxDefaults.clampWorkingDir(dir)
        prefs.edit().putString(KEY_WORKING_DIR, v).apply()
        TermuxRuntime.defaultWorkingDir = v
    }

    fun setMaxStdoutBytes(bytes: Int) {
        val v = TermuxDefaults.clampMaxStdout(bytes)
        prefs.edit().putInt(KEY_MAX_STDOUT, v).apply()
        TermuxRuntime.maxStdoutBytes = v
    }

    fun setMaxStderrBytes(bytes: Int) {
        val v = TermuxDefaults.clampMaxStderr(bytes)
        prefs.edit().putInt(KEY_MAX_STDERR, v).apply()
        TermuxRuntime.maxStderrBytes = v
    }

    fun setAptWrapEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_APT_WRAP, enabled).apply()
        TermuxRuntime.aptWrapEnabled = enabled
    }

    companion object {
        private const val KEY_COMMAND_TIMEOUT = "command_timeout_ms"
        private const val KEY_VERIFY_TIMEOUT = "verify_timeout_ms"
        private const val KEY_WORKING_DIR = "default_working_dir"
        private const val KEY_MAX_STDOUT = "max_stdout_bytes"
        private const val KEY_MAX_STDERR = "max_stderr_bytes"
        private const val KEY_APT_WRAP = "apt_wrap_enabled"
        private const val KEY_VERIFIED_AT = "last_verified_at_ms"
    }
}

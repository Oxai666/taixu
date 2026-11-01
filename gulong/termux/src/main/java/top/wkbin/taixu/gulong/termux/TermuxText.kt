package top.wkbin.taixu.gulong.termux

/**
 * Termux 通道用的纯文本工具：UTF-8 字节预算截断、单引号转义、后台脱离包装。
 *
 * 全部是纯函数，不碰 Android API，可直接单测。
 */

/** 单个码点的 UTF-8 编码字节数。 */
internal fun utf8Width(cp: Int): Int = when {
    cp <= 0x7F -> 1
    cp <= 0x7FF -> 2
    cp <= 0xFFFF -> 3
    else -> 4
}

/**
 * 保留 [s] 开头至多 [maxBytes] 字节的 UTF-8，切口对齐到码点边界，
 * 绝不把多字节序列（含代理对 emoji）劈成两半。本来就装得下则原样返回。
 */
internal fun takeFirstUtf8Bytes(s: String, maxBytes: Int): String {
    if (maxBytes <= 0) return ""
    if (s.toByteArray(Charsets.UTF_8).size <= maxBytes) return s
    var bytes = 0
    var i = 0
    while (i < s.length) {
        val cp = s.codePointAt(i)
        val w = utf8Width(cp)
        if (bytes + w > maxBytes) break
        bytes += w
        i += Character.charCount(cp)
    }
    return s.substring(0, i)
}

/**
 * 保留 [s] 结尾至多 [maxBytes] 字节的 UTF-8，切口同样对齐码点边界。
 * 与 [takeFirstUtf8Bytes] 互为头保/尾保两种截断策略。
 */
internal fun takeLastUtf8Bytes(s: String, maxBytes: Int): String {
    if (maxBytes <= 0) return ""
    if (s.toByteArray(Charsets.UTF_8).size <= maxBytes) return s
    var bytes = 0
    var i = s.length
    while (i > 0) {
        val cp = s.codePointBefore(i)
        val w = utf8Width(cp)
        if (bytes + w > maxBytes) break
        bytes += w
        i -= Character.charCount(cp)
    }
    return s.substring(i)
}

/** POSIX 单引号转义：包住整串并把内部单引号转成 `'\''`。 */
internal fun shellSingleQuote(s: String): String =
    "'" + s.replace("'", "'\\''") + "'"

/**
 * 把命令包成「完全脱离」的形式并立刻回一行 PID 标记。
 *
 * 为什么必须这么做：远端子进程会继承通道的三个标准流句柄，
 * 前台 shell 退出后管道迟迟不见 EOF，调用方就会一直挂到超时。
 * 这里改用 nohup（免疫会话关闭时的 SIGHUP）、三流全部重定向、后台执行，再回显 PID，
 * 通道立即读到 EOF，调用当场拿到 PID 返回。
 */
internal fun wrapDetachedCommand(command: String): String =
    "nohup sh -c ${shellSingleQuote(command)} >/dev/null 2>&1 </dev/null & echo \"gulong_bg_pid=\$!\""

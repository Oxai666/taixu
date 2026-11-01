package top.wkbin.taixu.harness

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/**
 * 古龙 · 外部 Termux 工具（`termux`）的 schema 定义。
 *
 * 为什么单独一个文件：`ProviderClient.kt` 的 `TOOLS` 列表受架构棘轮约束
 * （只能下调行数，不许增长），往里塞十几行 schema 会直接把棘轮顶破。
 * 工具定义本身是纯数据，抽成独立文件既满足棘轮，也让「工具清单」与「网络客户端」
 * 这两件不相干的事解耦。
 */
object TermuxToolDefinition {

    val TOOL: ApiToolDefinition = ApiToolDefinition(
        function = ApiFunctionDefinition(
            name = "termux",
            description = "在用户手机上已安装的 Termux 应用里执行 shell 命令——这是外部 Termux，不是内置 PRoot 沙箱（那个用 base）。" +
                "默认后台执行并把 stdout/stderr/exit_code 回给你，可据此判断包是否装好、读文件、跑脚本。" +
                "interactive=true 改为开一个用户可见的 Termux 会话，该模式不回传输出。" +
                "Termux 侧需一次性设置 allow-external-apps=true（~/.termux/termux.properties）并重启 Termux。" +
                "command 模式下 apt/apt-get 会自动注入 DEBIAN_FRONTEND=noninteractive 与 --force-confdef/--force-confold，" +
                "除非用户明确要求无人值守升级，不要自己再加 -y。",
            parameters = Json.parseToJsonElement(
                """{"type":"object","properties":{""" +
                    """"command":{"type":"string","description":"shell 命令行，例如 'pkg update && pkg upgrade -y'。与 executable+arguments 互斥。"},""" +
                    """"executable":{"type":"string","description":"可执行文件绝对路径，例如 /data/data/com.termux/files/usr/bin/bash。与 arguments 配对使用。"},""" +
                    """"arguments":{"type":"array","description":"executable 模式的参数列表","items":{"type":"string"}},""" +
                    """"working_dir":{"type":"string","description":"工作目录，默认 /data/data/com.termux/files/home"},""" +
                    """"interactive":{"type":"boolean","description":"true 则开一个可见 Termux 会话且不捕获输出，默认 false（后台 + 捕获）"},""" +
                    """"background":{"type":"boolean","description":"仅 command 模式。true 则完全脱离启动（nohup + 三流重定向）并立刻返回 PID，适合服务器/长驻进程，默认 false"},""" +
                    """"timeout_seconds":{"type":"integer","description":"捕获模式超时秒数；省略或传 0 用用户配置的默认值，上限 600 秒"}}}""",
            ).jsonObject,
        ),
    )
}

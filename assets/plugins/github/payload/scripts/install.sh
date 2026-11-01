#!/bin/sh
set -eu
umask 077
for command in python3 git ssh ssh-keygen; do
    command -v "$command" >/dev/null 2>&1 || {
        printf '缺少 %s。请先在 Linux 安装 python3 git openssh-client ca-certificates，再重新安装插件。\n' "$command" >&2
        exit 1
    }
done
test -x /usr/bin/python3
/usr/bin/python3 -I -c 'import sys; assert sys.version_info >= (3, 9), "Python 3.9 or newer required"'
test "$(od -An -t x1 -N 4 "$GULONG_PLUGIN_PAYLOAD/vendor/gh" | tr -d ' \n')" = 7f454c46
test "$(od -An -t x1 -j 18 -N 2 "$GULONG_PLUGIN_PAYLOAD/vendor/gh" | tr -d ' \n')" = b700
printf '[GULONG_PROGRESS:20] [COPY] 复制官方 gh 与配置服务\n'
mkdir -p "$GULONG_TOOL_DIR/bin" "$GULONG_TOOL_DIR/lib" "$GULONG_TOOL_DIR/web" "$GULONG_TOOL_DATA"
chmod 700 "$GULONG_TOOL_DATA"
cp "$GULONG_PLUGIN_PAYLOAD/vendor/gh" "$GULONG_TOOL_DIR/lib/gh"
cp "$GULONG_PLUGIN_PAYLOAD/vendor/LICENSE" "$GULONG_TOOL_DIR/lib/gh-LICENSE"
for entry in github_plugin.py service_protocol.py; do
    cp "$GULONG_PLUGIN_PAYLOAD/lib/$entry" "$GULONG_TOOL_DIR/lib/$entry"
done
for entry in gh gh-ui gh-git; do
    cp "$GULONG_PLUGIN_PAYLOAD/bin/$entry" "$GULONG_TOOL_DIR/bin/$entry"
    chmod 755 "$GULONG_TOOL_DIR/bin/$entry"
done
for entry in index.html app.js app.css; do
    cp "$GULONG_PLUGIN_PAYLOAD/web/$entry" "$GULONG_TOOL_DIR/web/$entry"
done
chmod 755 "$GULONG_TOOL_DIR/lib/gh"
printf '[GULONG_PROGRESS:80] [VERIFY] 验证 gh 可执行文件\n'
"$GULONG_TOOL_DIR/lib/gh" --version
/usr/bin/python3 -I -c 'import ast,sys; ast.parse(open(sys.argv[1], encoding="utf-8").read())' "$GULONG_TOOL_DIR/lib/github_plugin.py"
printf '[GULONG_PROGRESS:100] [VERIFY] 安装完成，请在插件详情启动服务并打开管理页面\n'

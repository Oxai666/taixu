#!/bin/sh
set -eu
command -v git >/dev/null || { echo '请先安装 git；离线安装器不会联网获取依赖' >&2; exit 1; }
mkdir -p "$GULONG_TOOL_DIR/bin" "$GULONG_TOOL_DIR/libexec" "$GULONG_TOOL_DIR/licenses"
cp "$GULONG_PLUGIN_PAYLOAD/bin/lazygit" "$GULONG_TOOL_DIR/bin/lazygit"
cp "$GULONG_PLUGIN_PAYLOAD/vendor/lazygit" "$GULONG_TOOL_DIR/libexec/lazygit"
cp "$GULONG_PLUGIN_PAYLOAD/vendor/LICENSE" "$GULONG_TOOL_DIR/licenses/LICENSE"
cp "$GULONG_PLUGIN_PAYLOAD/config.yml" "$GULONG_TOOL_DIR/config.yml"
chmod 755 "$GULONG_TOOL_DIR/bin/lazygit" "$GULONG_TOOL_DIR/libexec/lazygit"
"$GULONG_TOOL_DIR/libexec/lazygit" --version

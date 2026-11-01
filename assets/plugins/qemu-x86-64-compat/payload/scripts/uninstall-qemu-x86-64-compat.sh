#!/bin/sh
set -eu

TOOL_DIR="${GULONG_TOOL_DIR:?missing GULONG_TOOL_DIR}"
COMPAT_ROOT="/opt/taixu/compat/x86_64"

rm -f /opt/taixu/bin/qemu-x86_64

rm -rf "$TOOL_DIR"
rm -rf "$COMPAT_ROOT"

echo "QEMU x86_64 user-mode 插件已卸载"

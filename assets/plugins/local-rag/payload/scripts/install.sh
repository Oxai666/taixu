#!/bin/sh
set -eu
command -v git >/dev/null || { echo '请先安装 git，以检查项目忽略规则' >&2; exit 1; }
/usr/bin/python3 -I -c 'import sys,sqlite3; assert sys.version_info >= (3,9); c=sqlite3.connect(":memory:"); c.execute("CREATE VIRTUAL TABLE probe USING fts5(body)")' || {
  echo '需要预先安装含 sqlite3/FTS5 的 Python 3；安装器不会联网获取依赖' >&2
  exit 1
}
mkdir -p "$GULONG_TOOL_DIR/bin" "$GULONG_TOOL_DIR/lib"
cp "$GULONG_PLUGIN_PAYLOAD/bin/local-rag" "$GULONG_TOOL_DIR/bin/local-rag"
cp "$GULONG_PLUGIN_PAYLOAD/lib/"*.py "$GULONG_TOOL_DIR/lib/"
chmod 755 "$GULONG_TOOL_DIR/bin/local-rag"

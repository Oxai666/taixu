#!/bin/sh
# ==============================================================================
# Gulong (LinuxAIRuntime) - Node.js Package Managers (pnpm / yarn) Setup
# ==============================================================================
set -e

echo "==> [Gulong] 正在配置现代包管理器 (pnpm / yarn)..."

npm install -g pnpm yarn --registry=https://registry.npmmirror.com 2>/dev/null || \
npm install -g pnpm yarn 2>/dev/null || true

echo "==> [Gulong] ✅ pnpm 与 yarn 配置完成！"
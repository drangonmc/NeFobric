#!/bin/sh
# NeFobric @VERSION@ 客户端安装脚本（Linux / macOS）
# 用法：sh install-client.sh [.minecraft 目录]
set -e
VERSION="@VERSION@"
MC="@MC@"
ID="@VERSION_ID@"
HERE="$(cd "$(dirname "$0")" && pwd)"
if [ -n "$1" ]; then
  MC_DIR="$1"
elif [ "$(uname)" = "Darwin" ]; then
  MC_DIR="$HOME/Library/Application Support/minecraft"
else
  MC_DIR="$HOME/.minecraft"
fi

echo "NeFobric $VERSION 客户端安装"
echo "Minecraft 目录：$MC_DIR"
if [ ! -f "$MC_DIR/versions/$MC/$MC.json" ]; then
  echo "找不到原版 $MC。请先在启动器里安装并运行一次原版 $MC，或把 .minecraft 目录作为参数传入。" >&2
  exit 1
fi

mkdir -p "$MC_DIR/libraries/dev/nefobric/nefobric-loader/$VERSION" "$MC_DIR/versions/$ID" "$MC_DIR/mods"
cp "$HERE/nefobric-loader-$VERSION.jar" "$MC_DIR/libraries/dev/nefobric/nefobric-loader/$VERSION/"
cp "$HERE/$ID.json" "$MC_DIR/versions/$ID/"
[ -f "$MC_DIR/versions/$ID/$ID.jar" ] || : > "$MC_DIR/versions/$ID/$ID.jar"

echo "安装完成：$MC_DIR/versions/$ID"
echo "  在启动器里选择版本 $ID，模组放进 $MC_DIR/mods"

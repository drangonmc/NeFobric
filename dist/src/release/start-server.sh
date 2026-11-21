#!/bin/sh
# NeFobric @VERSION@ 专用服务端：把本文件、nefobric-loader-@VERSION@.jar 和原版 @MC@ 的 server.jar 放在同一个文件夹
cd "$(dirname "$0")"
exec java -Xmx2G -jar nefobric-loader-@VERSION@.jar nogui

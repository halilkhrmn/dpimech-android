#!/bin/sh
# Builds ciadpi for the host (Linux) into build/host/, for the integration tests in core/.
# The submodule stays clean: objects are not written next to the sources.
set -eu
root=$(cd "$(dirname "$0")/.." && pwd)
src="$root/native/byedpi"
out="$root/build/host"
mkdir -p "$out"
${CC:-cc} -D_DEFAULT_SOURCE -I"$src" -std=c99 -O2 -w \
    "$src"/packets.c "$src"/main.c "$src"/conev.c "$src"/proxy.c \
    "$src"/desync.c "$src"/mpool.c "$src"/extend.c \
    -o "$out/ciadpi"
echo "$out/ciadpi"

APP_ABI := arm64-v8a armeabi-v7a x86_64 x86
APP_PLATFORM := android-26
APP_MODULES := hev-socks5-tunnel ciadpi
APP_SUPPORT_FLEXIBLE_PAGE_SIZES := true
# hev-socks5-tunnel registers its natives on this class (engine module).
APP_CFLAGS := -DPKGNAME=io/github/halilkhrmn/dpimech/engine -DCLSNAME=TProxy
# Reproducible builds: no absolute build paths in the binaries.
APP_CFLAGS += -ffile-prefix-map=$(abspath $(NDK_PROJECT_PATH))=.

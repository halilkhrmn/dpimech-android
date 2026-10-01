# ndk-build entry point for the engines; run by the :engine module (see engine/build.gradle.kts).
NATIVE_PATH := $(call my-dir)

# hev-socks5-tunnel: shared library with its own JNI glue (PKGNAME/CLSNAME in Application.mk).
include $(NATIVE_PATH)/hev-socks5-tunnel/Android.mk

# ciadpi (ByeDPI): a plain executable. The engine module packages it as libciadpi.so so Android
# installs it into nativeLibraryDir, where the app may execute it.
include $(CLEAR_VARS)
LOCAL_PATH := $(NATIVE_PATH)/byedpi
LOCAL_MODULE := ciadpi
LOCAL_SRC_FILES := packets.c main.c conev.c proxy.c desync.c mpool.c extend.c
LOCAL_CFLAGS := -std=c99 -O2 -D_DEFAULT_SOURCE -Wno-unused -Wno-unused-parameter
LOCAL_LDFLAGS := -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384
include $(BUILD_EXECUTABLE)

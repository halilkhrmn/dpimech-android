package io.github.halilkhrmn.dpimech.engine

/**
 * hev-socks5-tunnel's JNI interface (`src/hev-jni.c`); the library registers these natives on
 * this class by name when it loads. Starting runs the tunnel on a native thread.
 */
internal object TProxy {
    init {
        System.loadLibrary("hev-socks5-tunnel")
    }

    @JvmStatic
    external fun TProxyStartService(configPath: String, fd: Int): Boolean

    @JvmStatic
    external fun TProxyStopService(): Boolean

    @JvmStatic
    external fun TProxyIsRunning(): Boolean

    /** tx packets, tx bytes, rx packets, rx bytes. */
    @JvmStatic
    external fun TProxyGetStats(): LongArray?
}

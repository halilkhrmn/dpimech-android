package io.github.halilkhrmn.dpimech.engine

import android.content.Context
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * A running ciadpi (ByeDPI) process. It runs as its own process from nativeLibraryDir, so every
 * profile and every Strategy Lab test gets a fresh engine that can be stopped cleanly; its
 * sockets belong to DPIMech's uid, which is kept out of the VPN.
 */
class Ciadpi private constructor(private val process: Process, val port: Int) {

    val isAlive: Boolean get() = process.isAlive

    /** Blocks until the process exits; returns its exit code. */
    fun waitFor(): Int = process.waitFor()

    fun stop() {
        process.destroy()
        if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly()
    }

    companion object {
        fun binary(context: Context) = File(context.applicationInfo.nativeLibraryDir, "libciadpi.so")

        /** A free local TCP port for ciadpi's SOCKS5 listener. */
        fun freePort(): Int = ServerSocket(0).use { it.localPort }

        /**
         * Starts ciadpi with [args] (from `ByeDpiCommand.build`) and waits until it accepts
         * connections on [port]. Output lines go to [EngineLog].
         */
        fun start(context: Context, args: List<String>, port: Int, label: String): Ciadpi {
            val process = ProcessBuilder(listOf(binary(context).path) + args)
                .redirectErrorStream(true)
                .start()
            thread(name = "ciadpi-log-$label", isDaemon = true) {
                try {
                    process.inputStream.bufferedReader().forEachLine { EngineLog.add("ciadpi[$label] $it") }
                } catch (_: IOException) {
                }
            }
            val engine = Ciadpi(process, port)
            repeat(50) {
                if (!process.isAlive) {
                    throw IOException("ByeDPI stopped at start (exit ${process.exitValue()})")
                }
                try {
                    Socket().use { it.connect(InetSocketAddress(LOCALHOST, port), 100) }
                    return engine
                } catch (_: IOException) {
                    Thread.sleep(50)
                }
            }
            engine.stop()
            throw IOException("ByeDPI did not start listening on port $port")
        }

        private const val LOCALHOST = "127.0.0.1"
    }
}

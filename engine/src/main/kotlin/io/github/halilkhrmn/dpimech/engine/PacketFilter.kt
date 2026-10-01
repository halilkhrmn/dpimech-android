package io.github.halilkhrmn.dpimech.engine

import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import io.github.halilkhrmn.dpimech.core.DohClient
import io.github.halilkhrmn.dpimech.core.IpPacket
import io.github.halilkhrmn.dpimech.core.TunnelFilter
import java.io.FileDescriptor
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Sits between the TUN and hev-socks5-tunnel when DNS over HTTPS or the QUIC switch is on:
 * hev gets one end of a packet socket pair instead of the TUN, and every packet the apps send
 * passes [TunnelFilter] first. DNS queries are answered over DoH (plain DNS through the tunnel
 * when DoH is not reachable), QUIC is dropped, the rest goes to hev unchanged.
 */
internal class PacketFilter(
    private val tun: ParcelFileDescriptor,
    private val filter: TunnelFilter,
    private val doh: DohClient,
    mtu: Int,
) {
    private val ours = FileDescriptor()
    private val theirs = FileDescriptor()
    /** The end handed to hev; closed in [stop] after hev has stopped. */
    val hevFd: ParcelFileDescriptor

    @Volatile
    private var running = true
    private val size = mtu + 64
    private val dns = Executors.newFixedThreadPool(4) { r -> Thread(r, "doh").apply { isDaemon = true } }
    private val tunLock = Any()
    private val up: Thread
    private val down: Thread

    init {
        Os.socketpair(OsConstants.AF_UNIX, OsConstants.SOCK_SEQPACKET, 0, ours, theirs)
        for (fd in listOf(ours, theirs)) {
            runCatching { Os.setsockoptInt(fd, OsConstants.SOL_SOCKET, OsConstants.SO_SNDBUF, BUFFER) }
            runCatching { Os.setsockoptInt(fd, OsConstants.SOL_SOCKET, OsConstants.SO_RCVBUF, BUFFER) }
        }
        Os.fcntlInt(ours, OsConstants.F_SETFL, Os.fcntlInt(ours, OsConstants.F_GETFL, 0) or OsConstants.O_NONBLOCK)
        hevFd = ParcelFileDescriptor.dup(theirs)
        Os.close(theirs)
        up = thread(name = "tun-up", isDaemon = true) { pump(tun.fileDescriptor, ::fromApps) }
        down = thread(name = "tun-down", isDaemon = true) { pump(ours) { buf, n -> toApps(buf, n) } }
    }

    /** Reads packets from [fd] until stopped; [handle] gets each one. */
    private fun pump(fd: FileDescriptor, handle: (ByteArray, Int) -> Unit) {
        val buf = ByteArray(size)
        val poll = arrayOf(StructPollfd().apply { this.fd = fd; events = OsConstants.POLLIN.toShort() })
        while (running) {
            try {
                if (Os.poll(poll, POLL_MS) <= 0) continue
                while (running) {
                    val n = Os.read(fd, buf, 0, buf.size)
                    if (n <= 0) break
                    handle(buf, n)
                }
            } catch (e: ErrnoException) {
                when (e.errno) {
                    OsConstants.EAGAIN, OsConstants.EINTR -> Unit
                    else -> {
                        if (running) EngineLog.add("packet filter: ${e.message}")
                        return
                    }
                }
            }
        }
    }

    private fun fromApps(buf: ByteArray, n: Int) {
        when (filter.classify(buf, n)) {
            TunnelFilter.Verdict.PASS -> toHev(buf, n)
            TunnelFilter.Verdict.DROP -> Unit
            TunnelFilter.Verdict.DNS -> {
                val packet = buf.copyOf(n)
                try {
                    dns.execute { answer(packet) }
                } catch (_: RejectedExecutionException) {
                    // stopping
                }
            }
        }
    }

    private fun answer(packet: ByteArray) {
        val q = IpPacket.udp(packet, packet.size) ?: return toHev(packet, packet.size)
        val reply = doh.resolve(q.payload)
        if (reply == null || !running) {
            // DoH unavailable: let the query go the old way, through hev and ciadpi.
            toHev(packet, packet.size)
            return
        }
        val out = IpPacket.reply(q, reply)
        toApps(out, out.size)
    }

    private fun toHev(buf: ByteArray, n: Int) {
        try {
            Os.write(ours, buf, 0, n)
        } catch (_: ErrnoException) {
            // Queue full or closing: drop, like a congested link would.
        }
    }

    private fun toApps(buf: ByteArray, n: Int) {
        try {
            synchronized(tunLock) { Os.write(tun.fileDescriptor, buf, 0, n) }
        } catch (_: ErrnoException) {
        }
    }

    /** Call after hev has stopped. The TUN itself is closed by the owner. */
    fun stop() {
        running = false
        dns.shutdownNow()
        up.join(2 * POLL_MS.toLong())
        down.join(2 * POLL_MS.toLong())
        dns.awaitTermination(1, TimeUnit.SECONDS)
        runCatching { Os.close(ours) }
        runCatching { hevFd.close() }
    }

    private companion object {
        const val POLL_MS = 500
        const val BUFFER = 4 shl 20
    }
}

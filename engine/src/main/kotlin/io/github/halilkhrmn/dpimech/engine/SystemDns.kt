package io.github.halilkhrmn.dpimech.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.DnsResolver
import android.net.Network
import android.os.Build
import android.os.CancellationSignal
import io.github.halilkhrmn.dpimech.core.DnsMessage
import io.github.halilkhrmn.dpimech.core.DnsUpstream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * The network's own DNS, asked from outside the VPN (DPIMech is not in its own tunnel): the
 * provider's resolver, or Android's Private DNS when one is set. Android 10+ uses the system
 * resolver for the real network; older versions send the query to the network's first DNS
 * server over UDP. Used when the user picks "system DNS" and as the fallback when the chosen
 * server does not answer.
 */
internal class SystemDns(context: Context) : DnsUpstream {
    private val cm = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    override fun resolve(query: ByteArray): ByteArray? {
        if (!DnsMessage.isQuery(query)) return null
        // The real network, never the VPN: NetworkIdentity skips VPN networks.
        val network = NetworkIdentity.network() ?: return null
        val answer = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) rawQuery(network, query) else udpQuery(network, query)
        }.onFailure { EngineLog.add("system DNS: $it") }.getOrNull()
        // Some resolvers renumber the query; the app expects its own id back.
        return answer?.takeIf { it.size >= 12 }?.let { DnsMessage.withId(it, DnsMessage.id(query)) }
    }

    @android.annotation.TargetApi(Build.VERSION_CODES.Q)
    private fun rawQuery(network: Network, query: ByteArray): ByteArray? {
        val result = AtomicReference<ByteArray?>()
        val done = CountDownLatch(1)
        val cancel = CancellationSignal()
        DnsResolver.getInstance().rawQuery(
            network, query, DnsResolver.FLAG_EMPTY, Runnable::run, cancel,
            object : DnsResolver.Callback<ByteArray> {
                override fun onAnswer(answer: ByteArray, rcode: Int) {
                    result.set(answer)
                    done.countDown()
                }

                override fun onError(error: DnsResolver.DnsException) {
                    done.countDown()
                }
            },
        )
        if (!done.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) cancel.cancel()
        return result.get()
    }

    private fun udpQuery(network: Network, query: ByteArray): ByteArray? {
        val server = cm.getLinkProperties(network)?.dnsServers?.firstOrNull() ?: return null
        DatagramSocket().use { socket ->
            network.bindSocket(socket)
            socket.soTimeout = TIMEOUT_MS.toInt()
            socket.send(DatagramPacket(query, query.size, server, 53))
            val buf = ByteArray(4096)
            val reply = DatagramPacket(buf, buf.size)
            socket.receive(reply)
            return buf.copyOf(reply.length)
        }
    }

    private companion object {
        const val TIMEOUT_MS = 4000L
    }
}

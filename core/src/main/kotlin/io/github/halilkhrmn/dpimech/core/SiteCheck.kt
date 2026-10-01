package io.github.halilkhrmn.dpimech.core

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/** Opens the TCP connection to a site: directly, or through ciadpi. */
fun interface Connector {
    fun open(host: String, port: Int, timeoutMs: Int): Socket

    companion object {
        val DIRECT = Connector { host, port, timeout ->
            Socket().apply {
                connect(InetSocketAddress(host, port), timeout)
                soTimeout = timeout
            }
        }

        fun socks(proxyPort: Int) = Connector { host, port, timeout -> Socks5.connect(proxyPort, host, port, timeout) }
    }
}

/**
 * "Does the site open?" as on desktop (`health::site_opens`): an HTTPS request to `/` whose answer
 * starts arriving. A redirect counts: it already proves the TLS handshake got through. A DPI
 * reset, a timeout or a certificate error (block pages) counts as closed.
 */
class SiteCheck(
    private val tls: SSLSocketFactory = SSLSocketFactory.getDefault() as SSLSocketFactory,
    private val timeoutMs: Int = 5000,
    /** Some DPI boxes cut the connection after the first few kilobytes. */
    private val bodySample: Int = 16 * 1024,
) {
    fun opens(connector: Connector, host: String, port: Int = 443): Boolean = try {
        connector.open(host, port, timeoutMs).use { raw ->
            (tls.createSocket(raw, host, port, true) as SSLSocket).use { s ->
                s.sslParameters = s.sslParameters.apply { serverNames = listOf(SNIHostName(host)) }
                s.soTimeout = timeoutMs
                s.startHandshake()
                s.getOutputStream().apply {
                    write(
                        "GET / HTTP/1.1\r\nHost: $host\r\nUser-Agent: $USER_AGENT\r\nAccept: */*\r\nConnection: close\r\n\r\n"
                            .toByteArray(),
                    )
                    flush()
                }
                val inp = s.getInputStream()
                val buf = ByteArray(4096)
                var read = 0
                var first = true
                while (read < bodySample) {
                    val n = inp.read(buf)
                    if (n < 0) break
                    if (first && !String(buf, 0, minOf(n, 5)).startsWith("HTTP/")) return false
                    first = false
                    read += n
                }
                read > 0
            }
        }
    } catch (_: IOException) {
        false
    } catch (_: IllegalArgumentException) {
        false
    }

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Mobile Safari/537.36"
    }
}

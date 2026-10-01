package io.github.halilkhrmn.dpimech.core

import java.io.DataInputStream
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * Runs the real ciadpi (built from `native/byedpi`, path in `$CIADPI`) with arguments from
 * [ByeDpiCommand] and checks which connections get desynced, from ciadpi's debug log.
 * Skipped when `$CIADPI` is not set; CI builds ciadpi and sets it.
 */
class CiadpiIntegrationTest {
    private val ciadpi = System.getenv("CIADPI")

    @Test
    fun domainFilterDesyncsOnlyListedHosts() {
        val hosts = listOf("discord.com", "cdn.discordapp.com", "example.org")
        val expected = mapOf("discord.com" to true, "cdn.discordapp.com" to true, "example.org" to false)
        assertEquals(expected, desynced("-x 2 -r1+s", hosts, domainFilter = true))
        assertEquals(expected, desynced("-x 2 -d1 -a1 -At,r,s -s1+s -r1+s", hosts, domainFilter = true))
    }

    @Test
    fun withoutFilterEverythingIsDesynced() {
        val hosts = listOf("discord.com", "example.org")
        assertEquals(hosts.associateWith { true }, desynced("-x 2 -s1+s", hosts, domainFilter = false))
    }

    /** host → whether ciadpi applied a split/disorder/fake to its first packet. */
    private fun desynced(strategy: String, hosts: List<String>, domainFilter: Boolean): Map<String, Boolean> {
        assumeTrue(ciadpi != null, "set CIADPI to the ciadpi binary to run")
        val lists = Files.createTempDirectory("lists").toFile()
        val hostlist = File(lists, "p.txt").apply { writeText(Hostlist.body(listOf("discord.com", "discordapp.com"))) }
        val port = ServerSocket(0).use { it.localPort }
        val args = ByeDpiCommand.build(
            ByeDpiCommand.Request(strategy, port, lists, hostlist, domainFilter),
        ).getOrThrow()
        val log = File(lists, "ciadpi.log")
        val proc = ProcessBuilder(listOf(ciadpi!!) + args).redirectErrorStream(true).redirectOutput(log).start()
        try {
            waitForPort(port)
            for (host in hosts) connectThrough(port, host)
            Thread.sleep(300)
        } finally {
            proc.destroy()
            proc.waitFor(5, TimeUnit.SECONDS)
        }
        return parse(log.readLines()).filterKeys { it in hosts }
    }

    /**
     * Each "desync TCP" entry is followed by "host: <name> (…)". An untouched packet goes out
     * at once ("send: …"); a desynced one logs its method first ("split: …", "tlsrec: …").
     */
    private fun parse(lines: List<String>): Map<String, Boolean> {
        val out = mutableMapOf<String, Boolean>()
        var i = 0
        while (i < lines.size) {
            if (lines[i].startsWith("desync TCP:")) {
                val host = lines.getOrNull(i + 1)?.removePrefix("host: ")?.substringBefore(' ')
                val next = lines.getOrNull(i + 2).orEmpty()
                if (host != null && host !in out) out[host] = !next.startsWith("send:")
            }
            i++
        }
        return out
    }

    private fun connectThrough(proxyPort: Int, host: String) {
        val upstream = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        thread(isDaemon = true) {
            upstream.use { s -> s.accept().use { it.getInputStream().read(ByteArray(4096)); Thread.sleep(1000) } }
        }
        Socket("127.0.0.1", proxyPort).use { s ->
            s.soTimeout = 3000
            val out = s.getOutputStream()
            val inp = DataInputStream(s.getInputStream())
            out.write(byteArrayOf(5, 1, 0))
            inp.readFully(ByteArray(2))
            val req = ByteBuffer.allocate(10).put(byteArrayOf(5, 1, 0, 1, 127, 0, 0, 1))
                .putShort(upstream.localPort.toShort()).array()
            out.write(req)
            val reply = ByteArray(10).also { inp.readFully(it) }
            assertEquals(0, reply[1].toInt(), "SOCKS5 connect to upstream for $host")
            out.write(clientHello(host))
            out.flush()
            Thread.sleep(300)
        }
    }

    private fun clientHello(host: String): ByteArray {
        val engine = SSLContext.getDefault().createSSLEngine(host, 443)
        engine.useClientMode = true
        engine.sslParameters = engine.sslParameters.apply { serverNames = listOf(SNIHostName(host)) }
        val buf = ByteBuffer.allocate(engine.session.packetBufferSize)
        engine.wrap(ByteBuffer.allocate(0), buf)
        buf.flip()
        return ByteArray(buf.remaining()).also { buf.get(it) }
    }

    private fun waitForPort(port: Int) {
        repeat(50) {
            try {
                Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 100) }
                return
            } catch (_: Exception) {
                Thread.sleep(100)
            }
        }
        error("ciadpi did not start listening on $port")
    }
}

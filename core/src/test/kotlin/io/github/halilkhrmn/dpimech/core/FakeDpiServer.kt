package io.github.halilkhrmn.dpimech.core

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager
import kotlin.concurrent.thread

/**
 * An HTTPS server on 127.0.0.1 with a built-in "DPI": like many real boxes it only parses the
 * first TLS record of a connection, and resets the connection when that record names a
 * [blocked] host. ciadpi's `--tlsrec` splits the record inside the SNI, which gets past it.
 */
class FakeDpiServer(private val blocked: Set<String>) : AutoCloseable {
    private val server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
    val port: Int get() = server.localPort
    private val context = serverContext()

    init {
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val s = runCatching { server.accept() }.getOrNull() ?: break
                thread(isDaemon = true) { runCatching { handle(s) } }
            }
        }
    }

    private fun handle(s: Socket) = s.use {
        s.soTimeout = 5000
        val inp = DataInputStream(s.getInputStream())
        val header = ByteArray(5).also { inp.readFully(it) }
        val length = ((header[3].toInt() and 0xff) shl 8) or (header[4].toInt() and 0xff)
        val record = ByteArray(length).also { inp.readFully(it) }
        val text = String(record, Charsets.ISO_8859_1)
        if (blocked.any { it in text }) {
            s.setSoLinger(true, 0) // RST, like a DPI box
            return
        }
        val tls = context.socketFactory as SSLSocketFactory
        (tls.createSocket(s, ByteArrayInputStream(header + record), true) as SSLSocket).use { t ->
            t.useClientMode = false
            t.startHandshake()
            t.inputStream.read(ByteArray(4096))
            t.outputStream.write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok".toByteArray())
            t.outputStream.flush()
        }
    }

    override fun close() = server.close()

    companion object {
        private val keystore: File by lazy {
            val dir = Files.createTempDirectory("dpi").toFile()
            val ks = File(dir, "k.p12")
            val p = ProcessBuilder(
                File(System.getProperty("java.home"), "bin/keytool").path, "-genkeypair", "-keystore", ks.path,
                "-storetype", "PKCS12", "-storepass", "secret", "-keyalg", "EC", "-alias", "s",
                "-dname", "CN=test", "-validity", "2",
            ).redirectErrorStream(true).start()
            p.inputStream.readAllBytes()
            check(p.waitFor(60, TimeUnit.SECONDS) && p.exitValue() == 0) { "keytool failed" }
            ks
        }

        private fun serverContext(): SSLContext {
            val ks = KeyStore.getInstance("PKCS12").apply { keystore.inputStream().use { load(it, "secret".toCharArray()) } }
            val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(ks, "secret".toCharArray()) }
            return SSLContext.getInstance("TLS").apply { init(kmf.keyManagers, null, null) }
        }

        /** The test client trusts the self-signed certificate. */
        val trustAll: SSLSocketFactory by lazy {
            val tm = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
            SSLContext.getInstance("TLS").apply { init(null, arrayOf(tm), null) }.socketFactory
        }
    }
}

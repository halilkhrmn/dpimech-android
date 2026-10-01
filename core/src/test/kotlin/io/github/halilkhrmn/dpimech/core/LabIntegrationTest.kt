package io.github.halilkhrmn.dpimech.core

import java.net.InetSocketAddress
import java.net.Socket
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * The Strategy Lab against the real ciadpi (`$CIADPI`) and a local server with a fake DPI:
 * the baseline and a do-nothing strategy fail on the blocked host, `--tlsrec` gets through and
 * wins. Requests go through ciadpi's SOCKS5 exactly as in the app; only the address is local.
 */
class LabIntegrationTest {
    private val ciadpi = System.getenv("CIADPI")

    private val launcher = EngineLauncher { args, port ->
        val p = ProcessBuilder(listOf(ciadpi!!) + args).redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
        repeat(50) {
            if (!p.isAlive) error("ciadpi exited (${p.exitValue()})")
            try {
                Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 100) }
                return@EngineLauncher AutoCloseable { p.destroy(); p.waitFor(5, TimeUnit.SECONDS) }
            } catch (_: Exception) {
                Thread.sleep(50)
            }
        }
        p.destroy()
        error("ciadpi did not listen")
    }

    @Test
    fun siteCheckAndSocksAgainstFakeDpi() {
        assumeTrue(ciadpi != null, "set CIADPI to run")
        FakeDpiServer(setOf("blocked.test")).use { server ->
            val check = SiteCheck(FakeDpiServer.trustAll, timeoutMs = 3000)
            val local = Connector { _, _, t -> Connector.DIRECT.open("127.0.0.1", server.port, t) }
            assertTrue(check.opens(local, "open.test", server.port))
            assertFalse(check.opens(local, "blocked.test", server.port))

            val port = java.net.ServerSocket(0).use { it.localPort }
            val args = ByeDpiCommand.build(ByeDpiCommand.Request("-r 1+s", port, Files.createTempDirectory("l").toFile())).getOrThrow()
            launcher.start(args, port).use {
                assertTrue(Socks5.greets(port))
                val viaCiadpi = Connector { _, p, t -> Socks5.connect(port, "127.0.0.1", p, t) }
                assertTrue(check.opens(viaCiadpi, "blocked.test", server.port))
            }
            assertFalse(Socks5.greets(port))
        }
    }

    @Test
    fun labFindsTheStrategyThatWorks() {
        assumeTrue(ciadpi != null, "set CIADPI to run")
        FakeDpiServer(setOf("blocked.test")).use { server ->
            val runner = LabRunner(
                launcher = launcher,
                listsDir = Files.createTempDirectory("lab").toFile(),
                opens = SiteCheck(FakeDpiServer.trustAll, timeoutMs = 3000)::opens,
                viaProxy = { proxy -> Connector { _, p, t -> Socks5.connect(proxy, "127.0.0.1", p, t) } },
                direct = Connector { _, p, t -> Connector.DIRECT.open("127.0.0.1", p, t) },
            )
            val std = LabResult.STANDARD_SET
            val results = runner.run(
                LabRequest(
                    strategies = listOf(
                        LabStrategy("Nothing", "-c 512", std),
                        LabStrategy("TLS record split", "-r 1+s", std),
                        LabStrategy("Bad", "-H /etc/passwd", std),
                    ),
                    probes = listOf("open.test", "blocked.test"),
                    port = server.port,
                ),
                listener = { _, _, _ -> },
            )
            val byName = results.associateBy { it.strategy?.name }
            assertEquals(listOf("blocked.test"), byName.getValue(null).failedDomains)
            assertEquals(listOf("blocked.test"), byName.getValue("Nothing").failedDomains)
            assertTrue(byName.getValue("Bad").error != null)
            val best = LabResult.best(results)!!
            assertEquals("TLS record split", best.strategy!!.name)
            assertTrue(best.confirmed, best.toString())
        }
    }
}

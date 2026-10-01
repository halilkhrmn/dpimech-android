package io.github.halilkhrmn.dpimech.core

import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * The Android data path on Linux: an app's packets enter a TUN device, hev-socks5-tunnel
 * (configured by [TunnelConfig]) hands them to ciadpi (arguments from [ByeDpiCommand]), and
 * ciadpi opens the real connection. On Android the app is kept out of the VPN by package; here
 * the "app" runs as uid 1000 and only that uid is routed into the TUN.
 *
 * Needs root, iproute2, IPv6 in the kernel, `$CIADPI` and `$HEV` (see native/build-host.sh);
 * skipped otherwise. CI runs it.
 */
class TunnelEndToEndTest {
    private val ciadpi = System.getenv("CIADPI")
    private val hev = System.getenv("HEV")
    private val tun = "dpitest0"
    private val target = "10.77.0.2"
    private val table = "177"

    @Test
    fun tcpAndUdpOfTheAppGoThroughCiadpi() {
        assumeTrue(ciadpi != null && hev != null, "set CIADPI and HEV to run")
        assumeTrue(run("id", "-u").trim() == "0", "needs root")
        // hev-socks5-tunnel always opens AF_INET6 sockets (dual-stack), like on Android.
        assumeTrue(File("/proc/sys/net/ipv6").exists(), "needs a kernel with IPv6")

        val dir = Files.createTempDirectory("e2e").toFile()
        val socksPort = ServerSocket(0).use { it.localPort }
        val procs = mutableListOf<Process>()
        val undo = ArrayDeque<List<String>>()
        fun sh(vararg cmd: String, undoCmd: List<String>? = null) {
            run(*cmd)
            undoCmd?.let { undo.addFirst(it) }
        }
        try {
            sh("ip", "addr", "add", "$target/32", "dev", "lo", undoCmd = listOf("ip", "addr", "del", "$target/32", "dev", "lo"))
            val tcp = ServerSocket(0, 10, InetAddress.getByName(target))
            val udp = DatagramSocket(InetSocketAddress(target, 0))
            thread(isDaemon = true) { tcpServer(tcp) }
            thread(isDaemon = true) { udpServer(udp) }

            val args = ByeDpiCommand.build(ByeDpiCommand.Request("-x 2 -s1", socksPort, dir)).getOrThrow()
            val ciadpiLog = File(dir, "ciadpi.log")
            procs += ProcessBuilder(listOf(ciadpi!!) + args).redirectErrorStream(true).redirectOutput(ciadpiLog).start()

            // On Android the VPN builder sets the addresses; a self-made TUN here gets IPv4 only
            // (containers often have IPv6 off).
            val yaml = TunnelConfig(socksPort).hevYaml()
                .replace("tunnel:\n", "tunnel:\n  name: $tun\n")
                .lines().filterNot { it.startsWith("  ipv6:") }.joinToString("\n")
                .replace("misc:\n", "misc:\n  log-file: stderr\n")
            val conf = File(dir, "hev.yml").apply { writeText(yaml) }
            procs += ProcessBuilder(hev!!, conf.path).redirectErrorStream(true)
                .redirectOutput(File(dir, "hev.log")).start()
            waitFor { File("/sys/class/net/$tun").exists() }
            sh("ip", "link", "set", tun, "up")
            // Replies come back through the TUN from the target, which is a local address here.
            File("/proc/sys/net/ipv4/conf/$tun/accept_local").writeText("1")

            // Only the test "app" (uid 1000) is routed into the TUN. The local table has to come
            // after that rule, or the target address (on lo) would never reach the TUN.
            sh("ip", "route", "add", "$target/32", "dev", tun, "table", table)
            sh("ip", "rule", "add", "pref", "100", "uidrange", "1000-1000", "lookup", table,
                undoCmd = listOf("ip", "rule", "del", "pref", "100"))
            sh("ip", "rule", "add", "pref", "101", "lookup", "local",
                undoCmd = listOf("ip", "rule", "del", "pref", "101"))
            sh("ip", "rule", "del", "pref", "0", undoCmd = listOf("ip", "rule", "add", "pref", "0", "lookup", "local"))

            val out = run(
                "setpriv", "--reuid=1000", "--regid=1000", "--clear-groups", "python3", "-c",
                CLIENT, target, tcp.localPort.toString(), udp.localPort.toString(),
            )
            assertTrue("tcp=pong:hello" in out && "udp=echo:ping" in out, out)
            val log = ciadpiLog.readText()
            assertTrue("addr=$target:${tcp.localPort}" in log, "TCP went through ciadpi:\n$log")
            assertTrue("split: " in log, "ciadpi applied the strategy:\n$log")
        } catch (e: Throwable) {
            val logs = dir.listFiles { f -> f.name.endsWith(".log") }.orEmpty()
                .joinToString("\n") { "--- ${it.name}\n${it.readText().takeLast(3000)}" }
            throw AssertionError("${e.message}\n$logs", e)
        } finally {
            for (cmd in undo) run(*cmd.toTypedArray(), check = false)
            for (p in procs) {
                p.destroy()
                p.waitFor(5, TimeUnit.SECONDS)
            }
        }
    }

    private fun tcpServer(server: ServerSocket) {
        while (true) {
            val s = server.accept()
            thread(isDaemon = true) {
                s.use {
                    val buf = ByteArray(256)
                    var n = 0
                    // The strategy splits the first packet; read until the whole word is in.
                    while (n < 5) n += it.getInputStream().read(buf, n, buf.size - n).takeIf { r -> r > 0 } ?: break
                    it.getOutputStream().write("pong:${String(buf, 0, n)}".toByteArray())
                }
            }
        }
    }

    private fun udpServer(socket: DatagramSocket) {
        val buf = ByteArray(1500)
        while (true) {
            val p = DatagramPacket(buf, buf.size)
            socket.receive(p)
            val reply = "echo:${String(p.data, 0, p.length)}".toByteArray()
            socket.send(DatagramPacket(reply, reply.size, p.socketAddress))
        }
    }

    private fun waitFor(cond: () -> Boolean) {
        repeat(50) {
            if (cond()) return
            Thread.sleep(100)
        }
        error("timed out")
    }

    private fun run(vararg cmd: String, check: Boolean = true): String {
        val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor(30, TimeUnit.SECONDS)
        if (check && p.exitValue() != 0) error("${cmd.joinToString(" ")} failed: $out")
        return out
    }

    private companion object {
        val CLIENT = """
            import socket, sys
            host, tport, uport = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
            s = socket.create_connection((host, tport), timeout=5)
            s.sendall(b"hello")
            print("tcp=" + s.recv(100).decode())
            u = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            u.settimeout(5)
            u.sendto(b"ping", (host, uport))
            print("udp=" + u.recvfrom(100)[0].decode())
        """.trimIndent()
    }
}

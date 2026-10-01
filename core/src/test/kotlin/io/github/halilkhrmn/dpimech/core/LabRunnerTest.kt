package io.github.halilkhrmn.dpimech.core

import java.nio.file.Files
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LabRunnerTest {
    private fun s(name: String, args: String = "-s1") = LabStrategy(name, args, LabResult.STANDARD_SET)

    private fun result(name: String, ok: Int, total: Int, ms: Int) = LabResult(s(name, "--$name"), ok, total, ms)

    @Test
    fun luckyQuickRoundIsNotConfirmed() {
        val flaky = LabRunner.mergeConfirmation(result("a", 8, 8, 100), result("a", 10, 24, 300))
        assertFalse(flaky.confirmed)
        assertEquals(18 to 32, flaky.ok to flaky.total)
        // One miss in the quick round is already one too many.
        assertFalse(LabRunner.mergeConfirmation(result("b", 7, 8, 200), result("b", 24, 24, 200)).confirmed)
        val solid = LabRunner.mergeConfirmation(result("c", 8, 8, 400), result("c", 24, 24, 400))
        assertTrue(solid.confirmed)
        assertTrue(solid > flaky)
        assertEquals(400, solid.avgMs)
    }

    @Test
    fun candidatesSkipErrorsAndZeroResults() {
        val list = listOf(
            result("broken", 0, 8, 0).copy(error = "did not start"),
            result("none", 0, 8, 0),
            result("half", 4, 8, 50),
            result("all", 8, 8, 500),
        )
        assertEquals(listOf("all", "half"), LabRunner.confirmCandidates(list).map { it.strategy!!.name })
    }

    /** Fake engines and sites: a strategy "works" when its args contain the host's marker. */
    @Test
    fun runsBaselineStrategiesAndConfirmation() {
        val started = Collections.synchronizedList(mutableListOf<List<String>>())
        val runner = LabRunner(
            launcher = { args, _ ->
                if ("-x" in args) throw IllegalStateException("bad engine")
                started += args
                AutoCloseable { }
            },
            listsDir = Files.createTempDirectory("lab").toFile(),
            parallel = 2,
            opens = { via, host, _ -> via != Connector.DIRECT || host == "open.test" },
        )
        val events = Collections.synchronizedList(mutableListOf<Pair<Int, LabResult>>())
        val results = runner.run(
            LabRequest(
                strategies = listOf(s("works"), s("broken", "-x 1"), s("invalid", "--port 1")),
                probes = listOf("open.test", "blocked.test"),
            ),
            listener = { done, total, r -> assertEquals(1 + 3 + 3, total); events += done to r },
        )
        val baseline = results.first()
        assertNull(baseline.strategy)
        assertEquals(listOf("blocked.test"), baseline.failedDomains)
        val byName = results.drop(1).associateBy { it.strategy!!.name }
        assertTrue(byName.getValue("works").confirmed)
        assertEquals(2 * 2 + 2 * LabRunner.CONFIRM_ROUNDS, byName.getValue("works").total)
        assertEquals("bad engine", byName.getValue("broken").error)
        assertTrue(byName.getValue("invalid").error!!.contains("--port"))
        assertEquals("works", LabResult.best(results)?.strategy?.name)
        // Baseline + 3 quick results + 1 confirmation, numbered in order.
        assertEquals(listOf(1, 2, 3, 4, 5), events.map { it.first }.sorted())
        assertTrue(started.all { it.take(4) == listOf("-i", "127.0.0.1", "-p", it[3]) })
    }

    @Test
    fun cancelStopsBeforeStrategies() {
        val runner = LabRunner(
            launcher = { _, _ -> error("must not start") },
            listsDir = Files.createTempDirectory("lab").toFile(),
            opens = { _, _, _ -> true },
        )
        val results = runner.run(LabRequest(listOf(s("a")), listOf("x.test")), { _, _, _ -> }, cancelled = { true })
        assertEquals(1, results.size)
    }
}

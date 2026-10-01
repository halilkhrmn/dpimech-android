package io.github.halilkhrmn.dpimech.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StrategyFileTest {
    @Test
    fun embeddedFileHasByeDpiStrategies() {
        val list = StrategyFile.embedded.byeDpi
        assertTrue(list.size >= 5)
        assertTrue(list.any { it.args.contains("{sni}") })
    }

    @Test
    fun everyEmbeddedStrategyBuilds() {
        val lists = File("/data/user/0/app/files/lists")
        for (s in StrategyFile.embedded.byeDpi) {
            for (filter in listOf(false, true)) {
                val r = ByeDpiCommand.build(
                    ByeDpiCommand.Request(s.args, 1080, lists, File(lists, "p.txt"), domainFilter = filter),
                )
                assertTrue(r.isSuccess, "${s.name}: ${r.exceptionOrNull()?.message}")
            }
        }
    }

    @Test
    fun rejectsBadFiles() {
        assertTrue(StrategyFile.parse("""{"format": 2, "engines": {"bye_dpi": [{"name": "a", "args": "-s1"}]}}""").isFailure)
        assertTrue(StrategyFile.parse("""{"format": 1, "engines": {}}""").isFailure)
        assertTrue(StrategyFile.parse("""{"format": 1, "engines": {"bye_dpi": [{"name": "a", "args": " "}]}}""").isFailure)
        assertTrue(StrategyFile.parse("""{"format": 1, "engines": {"zapret_tpws": [{"name": "a", "args": "-x"}]}}""").isFailure)
        assertTrue(StrategyFile.parse("<html>404</html>").isFailure)
        val long = "x".repeat(4001)
        assertTrue(StrategyFile.parse("""{"format": 1, "engines": {"bye_dpi": [{"name": "a", "args": "$long"}]}}""").isFailure)
    }

    @Test
    fun unknownEnginesAndKeysAreIgnored() {
        val file = StrategyFile.parse(
            """{"format": 1, "note": "x", "engines": {"future": [{"name": "a", "args": "-x"}],
               "bye_dpi": [{"name": "b", "args": "-s1", "extra": true}]}}""",
        ).getOrThrow()
        assertEquals(listOf(StrategyEntry("b", "-s1")), file.byeDpi)
    }

    @Test
    fun communityListParses() {
        val text = "# comment\n\n-s1 -q1\n  -d1 -r1+s  \n"
        assertEquals(
            listOf(StrategyEntry("#3", "-s1 -q1"), StrategyEntry("#4", "-d1 -r1+s")),
            OnlineSource.parseArgsPerLine(text),
        )
    }
}

package io.github.halilkhrmn.dpimech.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class ArgPolicyTest {
    private val lists = File("/data/user/0/app/files/lists")

    private fun ok(s: String) = assertTrue(ArgPolicy.check(splitArgs(s), listOf(lists)).isSuccess, s)
    private fun bad(s: String) = assertTrue(ArgPolicy.check(splitArgs(s), listOf(lists)).isFailure, s)

    @Test
    fun allowsNormalStrategies() {
        ok("-r 1+s")
        ok("-o1 -a1 -An -Ku -a1 -An -s1 -d3+s -At,r,s -s1 -o2")
        ok("--split 1+s --disorder=3 -NU -c 2048")
        ok("--tlsr 1+s") // unique long prefix
        ok("-H :discord.com -l :GET")
        ok("-H $lists/hosts.txt")
        ok("-y -")
        ok("")
    }

    @Test
    fun rejectsDangerousOrManagedOptions() {
        bad("-y /sdcard/x")
        bad("--cache-d=/sdcard/x") // prefix of --cache-dump
        bad("-NUy/sdcard/x") // clustered
        bad("-l /etc/passwd")
        bad("-H $lists/../../secret.txt")
        bad("-H $lists")
        bad("-H relative.txt")
        bad("-i 0.0.0.0")
        bad("--port 9999")
        bad("--protect-path /tmp/x")
        bad("--transparent")
        bad("--connect-to 1.2.3.4")
        bad("--daemon")
        bad("stray")
        bad("-r") // missing value
        bad("--no-udp=1")
        bad("--d 1") // ambiguous: debug, disorder, disoob, def-ttl, drop-sack
    }
}

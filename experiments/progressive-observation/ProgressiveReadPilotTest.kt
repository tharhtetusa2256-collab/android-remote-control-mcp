package com.danielealbano.androidremotecontrolmcp.privacy

/** Standalone synthetic JVM checks; no Android or model dependency. */
fun main() {
    var passed = 0
    fun test(name: String, block: () -> Unit) {
        block()
        passed++
        println("PASS $name")
    }
    test("ordered bounded batches") {
        val p = ProgressiveReadPilot("s1", listOf(100, 100, 100, 100, 54))
        check(p.next("s1") == 0..1)
        check(p.acknowledge("s1", 0..1, true))
        check(!p.allVerified("s1"))
        check(p.next("s1") == 2..3)
        check(p.acknowledge("s1", 2..3, true))
        check(p.next("s1") == 4..4)
        check(p.acknowledge("s1", 4..4, true))
        check(p.allVerified("s1") && p.next("s1") == null)
    }
    test("exact budget includes separator") {
        val p = ProgressiveReadPilot("s1", listOf(126, 127, 1))
        check(p.next("s1") == 0..1)
    }
    test("oversized field rejected whole") {
        check(runCatching { ProgressiveReadPilot("s1", listOf(255)) }.isFailure)
    }
    test("invalid costs rejected") {
        check(runCatching { ProgressiveReadPilot("s1", listOf(0)) }.isFailure)
        check(runCatching { ProgressiveReadPilot("s1", emptyList()) }.isFailure)
        check(runCatching { ProgressiveReadPilot("s1", List(1001) { 1 }) }.isFailure)
    }
    test("out of order receipt latches stop") {
        val p = ProgressiveReadPilot("s1", listOf(200, 200))
        check(!p.acknowledge("s1", 1..1, true))
        check(p.next("s1") == null && !p.allVerified("s1"))
    }
    test("failed receipt cannot resume") {
        val p = ProgressiveReadPilot("s1", listOf(200))
        check(!p.acknowledge("s1", 0..0, false))
        check(!p.acknowledge("s1", 0..0, true))
    }
    test("snapshot change latches stop") {
        val p = ProgressiveReadPilot("s1", listOf(200))
        check(p.next("s2") == null)
        check(p.next("s1") == null && !p.allVerified("s1"))
    }
    test("duplicate receipt rejected") {
        val p = ProgressiveReadPilot("s1", listOf(200, 200))
        check(p.acknowledge("s1", 0..0, true))
        check(!p.acknowledge("s1", 0..0, true))
        check(!p.allVerified("s1"))
    }
    test("cancellation prevents further batches") {
        val p = ProgressiveReadPilot("s1", listOf(200, 200))
        check(p.acknowledge("s1", 0..0, true))
        p.cancel()
        check(p.next("s1") == null && !p.allVerified("s1"))
    }
    test("completed snapshot still requires current identity") {
        val p = ProgressiveReadPilot("s1", listOf(200))
        check(p.acknowledge("s1", 0..0, true))
        check(p.allVerified("s1"))
        check(!p.allVerified("s2") && !p.allVerified("s1"))
    }
    println("$passed/10 PASSED")
}

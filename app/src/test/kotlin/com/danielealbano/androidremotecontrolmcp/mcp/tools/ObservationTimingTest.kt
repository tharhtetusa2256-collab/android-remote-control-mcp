package com.danielealbano.androidremotecontrolmcp.mcp.tools

import com.danielealbano.androidremotecontrolmcp.BuildConfig
import com.danielealbano.androidremotecontrolmcp.data.model.ServerLogEntry
import com.danielealbano.androidremotecontrolmcp.testutil.RecordingServerLogRepository
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

class ObservationTimingTest {
    @Test
    fun `repeated stages are accumulated without logging payloads`() {
        var now = 0L
        val timing = ObservationTiming(ObservationKind.SCREEN) { now }
        val payload = "private screen payload"
        assertEquals(payload, timing.stage(ObservationStage.TREE_TRAVERSAL) {
            now += 12
            payload
        })
        timing.stage(ObservationStage.TREE_TRAVERSAL) { now += 8 }
        now += 5
        val summary = timing.summary(ObservationOutcome.RETURNED)
        assertEquals("kind=SCREEN outcome=RETURNED total_ns=25 TREE_TRAVERSAL_ns=20 TREE_TRAVERSAL_calls=2", summary)
        assertFalse(summary.contains(payload))
    }

    @Test
    fun `failed stage preserves the original exception and duration`() {
        var now = 0L
        val timing = ObservationTiming(ObservationKind.FIND) { now }
        val original = IllegalStateException("private error")
        val actual = assertThrows(IllegalStateException::class.java) {
            timing.stage(ObservationStage.PRIVACY) {
                now += 17
                throw original
            }
        }
        assertSame(original, actual)
        val summary = timing.summary(ObservationOutcome.FAILED)
        assertTrue(summary.contains("PRIVACY_ns=17 PRIVACY_calls=1"))
        assertFalse(summary.contains(original.message!!))
    }

    @Test
    fun `disabled timing executes exactly once and returns unchanged result`() {
        val timing: ObservationTiming? = null
        var count = 0
        assertEquals(42, timing.stage(ObservationStage.FORMAT) {
            count++
            42
        })
        assertEquals(1, count)
    }

    @Test
    fun `unreached stages are absent from cancelled summary`() {
        var now = 0L
        val timing = ObservationTiming(ObservationKind.SCREEN) { now }
        timing.stage(ObservationStage.LOCK_WAIT) { now += 9 }
        assertEquals(
            "kind=SCREEN outcome=CANCELLED total_ns=9 LOCK_WAIT_ns=9 LOCK_WAIT_calls=1",
            timing.summary(ObservationOutcome.CANCELLED),
        )
    }

    @Test
    fun `compact summary is below server log byte cap with all stages`() {
        var now = 0L
        val timing = ObservationTiming(ObservationKind.SCREEN) { now }
        for (stage in ObservationStage.entries) {
            timing.stage(stage) { now += 100_000_000_000_000_000L }
        }
        val summary = timing.inAppSummary(ObservationOutcome.CANCELLED)
        assertTrue(summary.toByteArray(Charsets.UTF_8).size <= 500)
        for (stage in ObservationStage.entries) {
            assertTrue(summary.contains("${stage.name}=100000000000"))
        }
        assertTrue(summary.contains("total_ms=1300000000000"))
    }

    @Test
    fun `debug profile writes only metadata to existing local repository`() =
        runTest {
            assumeTrue(BuildConfig.DEBUG)
            val repository = RecordingServerLogRepository()
            val privatePayload = "private synthetic payload"
            val value =
                withContext(ObservationLogSink(repository)) {
                    profileObservation(ObservationKind.SCREEN) { timing ->
                        timing.stage(ObservationStage.PRIVACY) { privatePayload }
                    }
                }
            assertEquals(privatePayload, value)
            val entry = repository.ofType(ServerLogEntry.Type.SERVER).single()
            assertTrue(entry.message.startsWith("Observation v1 SCREEN RETURNED total_ms="))
            assertTrue(entry.message.contains("PRIVACY="))
            assertFalse(entry.message.contains(privatePayload))
            assertTrue(entry.message.toByteArray(Charsets.UTF_8).size <= 500)
        }
}

package com.danielealbano.androidremotecontrolmcp.mcp.tools

import android.util.Log
import com.danielealbano.androidremotecontrolmcp.BuildConfig
import kotlinx.coroutines.CancellationException

/** Debug-only, request-local timings. No UI data, identifiers, parameters or exception messages. */
internal enum class ObservationKind { SCREEN, FIND }

internal enum class ObservationStage {
    LOCK_WAIT,
    CACHE_CLEAR,
    WINDOW_COLLECTION,
    ROOT_REFRESH,
    TREE_TRAVERSAL,
    NODE_CACHE,
    WEBVIEW_MERGE,
    PRIVACY,
    SCREEN_INFO,
    FORMAT,
    MATCH,
    RESPONSE,
    SCREENSHOT,
}

internal enum class ObservationOutcome { RETURNED, FAILED, CANCELLED }

internal class ObservationTiming(
    private val kind: ObservationKind,
    private val clock: () -> Long = System::nanoTime,
) {
    private val started = clock()
    private val durations = LongArray(ObservationStage.entries.size)
    private val counts = IntArray(ObservationStage.entries.size)

    fun now(): Long = clock()

    fun recordSince(stage: ObservationStage, start: Long?) {
        if (start == null) return
        val index = stage.ordinal
        durations[index] += (clock() - start).coerceAtLeast(0L)
        counts[index]++
    }

    fun summary(outcome: ObservationOutcome): String = buildString {
        append("kind=").append(kind.name)
        append(" outcome=").append(outcome.name)
        append(" total_ns=").append((clock() - started).coerceAtLeast(0L))
        for (stage in ObservationStage.entries) {
            if (counts[stage.ordinal] == 0) continue
            append(' ').append(stage.name).append("_ns=").append(durations[stage.ordinal])
            append(' ').append(stage.name).append("_calls=").append(counts[stage.ordinal])
        }
    }
}

internal inline fun <T> ObservationTiming?.stage(stage: ObservationStage, block: () -> T): T {
    val started = this?.now()
    try {
        return block()
    } finally {
        this?.recordSince(stage, started)
    }
}

/** Observe the existing execution without changing its result or catching its failures. */
internal suspend fun <T> profileObservation(
    kind: ObservationKind,
    block: suspend (ObservationTiming?) -> T,
): T {
    if (!BuildConfig.DEBUG) return block(null)
    val timing = ObservationTiming(kind)
    var outcome = ObservationOutcome.FAILED
    try {
        val result = block(timing)
        outcome = ObservationOutcome.RETURNED
        return result
    } catch (error: CancellationException) {
        outcome = ObservationOutcome.CANCELLED
        throw error
    } finally {
        // Diagnostic logging must never replace the original result or exception.
        runCatching { Log.d("MCP:ObservationTiming", timing.summary(outcome)) }
    }
}

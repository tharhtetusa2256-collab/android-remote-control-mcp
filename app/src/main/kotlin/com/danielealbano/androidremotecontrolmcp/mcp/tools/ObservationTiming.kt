package com.danielealbano.androidremotecontrolmcp.mcp.tools

import android.util.Log
import com.danielealbano.androidremotecontrolmcp.BuildConfig
import com.danielealbano.androidremotecontrolmcp.data.model.ServerLogEntry
import com.danielealbano.androidremotecontrolmcp.data.repository.ServerLogRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

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

private const val NANOS_PER_MILLISECOND = 1_000_000L

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

    // The existing server log store caps an entry at 500 UTF-8 bytes. Milliseconds and fixed
    // enum names keep even a summary with all 13 stages below that limit.
    fun inAppSummary(outcome: ObservationOutcome): String =
        buildString {
            append("Observation v1 ").append(kind.name)
            append(' ').append(outcome.name)
            append(" total_ms=").append((clock() - started).coerceAtLeast(0L) / NANOS_PER_MILLISECOND)
            for (stage in ObservationStage.entries) {
                if (counts[stage.ordinal] == 0) continue
                append(' ').append(stage.name).append('=').append(durations[stage.ordinal] / NANOS_PER_MILLISECOND)
            }
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

/** Internal request-scoped sink. Only the existing local server log repository receives metadata. */
internal class ObservationLogSink(
    private val repository: ServerLogRepository,
) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<ObservationLogSink>

    fun record(timing: ObservationTiming, outcome: ObservationOutcome) {
        repository.log(
            type = ServerLogEntry.Type.SERVER,
            message = timing.inAppSummary(outcome),
        )
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
        runCatching { currentCoroutineContext()[ObservationLogSink]?.record(timing, outcome) }
    }
}

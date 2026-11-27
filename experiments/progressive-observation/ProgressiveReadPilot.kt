package com.danielealbano.androidremotecontrolmcp.privacy

/** Synthetic test fixture only. Costs are estimates, not tokenizer guarantees. No text or output. */
internal class ProgressiveReadPilot(
    private val snapshot: String,
    costs: List<Int>,
    budget: Int = 254,
) {
    init {
        require(snapshot.isNotBlank())
        require(budget in 1..254 && costs.size in 1..1000)
        require(costs.all { it in 1..budget })
    }

    private val batches = buildList {
        var first = 0
        var used = 0
        costs.forEachIndexed { index, cost ->
            val separator = if (used == 0) 0 else 1
            if (used + separator + cost > budget) {
                add(first until index)
                first = index
                used = 0
            }
            used += cost + if (used == 0) 0 else 1
        }
        add(first until costs.size)
    }
    private var position = 0
    private var stopped = false

    fun next(currentSnapshot: String): IntRange? {
        if (currentSnapshot != snapshot) stopped = true
        return if (stopped) null else batches.getOrNull(position)
    }

    /** A receipt is synthetic evidence, never proof of real privacy processing. */
    fun acknowledge(currentSnapshot: String, batch: IntRange, verified: Boolean): Boolean {
        if (stopped || currentSnapshot != snapshot || !verified || batch != batches.getOrNull(position)) {
            stopped = true
            return false
        }
        position++
        return true
    }

    fun cancel() {
        stopped = true
    }

    fun allVerified(currentSnapshot: String): Boolean {
        if (currentSnapshot != snapshot) stopped = true
        return !stopped && position == batches.size
    }
}

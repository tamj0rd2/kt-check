package com.tamj0rd2.ktcheck.core.shrinkers

fun IntRange.defaultShrinkTarget() = when {
    last < 0 -> last
    first > 0 -> first
    else -> 0
}

internal object IntShrinker : Shrinker<Int, IntRange> {
    override fun defaultShrinkTarget(range: IntRange): Int = range.defaultShrinkTarget()

    override fun shrink(
        value: Int,
        range: IntRange,
        target: Int,
    ): Sequence<Int> = sequence {
        require(target in range) { "Origin $target must be within range $range" }

        if (value == target) return@sequence
        yield(target)

        // yield progressively closer values by repeatedly halving the original distance
        val originalDistance = value.toLong() - target.toLong()
        var divisor = 2L
        while (true) {
            val shrinkAmount = originalDistance / divisor
            if (shrinkAmount == 0L) break

            val candidate = (value.toLong() - shrinkAmount).toInt()
            if (candidate in range && candidate != target) {
                yield(candidate)
            }
            divisor *= 2
        }
    }
}

package com.tamj0rd2.ktcheck.gen

import kotlin.math.abs

private data class DoubleGenerator(
    private val range: ClosedFloatingPointRange<Double>,
) : Gen<Double>() {
    override fun GenContext.generate(): GenResult<Double> {
        val value = tree.producer.double(range)
        return GenResult(
            value = value,
            shrinks = shrink(value, range).map { tree.withValue(it) }
        )
    }
}

internal fun shrink(value: Double, range: ClosedFloatingPointRange<Double>) = shrink(
    value = value,
    range = range,
    origin = when {
        range.endInclusive < 0.0 -> range.endInclusive
        range.start > 0.0 -> range.start
        else -> 0.0
    }
)

internal fun shrink(
    value: Double,
    range: ClosedFloatingPointRange<Double>,
    origin: Double,
): Sequence<Double> = sequence {
    // Don't shrink non-finite values (NaN, Infinity, -Infinity)
    if (!value.isFinite()) return@sequence

    require(origin.isFinite()) { "Origin must be finite: $origin" }
    require(origin in range) { "Origin $origin must be within range $range" }

    if (value == origin) return@sequence

    // Always yield the origin first
    yield(origin)

    // Try to shrink to the whole number part (simpler counterexample)
    // Handle toLong() overflow for very large doubles
    val wholePart = if (value in Long.MIN_VALUE.toDouble()..Long.MAX_VALUE.toDouble()) {
        value.toLong().toDouble()
    } else {
        null
    }
    if (wholePart != null && wholePart != value && wholePart in range && wholePart != origin) {
        yield(wholePart)
    }

    // Then yield progressively closer values by repeatedly halving the original distance
    val originalDistance = value - origin
    var divisor = 2.0

    // Calculate epsilon based on range size
    val rangeSize = range.endInclusive - range.start
    val epsilon = when {
        rangeSize.isInfinite() -> 1e-6
        rangeSize == 0.0 -> return@sequence
        else -> rangeSize * 1e-10
    }.coerceAtLeast(1e-10)

    while (abs(originalDistance / divisor) > epsilon) {
        val shrinkAmount = originalDistance / divisor
        val candidate = value - shrinkAmount

        if (candidate.isFinite() && candidate in range && candidate != origin && candidate != value) {
            yield(candidate)
        }
        divisor *= 2.0

        // Safety check to prevent infinite loops
        if (divisor > 1e15) break
    }
}

fun Gen.Companion.double(
    range: ClosedFloatingPointRange<Double> = -Double.MAX_VALUE..Double.MAX_VALUE,
): Gen<Double> = DoubleGenerator(range)


package com.tamj0rd2.ktcheck.gen

import kotlin.math.abs

private data class FloatGenerator(
    private val range: ClosedFloatingPointRange<Float>,
) : Gen<Float>() {
    override fun GenContext.generate(): GenResult<Float> {
        val value = tree.producer.float(range)
        return GenResult(
            value = value,
            shrinks = shrink(value, range).map { tree.withValue(it) }
        )
    }
}

internal fun shrink(value: Float, range: ClosedFloatingPointRange<Float>) = shrink(
    value = value,
    range = range,
    origin = when {
        range.endInclusive < 0.0f -> range.endInclusive
        range.start > 0.0f -> range.start
        else -> 0.0f
    }
)

internal fun shrink(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    origin: Float,
): Sequence<Float> = sequence {
    // Don't shrink non-finite values (NaN, Infinity, -Infinity)
    if (!value.isFinite()) return@sequence

    require(origin.isFinite()) { "Origin must be finite: $origin" }
    require(origin in range) { "Origin $origin must be within range $range" }

    if (value == origin) return@sequence

    // Always yield the origin first
    yield(origin)

    // Try to shrink to the whole number part (simpler counterexample)
    // Handle toInt() overflow for very large floats
    val wholePart = if (value in Int.MIN_VALUE.toFloat()..Int.MAX_VALUE.toFloat()) {
        value.toInt().toFloat()
    } else {
        null
    }
    if (wholePart != null && wholePart != value && wholePart in range && wholePart != origin) {
        yield(wholePart)
    }

    // Then yield progressively closer values by repeatedly halving the original distance
    val originalDistance = value - origin
    var divisor = 2.0f

    // Calculate epsilon based on range size
    val rangeSize = range.endInclusive - range.start
    val epsilon = when {
        rangeSize.isInfinite() -> 1e-6f
        rangeSize == 0.0f -> return@sequence
        else -> rangeSize * 1e-7f
    }.coerceAtLeast(1e-7f)

    while (abs(originalDistance / divisor) > epsilon) {
        val shrinkAmount = originalDistance / divisor
        val candidate = value - shrinkAmount

        if (candidate.isFinite() && candidate in range && candidate != origin && candidate != value) {
            yield(candidate)
        }
        divisor *= 2.0f

        // Safety check to prevent infinite loops
        if (divisor > 1e8f) break
    }
}

fun Gen.Companion.float(
    range: ClosedFloatingPointRange<Float> = -Float.MAX_VALUE..Float.MAX_VALUE,
): Gen<Float> = FloatGenerator(range)


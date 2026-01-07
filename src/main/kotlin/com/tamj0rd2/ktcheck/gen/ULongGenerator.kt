package com.tamj0rd2.ktcheck.gen

private data class ULongGenerator(
    private val range: ULongRange,
) : Gen<ULong>() {
    override fun GenContext.generate(): GenResult<ULong> {
        val value = tree.producer.uLong(range)
        return GenResult(
            value = value,
            shrinks = shrink(value, range).map { tree.withValue(it) }
        )
    }
}

internal fun shrink(value: ULong, range: ULongRange) = shrink(
    value = value,
    range = range,
    origin = when {
        0uL in range -> 0uL
        else -> range.first
    }
)

internal fun shrink(value: ULong, range: ULongRange, origin: ULong): Sequence<ULong> = sequence {
    require(origin in range) { "Origin $origin must be within range $range" }

    if (value == origin) return@sequence

    // Always yield the origin first
    yield(origin)

    // Then yield progressively closer values by repeatedly halving the original distance
    val originalDistance = value - origin
    var divisor = 2uL
    while (true) {
        val shrinkAmount = originalDistance / divisor
        if (shrinkAmount == 0uL) break

        val candidate = value - shrinkAmount
        if (candidate in range && candidate != origin) {
            yield(candidate)
        }
        divisor *= 2uL
    }
}

fun Gen.Companion.uLong(range: ULongRange = ULong.MIN_VALUE..ULong.MAX_VALUE): Gen<ULong> = ULongGenerator(range)


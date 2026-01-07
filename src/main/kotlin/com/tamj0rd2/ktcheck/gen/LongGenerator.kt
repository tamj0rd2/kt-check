package com.tamj0rd2.ktcheck.gen

private data class LongGenerator(
    private val range: LongRange,
) : Gen<Long>() {
    override fun GenContext.generate(): GenResult<Long> {
        val value = tree.producer.long(range)
        return GenResult(
            value = value,
            shrinks = shrink(value, range).map { tree.withValue(it) }
        )
    }
}

internal fun shrink(value: Long, range: LongRange) = shrink(
    value = value,
    range = range,
    origin = when {
        range.last < 0 -> range.last
        range.first > 0 -> range.first
        else -> 0L
    }
)

internal fun shrink(value: Long, range: LongRange, origin: Long): Sequence<Long> = sequence {
    require(origin in range) { "Origin $origin must be within range $range" }

    if (value == origin) return@sequence

    // Always yield the origin first
    yield(origin)

    // Then yield progressively closer values by repeatedly halving the original distance
    val originalDistance = value - origin
    var divisor = 2L
    while (true) {
        val shrinkAmount = originalDistance / divisor
        if (shrinkAmount == 0L) break

        val candidate = value - shrinkAmount
        if (candidate in range && candidate != origin) {
            yield(candidate)
        }
        divisor *= 2
    }
}

fun Gen.Companion.long(range: LongRange = Long.MIN_VALUE..Long.MAX_VALUE): Gen<Long> = LongGenerator(range)


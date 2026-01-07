package com.tamj0rd2.ktcheck.gen

private data class UIntGenerator(
    private val range: UIntRange,
) : Gen<UInt>() {
    override fun GenContext.generate(): GenResult<UInt> {
        val value = tree.producer.uInt(range)
        return GenResult(
            value = value,
            shrinks = shrink(value, range).map { tree.withValue(it) }
        )
    }
}

internal fun shrink(value: UInt, range: UIntRange) = shrink(
    value = value,
    range = range,
    origin = when {
        0u in range -> 0u
        else -> range.first
    }
)

internal fun shrink(value: UInt, range: UIntRange, origin: UInt): Sequence<UInt> = sequence {
    require(origin in range) { "Origin $origin must be within range $range" }

    if (value == origin) return@sequence

    // Always yield the origin first
    yield(origin)

    // Then yield progressively closer values by repeatedly halving the original distance
    val originalDistance = value - origin
    var divisor = 2u
    while (true) {
        val shrinkAmount = originalDistance / divisor
        if (shrinkAmount == 0u) break

        val candidate = value - shrinkAmount
        if (candidate in range && candidate != origin) {
            yield(candidate)
        }
        divisor *= 2u
    }
}

fun Gen.Companion.uInt(range: UIntRange = UInt.MIN_VALUE..UInt.MAX_VALUE): Gen<UInt> = UIntGenerator(range)


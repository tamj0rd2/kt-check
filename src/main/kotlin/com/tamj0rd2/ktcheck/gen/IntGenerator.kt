package com.tamj0rd2.ktcheck.gen

import com.tamj0rd2.ktcheck.producer.ProducerTree
import com.tamj0rd2.ktcheck.producer.Seed

private data class IntGenerator(
    private val range: IntRange,
) : Gen<Int>() {
    override fun GenContext.generate(): GenResult<Int> {
        val value = tree.producer.int(range)
        return GenResult(
            value = value,
            shrinks = shrink(value, range).map { tree.withValue(it) }
        )
    }
}

internal fun shrink(value: Int, range: IntRange) = shrink(
    value = value,
    range = range,
    origin = when {
        range.last < 0 -> range.last
        range.first > 0 -> range.first
        else -> 0
    }
)

internal fun shrink(value: Int, range: IntRange, origin: Int): Sequence<Int> = sequence {
    require(origin in range) { "Origin $origin must be within range $range" }

    if (value == origin) return@sequence

    // Always yield the origin first
    yield(origin)

    // Then yield progressively closer values by repeatedly halving the original distance
    val originalDistance = value - origin
    var divisor = 2
    while (true) {
        val shrinkAmount = originalDistance / divisor
        if (shrinkAmount == 0) break

        val candidate = value - shrinkAmount
        if (candidate in range && candidate != origin) {
            yield(candidate)
        }
        divisor *= 2
    }
}

fun Gen.Companion.int(range: IntRange = Int.MIN_VALUE..Int.MAX_VALUE): Gen<Int> = IntGenerator(range)
fun Gen.Companion.long(range: IntRange = Int.MIN_VALUE..Int.MAX_VALUE): Gen<Long> = IntGenerator(range).map { it.toLong() }

// todo: move this to a better location
internal fun Gen.Companion.tree() = Gen.int().map { ProducerTree.new(Seed(it.toLong())) }

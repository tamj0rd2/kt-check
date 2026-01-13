package com.tamj0rd2.ktcheck.v2

import com.tamj0rd2.ktcheck.core.ProducerTree

internal data class IntGeneratorV2(
    internal val range: IntRange,
) : GenV2<Int>() {
    override fun generate(tree: ProducerTree): GenResultV2<Int> {
        val value = tree.producer.int(range)
        return GenResultV2(
            value = value,
            shrinks = generateShrinks(value),
        )
    }

    private fun generateShrinks(value: Int): Sequence<GenResultV2<Int>> = sequence {
        shrinkInt(value, range).forEach { shrunkValue ->
            yield(GenResultV2(shrunkValue, generateShrinks(shrunkValue)))
        }
    }

    companion object {
        private fun IntRange.defaultOrigin() = when {
            last < 0 -> last
            first > 0 -> first
            else -> 0
        }

        fun shrinkInt(value: Int, range: IntRange, origin: Int = range.defaultOrigin()): Sequence<Int> = sequence {
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
    }
}

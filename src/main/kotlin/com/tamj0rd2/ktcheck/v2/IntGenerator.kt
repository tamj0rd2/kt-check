package com.tamj0rd2.ktcheck.v2

@ConsistentCopyVisibility
data class IntGenerator private constructor(
    internal val range: IntRange,
    internal val origin: Int,
) : Gen<Int>() {
    init {
        require(origin in range) { "Origin $origin must be within range $range" }
    }

    override fun GenContext.generate(): GenResult<Int> {
        val value = producer.int(range)
        return GenResult(
            value = value,
            shrinks = generateShrinks(value),
        )
    }

    private fun generateShrinks(value: Int): Sequence<GenResult<Int>> = sequence {
        shrink(value, range, origin).forEach { shrunkValue ->
            yield(GenResult(shrunkValue, generateShrinks(shrunkValue)))
        }
    }

    companion object {
        fun Gen.Companion.int(range: IntRange = Int.MIN_VALUE..Int.MAX_VALUE, origin: Int = range.defaultOrigin()) =
            IntGenerator(range, origin)

        private fun IntRange.defaultOrigin() = when {
            last < 0 -> last
            first > 0 -> first
            else -> 0
        }

        fun shrink(value: Int, range: IntRange, origin: Int = range.defaultOrigin()): Sequence<Int> = sequence {
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

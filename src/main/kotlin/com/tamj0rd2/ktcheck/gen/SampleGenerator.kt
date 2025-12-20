package com.tamj0rd2.ktcheck.gen

private class SampleGenerator : Gen<Sample>() {
    override fun generate(tree: SampleTree): GenResult<Sample> = GenResult(
        value = tree.sample,
        shrinks = shrink(tree.sample.value).map { tree.withSample(Sample.Shrunk(it)) }
    )

    private fun shrink(value: ULong): Sequence<ULong> = sequence {
        if (value == 0UL) return@sequence
        yield(0uL)

        var current = value
        while (current > 0UL) {
            val diff = value - current
            if (diff > 0uL) yield(diff)
            current /= 2UL
        }
    }
}

internal fun Gen.Companion.sample(): Gen<Sample> = SampleGenerator()

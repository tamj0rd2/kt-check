package com.tamj0rd2.ktcheck.genv2

private data class IntGeneratorV2(
    private val range: IntRange,
) : Gen<Int>() {
    override fun generate(tree: ValueTree): GenResult<Int> {
        val value = tree.producer.int(range)

        // todo: isn't there a potential optimisation here - don't shrink if already shrunk? isn't that why I end up
        //  with millions of shrink candidates?
        val shrinks = shrink(value)
            .distinct()
            .filter { it in range }
            .map { tree.withValue(it) }

        return GenResult(value, shrinks)
    }
}

internal fun shrink(value: Int): Sequence<Int> = sequence {
    var current = value
    while (current != 0) {
        yield(value - current)
        current /= 2
    }
}

fun Gen.Companion.int(range: IntRange = Int.MIN_VALUE..Int.MAX_VALUE): Gen<Int> = IntGeneratorV2(range)

// todo: implement this property
internal fun Gen.Companion.long() = Gen.int().map { it.toLong() }

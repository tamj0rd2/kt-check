package com.tamj0rd2.ktcheck.genv2

private data class IntGeneratorV2(
    private val range: IntRange,
) : Gen<Int>() {
    override fun generate(tree: ChoiceTree): GenResult<Int> {
        val value = tree.int(range)
        val shrinks = shrink(value)
            .distinct()
            .filter { it in range }
            .map { tree.withChoice(it) }

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

fun GenV2.int(range: IntRange = Int.MIN_VALUE..Int.MAX_VALUE): Gen<Int> = IntGeneratorV2(range)

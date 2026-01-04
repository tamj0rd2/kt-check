package com.tamj0rd2.ktcheck.gen

import com.tamj0rd2.ktcheck.producer.ProducerTree
import com.tamj0rd2.ktcheck.producer.Seed

private data class IntGenerator(
    private val range: IntRange,
) : Gen<Int>() {
    override fun generate(tree: ProducerTree): GenResult<Int> {
        val value = tree.producer.int(range)
        return GenResult(
            value = value,
            shrinks = shrink(value, range).map { tree.withValue(it) }
        )
    }
}

// todo: generate directly within range rather than using filter
internal fun shrink(value: Int, range: IntRange): Sequence<Int> = sequence {
    var current = value
    while (current != 0) {
        yield(value - current)
        current /= 2
    }
}.filter { it in range }

fun Gen.Companion.int(range: IntRange = Int.MIN_VALUE..Int.MAX_VALUE): Gen<Int> = IntGenerator(range)

// todo: implement this properly
internal fun Gen.Companion.long() = Gen.int().map { it.toLong() }

// todo: move this to a better location
internal fun Gen.Companion.tree() = Gen.int().map { ProducerTree.new(Seed(it.toLong())) }

package com.tamj0rd2.ktcheck.genv2

fun interface Gen2<T> {
    fun generate(tree: SampleTree): Pair<T, Sequence<SampleTree>>

    fun <R> map(fn: (T) -> R): Gen2<R> = Gen2 { tree ->
        val (value, shrinks) = generate(tree)
        fn(value) to shrinks
    }

    fun <R> flatMap(fn: (T) -> Gen2<R>): Gen2<R> = Gen2 { tree ->
        val (leftValue, leftShrinks) = generate(tree.left)
        val (rightValue, rightShrinks) = fn(leftValue).generate(tree.right)
        rightValue to combineShrinks(tree, leftShrinks, rightShrinks)
    }

    companion object
}

fun interface Shrinker<T> {
    operator fun invoke(value: T): Sequence<T>
}

private val uLongShrinker = Shrinker<ULong> { value ->
    sequence {
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

internal fun Gen2.Companion.sample(): Gen2<Sample> = Gen2 { tree ->
    val shrinks = uLongShrinker(tree.sample.value).map { tree.withSample(Sample.Shrunk(it)) }
    tree.sample to shrinks
}

package com.tamj0rd2.ktcheck.genv2

import kotlin.random.Random

data class GenResult<T>(val value: T, val shrinks: Sequence<SampleTree>)

sealed class Gen<T> {
    internal abstract fun generate(tree: SampleTree): GenResult<T>

    fun sample(random: Random = Random.Default): T = samples(random).first()
    fun samples(random: Random = Random.Default) = generateSequence { sample(random.nextLong()) }
    fun sample(seed: Long): T = generate(SampleTree.from(seed)).value

    fun <R> map(fn: (T) -> R): Gen<R> = Gen { tree ->
        val (value, shrinks) = generate(tree)
        GenResult(fn(value), shrinks)
    }

    fun <R> flatMap(fn: (T) -> Gen<R>): Gen<R> = Gen { tree ->
        val (leftValue, leftShrinks) = generate(tree.left)
        val (rightValue, rightShrinks) = fn(leftValue).generate(tree.right)
        GenResult(rightValue, combineShrinks(tree, leftShrinks, rightShrinks))
    }

    fun <T2, R> combineWith(nextGen: Gen<T2>, combine: (T, T2) -> R): Gen<R> = Gen { tree ->
        val (thisValue, thisShrinks) = generate(tree.left)
        val (nextValue, nextShrinks) = nextGen.generate(tree.right)
        GenResult(
            value = combine(thisValue, nextValue),
            shrinks = combineShrinks(tree, thisShrinks, nextShrinks)
        )
    }

    companion object
}

private class InternalGenerator<T>(private val generator: (SampleTree) -> GenResult<T>) : Gen<T>() {
    override fun generate(tree: SampleTree): GenResult<T> = generator(tree)
}

internal operator fun <T> Gen.Companion.invoke(generator: (SampleTree) -> GenResult<T>): Gen<T> =
    InternalGenerator(generator)

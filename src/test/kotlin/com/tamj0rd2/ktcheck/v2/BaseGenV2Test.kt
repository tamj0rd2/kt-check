package com.tamj0rd2.ktcheck.v2

import com.tamj0rd2.ktcheck.Gen
import com.tamj0rd2.ktcheck.GenBuilder
import com.tamj0rd2.ktcheck.Seed
import com.tamj0rd2.ktcheck.contracts.BaseGeneratorContract
import com.tamj0rd2.ktcheck.contracts.RecursiveShrinkNavigator

internal abstract class BaseGenV2Test : BaseGeneratorContract, GenBuilder by GenV2.Companion {
    override fun <T : Any> Gen<T>.generateWithShrunkValues(rngValues: List<Any>): Pair<T, List<T>> =
        (this as GenV2<T>).generateWithShrunkValues(StubValueProducerV2(rngValues))

    override fun <T : Any> Gen<T>.generateWithShrunkValues(seed: Seed): Pair<T, List<T>> =
        (this as GenV2<T>).generateWithShrunkValues(RandomValueProducerV2(seed))

    override fun <T : Any> Gen<T>.navigateRecursiveShrinks(
        rngValues: List<Any>,
    ): RecursiveShrinkNavigator<T> {
        return (this as GenV2<T>).navigateRecursiveShrinks(StubValueProducerV2(rngValues))
    }

    private fun <T> GenV2<T>.navigateRecursiveShrinks(producer: ValueProducerV2): RecursiveShrinkNavigator<T> {
        val result = generate(producer)
        return V2RecursiveShrinkNavigator(result.value, result.shrinks)
    }

    companion object {
        internal fun <T> GenV2<T>.generateWithShrunkValues(seed: Seed = Seed.random()): Pair<T, List<T>> =
            generateWithShrunkValues(RandomValueProducerV2(seed))

        internal fun <T> GenV2<T>.generateWithShrunkValues(producer: ValueProducerV2): Pair<T, List<T>> {
            val (value, shrinks) = generate(producer)
            return value to shrinks.map { it.value }.toList()
        }
    }
}

private class V2RecursiveShrinkNavigator<T>(
    override val value: T,
    private val shrinkResults: Sequence<GenResultV2<T>>,
) : RecursiveShrinkNavigator<T> {

    override fun getShrinks(limit: Int): List<RecursiveShrinkNavigator<T>> {
        return shrinkResults.take(limit).map { result ->
            V2RecursiveShrinkNavigator(result.value, result.shrinks)
        }.toList()
    }

    override fun findShrinkByValue(value: T, limit: Int): RecursiveShrinkNavigator<T>? {
        return shrinkResults.take(limit).firstNotNullOfOrNull { result ->
            if (result.value == value) {
                V2RecursiveShrinkNavigator(result.value, result.shrinks)
            } else {
                null
            }
        }
    }
}

internal class StubValueProducerV2(
    values: List<Any>,
    private val delegate: ValueProducerV2? = null,
) : ValueProducerV2 {

    private val iterator = values.iterator()

    override fun int(range: IntRange): Int {
        if (!iterator.hasNext()) return delegate?.int(range) ?: error("StubProducer exhausted and no delegate provided")

        val value = iterator.next() as Int
        require(value in range) { "StubProducer produced value $value which is out of range $range" }
        return value
    }

    override fun bool(): Boolean {
        if (!iterator.hasNext()) return delegate?.bool() ?: error("StubProducer exhausted and no delegate provided")

        return iterator.next() as Boolean
    }
}

package com.tamj0rd2.ktcheck.v2

import com.tamj0rd2.ktcheck.contract.IGen
import com.tamj0rd2.ktcheck.contracts.BaseGeneratorContract
import com.tamj0rd2.ktcheck.contracts.RecursiveShrinkNavigator
import com.tamj0rd2.ktcheck.producer.Seed
import com.tamj0rd2.ktcheck.v2.BooleanGeneratorV2.Companion.bool
import com.tamj0rd2.ktcheck.v2.IntGeneratorV2.Companion.int

internal abstract class BaseGenV2Test : BaseGeneratorContract {
    override fun intGen(range: IntRange): GenV2<Int> {
        return GenV2.int(range)
    }

    override fun boolGen(): IGen<Boolean> {
        return GenV2.bool()
    }

    override fun <T> oneOfGen(vararg gens: IGen<T>): IGen<T> {
        return GenV2.oneOf(*gens.map { it as GenV2<T> }.toTypedArray())
    }

    override fun <T> oneOfGen(values: Collection<T>): IGen<T> {
        return GenV2.oneOf(values)
    }

    override fun <T> constantGen(value: T): IGen<T> {
        return GenV2.constant(value)
    }

    override fun <T> IGen<T>.listGen(): IGen<List<T>> {
        return (this as GenV2<T>).list()
    }

    override fun <T> IGen<T>.listGen(size: Int): IGen<List<T>> {
        return (this as GenV2<T>).list(size)
    }

    override fun <T> IGen<T>.listGen(sizeRange: IntRange): IGen<List<T>> {
        return (this as GenV2<T>).list(sizeRange)
    }

    override fun <T : Any> IGen<T>.generateWithShrunkValues(rngValues: List<Any>): Pair<T, List<T>> =
        (this as GenV2<T>).generateWithShrunkValues(StubValueProducerV2(rngValues))

    override fun <T : Any> IGen<T>.generateWithShrunkValues(seed: Seed): Pair<T, List<T>> =
        (this as GenV2<T>).generateWithShrunkValues(RandomValueProducerV2(seed))

    override fun <T : Any> IGen<T>.navigateRecursiveShrinks(
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

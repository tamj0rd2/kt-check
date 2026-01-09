package com.tamj0rd2.ktcheck.v2

import com.tamj0rd2.ktcheck.contract.IGen
import com.tamj0rd2.ktcheck.contracts.BaseGeneratorContract
import com.tamj0rd2.ktcheck.contracts.RecursiveShrinkNavigator
import com.tamj0rd2.ktcheck.producer.Seed
import com.tamj0rd2.ktcheck.v2.BooleanGenerator.Companion.bool
import com.tamj0rd2.ktcheck.v2.IntGenerator.Companion.int

internal abstract class BaseGenV2Test : BaseGeneratorContract {
    override fun intGen(range: IntRange): Gen<Int> {
        return Gen.int(range)
    }

    override fun boolGen(): IGen<Boolean> {
        return Gen.bool()
    }

    override fun <T> oneOfGen(vararg gens: IGen<T>): IGen<T> {
        return Gen.oneOf(*gens.map { it as Gen<T> }.toTypedArray())
    }

    override fun <T> oneOfGen(values: Collection<T>): IGen<T> {
        return Gen.oneOf(values)
    }

    override fun <T> constantGen(value: T): IGen<T> {
        return Gen.constant(value)
    }

    override fun <T> IGen<T>.listGen(): IGen<List<T>> {
        return (this as Gen<T>).list()
    }

    override fun <T> IGen<T>.listGen(size: Int): IGen<List<T>> {
        return (this as Gen<T>).list(size)
    }

    override fun <T> IGen<T>.listGen(sizeRange: IntRange): IGen<List<T>> {
        return (this as Gen<T>).list(sizeRange)
    }

    override fun <T : Any> IGen<T>.generateWithShrunkValues(rngValues: List<Any>): Pair<T, List<T>> =
        (this as Gen<T>).generateWithShrunkValues(StubValueProducer(rngValues))

    override fun <T : Any> IGen<T>.generateWithShrunkValues(seed: Seed): Pair<T, List<T>> =
        (this as Gen<T>).generateWithShrunkValues(RandomValueProducer(seed))

    override fun <T : Any> IGen<T>.navigateRecursiveShrinks(
        rngValues: List<Any>,
    ): RecursiveShrinkNavigator<T> {
        return (this as Gen<T>).navigateRecursiveShrinks(StubValueProducer(rngValues))
    }

    private fun <T> Gen<T>.navigateRecursiveShrinks(producer: ValueProducer): RecursiveShrinkNavigator<T> {
        val result = generate(producer)
        return V2RecursiveShrinkNavigator(result.value, result.shrinks)
    }

    companion object {
        internal fun <T> Gen<T>.generateWithShrunkValues(seed: Seed = Seed.random()): Pair<T, List<T>> =
            generateWithShrunkValues(RandomValueProducer(seed))

        internal fun <T> Gen<T>.generateWithShrunkValues(producer: ValueProducer): Pair<T, List<T>> {
            val (value, shrinks) = generate(producer)
            return value to shrinks.map { it.value }.toList()
        }
    }
}

private class V2RecursiveShrinkNavigator<T>(
    override val value: T,
    private val shrinkResults: Sequence<GenResult<T>>,
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

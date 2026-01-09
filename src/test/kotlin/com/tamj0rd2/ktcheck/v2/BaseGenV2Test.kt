package com.tamj0rd2.ktcheck.v2

import com.tamj0rd2.ktcheck.contract.IGen
import com.tamj0rd2.ktcheck.contracts.BaseGeneratorContract
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

    override fun <T : Any> IGen<T>.generateWithShrunkValues(rngValues: List<Any>): Pair<T, List<T>> =
        (this as Gen<T>).generateWithShrunkValues(StubValueProducer(rngValues))

    override fun <T : Any> IGen<T>.generateWithShrunkValues(seed: Seed): Pair<T, List<T>> =
        (this as Gen<T>).generateWithShrunkValues(RandomValueProducer(seed))

    companion object {
        internal fun <T> Gen<T>.generateWithShrunkValues(seed: Seed = Seed.random()): Pair<T, List<T>> =
            generateWithShrunkValues(RandomValueProducer(seed))

        internal fun <T> Gen<T>.generateWithShrunkValues(producer: ValueProducer): Pair<T, List<T>> {
            val (value, shrinks) = generate(producer)
            return value to shrinks.map { it.value }.toList()
        }
    }
}

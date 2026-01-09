package com.tamj0rd2.ktcheck.gen

import com.tamj0rd2.ktcheck.contract.IGen
import com.tamj0rd2.ktcheck.contracts.BaseGeneratorContract
import com.tamj0rd2.ktcheck.gen.GenTests.Companion.generateWithShrunkValues
import com.tamj0rd2.ktcheck.producer.ProducerTree
import com.tamj0rd2.ktcheck.producer.Seed

internal abstract class BaseGenTest : BaseGeneratorContract {
    override fun int(range: IntRange): Gen<Int> {
        return Gen.int(range)
    }

    override fun bool(): IGen<Boolean> {
        return Gen.bool()
    }

    override fun <T> oneOf(vararg gens: IGen<T>): IGen<T> {
        return Gen.oneOf(gens.map { it as Gen<T> })
    }

    override fun <T> oneOf(values: Collection<T>): IGen<T> {
        return Gen.oneOf(values)
    }

    override fun <T : Any> IGen<T>.generateWithShrunkValues(rngValues: List<Any>): Pair<T, List<T>> {
        return (this as Gen<T>).generateWithShrunkValues(ProducerTree.new().withValue(rngValues.single()))
    }

    override fun <T : Any> IGen<T>.generateWithShrunkValues(seed: Seed): Pair<T, List<T>> {
        return (this as Gen<T>).generateWithShrunkValues(ProducerTree.new(seed))
    }
}

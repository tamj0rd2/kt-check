package com.tamj0rd2.ktcheck.contracts

import com.tamj0rd2.ktcheck.contract.IGen
import com.tamj0rd2.ktcheck.producer.Seed

internal interface BaseGeneratorContract {
    fun <T : Any> IGen<T>.generateWithShrunkValues(rngValues: List<Any>): Pair<T, List<T>>

    fun <T : Any> IGen<T>.generateWithShrunkValues(seed: Seed = Seed.random()): Pair<T, List<T>>

    fun intGen(range: IntRange): IGen<Int>

    fun boolGen(): IGen<Boolean>

    fun <T> oneOfGen(vararg gens: IGen<T>): IGen<T>

    fun <T> oneOfGen(values: Collection<T>): IGen<T>
}

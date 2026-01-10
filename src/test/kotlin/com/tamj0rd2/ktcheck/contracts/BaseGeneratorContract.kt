package com.tamj0rd2.ktcheck.contracts

import com.tamj0rd2.ktcheck.contract.IGen
import com.tamj0rd2.ktcheck.producer.Seed

internal interface BaseGeneratorContract {
    fun <T : Any> IGen<T>.generateWithShrunkValues(rngValues: List<Any>): Pair<T, List<T>>

    fun <T : Any> IGen<T>.generateWithShrunkValues(seed: Seed = Seed.random()): Pair<T, List<T>>

    /**
     * Create a navigator for testing recursive shrinking.
     * Implementations provide this based on their architecture (ProducerTree or GenResult).
     */
    fun <T : Any> IGen<T>.navigateRecursiveShrinks(rngValues: List<Any>): RecursiveShrinkNavigator<T>

    fun intGen(range: IntRange): IGen<Int>

    fun boolGen(): IGen<Boolean>

    fun <T> oneOfGen(vararg gens: IGen<T>): IGen<T>

    fun <T> oneOfGen(values: Collection<T>): IGen<T>

    fun <T> constantGen(value: T): IGen<T>

    fun <T> IGen<T>.listGen(distinct: Boolean = false): IGen<List<T>>

    fun <T> IGen<T>.listGen(size: Int, distinct: Boolean = false): IGen<List<T>>

    fun <T> IGen<T>.listGen(sizeRange: IntRange, distinct: Boolean = false): IGen<List<T>>

    // Filter generator factory methods
    fun <T> IGen<T>.filterGen(predicate: (T) -> Boolean): IGen<T>

    fun <T> IGen<T>.filterGen(threshold: Int, predicate: (T) -> Boolean): IGen<T>
}

/**
 * Navigator interface for testing recursive shrinking behavior.
 * Abstracts over different generator architectures (ProducerTree vs GenResult).
 */
interface RecursiveShrinkNavigator<T> {
    /** The generated value */
    val value: T

    /** Get shrinks as a list (materializes up to the limit) */
    fun getShrinks(limit: Int = 10): List<RecursiveShrinkNavigator<T>>

    /** Find a shrink by its value */
    fun findShrinkByValue(value: T, limit: Int = 15): RecursiveShrinkNavigator<T>?
}

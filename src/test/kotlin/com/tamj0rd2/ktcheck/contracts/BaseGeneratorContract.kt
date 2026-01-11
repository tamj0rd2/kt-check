package com.tamj0rd2.ktcheck.contracts

import com.tamj0rd2.ktcheck.Gen
import com.tamj0rd2.ktcheck.GenBuilder
import com.tamj0rd2.ktcheck.Seed

internal interface BaseGeneratorContract : GenBuilder {
    fun <T : Any> Gen<T>.generateWithShrunkValues(rngValues: List<Any>): Pair<T, List<T>>

    fun <T : Any> Gen<T>.generateWithShrunkValues(seed: Seed = Seed.random()): Pair<T, List<T>>

    /**
     * Create a navigator for testing recursive shrinking.
     * Implementations provide this based on their architecture (ProducerTree or GenResult).
     */
    fun <T : Any> Gen<T>.navigateRecursiveShrinks(rngValues: List<Any>): RecursiveShrinkNavigator<T>
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

package com.tamj0rd2.ktcheck.contracts

import com.tamj0rd2.ktcheck.Counter.Companion.withCounter
import com.tamj0rd2.ktcheck.Gen
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo

internal interface OneOfGeneratorTestContract : BaseGeneratorContract {

    // todo: if there was an IOneOfGen interface, this could be an extension on that instead. I know that different
    //  generators consume rng values differently.
    fun <T : Any> Gen<T>.generateWithShrunkValuesForOneOfGens(rngValues: List<Any>): Pair<T, List<T>>

    @Test
    fun `can choose between generators uniformly`() {
        val gen = oneOf(
            bool().map { it as Any },
            int(Int.MIN_VALUE..Int.MAX_VALUE).map { it as Any })

        withCounter { gen.samples().take(100_000).forEach { collect(it::class.simpleName) } }
            .checkPercentages(mapOf("Boolean" to 49.0, "Int" to 49.0))
    }

    @Test
    fun `shrinking a oneOf generator can shrink between types without failure`() {
        val multiTypeGen = oneOf(
            bool().map { it as Any },
            int(0..4).map { it as Any })

        // chooses an index, then generates a value from each generator
        val (originalValue, shrinks) = multiTypeGen.generateWithShrunkValuesForOneOfGens(rngValues = listOf(1, 4, true))

        expectThat(originalValue).isEqualTo(4)
        expectThat(shrinks.toList()).isEqualTo(
            listOf(
                // Choice shrunk from 1 to 0. So generating a Boolean value:
                true,
                // Left shrinks complete. So Choice = 1. Now shrinking Int value (4):
                0,
                2,
                3,
            )
        )
    }

    @Test
    fun `oneOfValues shrinks toward first value in collection`() {
        val values = listOf("banana", "apple", "cherry")
        val gen = oneOf(values)

        withCounter {
            gen.samples().take(100_000).forEach { collect(it) }
        }.checkPercentages(values.associateWith { 32.0 })

        val (value, shrinks) = gen.generateWithShrunkValues(rngValues = listOf(2))
        expectThat(value).isEqualTo("cherry")

        expectThat(shrinks.toList()).isEqualTo(listOf("banana", "apple"))
    }
}

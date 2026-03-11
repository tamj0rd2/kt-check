package com.tamj0rd2.ktcheck.contracts

import com.tamj0rd2.ktcheck.Counter.Companion.withCounter
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo

internal interface OneOfGeneratorContract : BaseContract {
    override val exampleGen get() = oneOf(1..10)

    @Test
    fun `can choose between generators uniformly`() {
        val gen = oneOf(
            bool().map { it as Any },
            int().map { it as Any },
        )

        withCounter { gen.samples().take(100_000).forEach { collect(it::class.simpleName) } }
            .checkPercentages(mapOf("Boolean" to 49.0, "Int" to 49.0))
    }

    @Test
    fun `shrinking a oneOf generator can shrink between types without failure`() {
        val multiTypeGen = oneOf(
            bool().map { it as Any },
            int(0..4).map { it as Any }
        )

        val result = multiTypeGen.generating(4)

        // Choice shrunk from 1 to 0. So the first shrink should be a Boolean value:
        expectThat(result.shrunkValues.first()).isA<Boolean>()

        // Left shrinks complete. So Choice = 1. Now shrinking Int value (4):
        expectThat(result.shrunkValues.drop(1)).isEqualTo(listOf(0, 2, 3))
    }

    @Test
    fun `oneOfValues generates a reasonable distribution of values`() {
        val values = listOf("banana", "apple", "cherry")
        val gen = oneOf(values)

        withCounter {
            gen.samples().take(100_000).forEach { collect(it) }
        }.checkPercentages(values.associateWith { 32.0 })
    }

    @Test
    fun `oneOfValues shrink toward first value in the collection`() {
        val values = listOf("banana", "apple", "cherry")
        val gen = oneOf(values)

        val result = gen.generating("cherry")
        expectThat(result).shrunkValues.isEqualTo(listOf("banana", "apple"))
    }
}

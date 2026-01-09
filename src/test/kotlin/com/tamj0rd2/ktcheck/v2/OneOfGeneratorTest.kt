package com.tamj0rd2.ktcheck.v2

import com.tamj0rd2.ktcheck.stats.Counter.Companion.withCounter
import com.tamj0rd2.ktcheck.v2.BooleanGenerator.Companion.bool
import com.tamj0rd2.ktcheck.v2.Gen.Companion.samples
import com.tamj0rd2.ktcheck.v2.IntGenerator.Companion.int
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class OneOfGeneratorTest {
    @Test
    fun `can choose between generators uniformly`() {
        val gen = Gen.oneOf(
            Gen.bool().map { it as Any },
            Gen.int().map { it as Any },
        )

        withCounter { gen.samples().take(100_000).forEach { collect(it::class.simpleName) } }
            .checkPercentages(mapOf("Boolean" to 49.0, "Int" to 49.0))
    }

    @Test
    fun `shrinking a oneOf generator can shrink between types without failure`() {
        val multiTypeGen = Gen.oneOf(
            Gen.bool().map { it as Any },
            Gen.int(0..4).map { it as Any },
        )

        // chooses an index, then generates a value from each generator
        val producer = StubValueProducer(listOf(1, true, 4))
        val (originalValue, shrinks) = multiTypeGen.generateWithShrunkValues(producer)

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
        val gen = Gen.oneOf(values)

        withCounter {
            gen.samples().take(100_000).forEach { collect(it) }
        }.checkPercentages(values.associateWith { 32.0 })

        val (value, shrinks) = gen.generateWithShrunkValues(StubValueProducer(listOf(2)))
        expectThat(value).isEqualTo("cherry")

        expectThat(shrinks.toList()).isEqualTo(listOf("banana", "apple"))
    }
}

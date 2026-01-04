package com.tamj0rd2.ktcheck.gen

import com.tamj0rd2.ktcheck.gen.Gen.Companion.samples
import com.tamj0rd2.ktcheck.gen.ListGeneratorTest.Companion.generateWithDepthFirstShrinks
import com.tamj0rd2.ktcheck.producer.ProducerTree
import com.tamj0rd2.ktcheck.producer.Seed
import com.tamj0rd2.ktcheck.stats.Counter.Companion.withCounter
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

        val tree = ProducerTree.new(Seed(1))
            .withLeftValue(1)
            .withRightValue(4)

        val (originalValue, shrinks) = multiTypeGen.generateWithDepthFirstShrinks(tree)

        expectThat(originalValue).isEqualTo(4)
        expectThat(shrinks.toList()).isEqualTo(
            listOf(
                // Choice shrunk from 1 to 0. So generating a Boolean value:
                true,
                // Choice still 0, shrinks boolean value
                false,
                // Left shrinks complete. So Choice = 1. Now shrinking Int value (4):
                0,
                2,
                1,
                3,
            )
        )
    }

    @Test
    fun `oneOfValues shrinks toward first value in collection`() {
        val values = listOf("banana", "apple", "cherry")
        val gen = Gen.oneOfValues(values)

        withCounter {
            gen.samples().take(100_000).forEach { collect(it) }
        }.checkPercentages(values.associateWith { 32.0 })

        val tree = ProducerTree.new().withValue(2)
        val (value, shrinks) = gen.generateWithDepthFirstShrinks(tree)
        expectThat(value).isEqualTo("cherry")

        expectThat(shrinks.toList()).isEqualTo(listOf("banana", "apple"))
    }
}

package com.tamj0rd2.ktcheck.gen

import com.tamj0rd2.ktcheck.gen.Gen.Companion.samples
import com.tamj0rd2.ktcheck.gen.ListGeneratorTest.Companion.generateAllIncludingShrinks
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
        val treeChoosingGenAtIndex1AndFirstIntAs4 = generateSequence(0L) { it + 1 }
            .first {
                ValueTree.fromSeed(it).left.producer.int(0..1) == 1 &&
                        ValueTree.fromSeed(it).right.producer.int(0..4) == 4
            }
            .let { ValueTree.fromSeed(it) }

        val multiTypeGen = Gen.oneOf(
            Gen.bool().map { it as Any },
            Gen.int(0..4).map { it as Any },
        )

        val (originalValue, shrinks) = multiTypeGen.generateAllIncludingShrinks(treeChoosingGenAtIndex1AndFirstIntAs4)

        /*
         * Choice = Which generator to pick.
         * Value = The value from the chosen generator.
         * Here, Choice = 1 (int generator), Value = 4
         */
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
}

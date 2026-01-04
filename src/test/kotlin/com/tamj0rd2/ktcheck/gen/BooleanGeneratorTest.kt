package com.tamj0rd2.ktcheck.gen

import com.tamj0rd2.ktcheck.gen.Gen.Companion.samples
import com.tamj0rd2.ktcheck.gen.ListGeneratorTest.Companion.generateWithDepthFirstShrinks
import com.tamj0rd2.ktcheck.producer.ProducerTree
import com.tamj0rd2.ktcheck.stats.Counter.Companion.withCounter
import com.tamj0rd2.ktcheck.testing.checkAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.all
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo

class BooleanGeneratorTest {
    @Nested
    inner class Generation {
        @Test
        fun `generates a reasonable distribution of values over multiple runs`() {
            withCounter {
                Gen.bool()
                    .samples()
                    .take(100_000)
                    .forEach { collect(it) }
            }.checkPercentages(mapOf(true to 49.0, false to 49.0))
        }

        @Test
        fun `using the same seed generates the same value`() {
            val gen = Gen.bool()
            val tree = ProducerTree.new()
            val values = List(1000) { gen.generate(tree).value }
            val firstValue = values.first()
            expectThat(values.drop(1)).all { isEqualTo(firstValue) }
        }
    }

    @Nested
    inner class Shrinking {
        // todo: in the future I want to allow the user to specify the shrink direction
        @Test
        fun `true shrinks to false`() {
            withCounter {
                checkAll(Gen.tree()) { tree ->
                    val (value, shrinks) = Gen.bool().generateWithDepthFirstShrinks(tree)
                    collect("original value", value)

                    // test only interested in true values.
                    if (!value) return@checkAll

                    expectThat(shrinks.toList()).isEqualTo(listOf(false))
                }
            }.checkPercentages("original value", mapOf(true to 45.0))
        }

        @Test
        fun `false does not shrink`() {
            withCounter {
                checkAll(Gen.tree()) { tree ->
                    val (value, shrinks) = Gen.bool().generateWithDepthFirstShrinks(tree)
                    collect("original value", value)

                    // test only interested in false values.
                    if (value) return@checkAll

                    expectThat(shrinks.toList()).isEmpty()
                }
            }.checkPercentages("original value", mapOf(false to 45.0))
        }
    }
}

package com.tamj0rd2.ktcheck.v2

import com.tamj0rd2.ktcheck.producer.Seed
import com.tamj0rd2.ktcheck.stats.Counter.Companion.withCounter
import com.tamj0rd2.ktcheck.v2.BooleanGenerator.Companion.bool
import com.tamj0rd2.ktcheck.v2.Gen.Companion.samples
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.all
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isTrue

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
            val seed = Seed.random()
            val values = List(1000) { gen.generate(RandomValueProducer(seed)) }
            val firstValue = values.first()
            expectThat(values.drop(1)).all { isEqualTo(firstValue) }
        }
    }

    @Nested
    inner class Shrinking {
        // todo: in the future I want to allow the user to specify the shrink direction
        @Test
        fun `true shrinks to false`() {
            val (value, shrinks) = Gen.bool().generateWithShrunkValues(StubValueProducer(listOf(true)))
            expectThat(value).isTrue()
            expectThat(shrinks).isEqualTo(listOf(false))
        }

        @Test
        fun `false does not shrink`() {
            val (value, shrinks) = Gen.bool().generateWithShrunkValues(StubValueProducer(listOf(false)))
            expectThat(value).isFalse()
            expectThat(shrinks).isEmpty()
        }
    }
}

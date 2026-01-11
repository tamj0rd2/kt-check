package com.tamj0rd2.ktcheck.contracts

import com.tamj0rd2.ktcheck.Counter.Companion.withCounter
import com.tamj0rd2.ktcheck.Seed
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.all
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isTrue

internal interface BooleanGeneratorTestContract : BaseGeneratorContract {
    @Test
    fun `generates a reasonable distribution of values over multiple runs`() {
        withCounter {
            bool()
                .samples()
                .take(100_000)
                .forEach { collect(it) }
        }.checkPercentages(mapOf(true to 49.0, false to 49.0))
    }

    @Test
    fun `using the same seed generates the same value`() {
        val seed = Seed.random()
        val values = List(1000) { bool().generateWithShrunkValues(seed) }
        val firstValue = values.first()
        expectThat(values.drop(1)).all { isEqualTo(firstValue) }
    }

    // todo: in the future I want to allow the user to specify the shrink direction
    @Test
    fun `true shrinks to false`() {
        val (value, shrinks) = bool().generateWithShrunkValues(rngValues = listOf(true))
        expectThat(value).isTrue()
        expectThat(shrinks).isEqualTo(listOf(false))
    }

    @Test
    fun `false does not shrink`() {
        val (value, shrinks) = bool().generateWithShrunkValues(rngValues = listOf(false))
        expectThat(value).isFalse()
        expectThat(shrinks).isEmpty()
    }
}

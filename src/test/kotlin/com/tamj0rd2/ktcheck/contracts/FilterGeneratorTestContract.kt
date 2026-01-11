package com.tamj0rd2.ktcheck.contracts

import com.tamj0rd2.ktcheck.contract.GenerationException.FilterLimitReached
import com.tamj0rd2.ktcheck.producer.Seed
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isGreaterThan

internal interface FilterGeneratorTestContract : BaseGeneratorContract {

    @Test
    fun `can filter generated values`() {
        val gen = intGen(1..10).filterGen { it % 2 == 0 }

        gen.samples().take(100).forEach { value ->
            expectThat(value % 2).isEqualTo(0)
        }
    }

    @Test
    fun `throws if the filter threshold is exceeded`() {
        val gen = intGen(1..10).filterGen { it > 10 }

        assertThrows<FilterLimitReached> {
            gen.sample()
        }
    }

    @Test
    fun `doesn't produce shrinks that would fail the predicate`() {
        val gen = intGen(1..4).filterGen { it > 2 }

        // Generate a value and get its shrinks
        val (value, shrinks) = gen.generateWithShrunkValues(Seed.random())

        // Original value should pass predicate
        expectThat(value).isGreaterThan(2)

        // All shrinks should also pass predicate
        shrinks.forEach { shrunkValue ->
            expectThat(shrunkValue).isGreaterThan(2)
        }
    }
}


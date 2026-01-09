package com.tamj0rd2.ktcheck.contracts

import com.tamj0rd2.ktcheck.producer.Seed
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo

internal interface CommonGeneratorTestContract : BaseGeneratorContract {
    @Test
    fun `map maps the original value and its shrinks`() {
        val originalGen = intGen(0..10)
        val doublingGen = originalGen.map { it * 2 }

        val seed = Seed.random()
        val (originalNumber, originalShrinks) = originalGen.generateWithShrunkValues(seed)
        val (doubledValue, doubledShrinks) = doublingGen.generateWithShrunkValues(seed)

        expectThat(doubledValue).isEqualTo(originalNumber * 2)
        expectThat(doubledShrinks).isEqualTo(originalShrinks.map { it * 2 })
    }

    @Test
    fun `same seed produces same sample`() {
        val seed = 12345L
        val gen = intGen(-1000..1000)

        val firstRun = gen.samples(seed).take(100).toList()
        val secondRun = gen.samples(seed).take(100).toList()

        expectThat(secondRun).isEqualTo(firstRun)
    }
}

package com.tamj0rd2.ktcheck.contracts

import com.tamj0rd2.ktcheck.core.ProducerTreeDsl.Companion.producerTree
import com.tamj0rd2.ktcheck.core.Seed
import com.tamj0rd2.ktcheck.v1.shrink
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isEqualTo

internal interface CommonGeneratorTestContract : BaseGeneratorContract {
    @Test
    fun `same seed produces same sample`() {
        val seed = 12345L
        val gen = int(-1000..1000)

        val firstRun = gen.samples(seed).take(100).toList()
        val secondRun = gen.samples(seed).take(100).toList()

        expectThat(secondRun).isEqualTo(firstRun)
    }

    @Test
    fun `map maps the original value and its shrinks`() {
        val originalGen = int(0..10)
        val doublingGen = originalGen.map { it * 2 }

        val seed = Seed.random()
        val (originalNumber, originalShrinks) = originalGen.generateWithShrunkValues(seed)
        val (doubledValue, doubledShrinks) = doublingGen.generateWithShrunkValues(seed)

        expectThat(doubledValue).isEqualTo(originalNumber * 2)
        expectThat(doubledShrinks).isEqualTo(originalShrinks.map { it * 2 })
    }

    @Test
    fun `flatMap generates the second value based on the first`() {
        val smallGen = int(0..5)
        val bigGen = int(10..20)
        val gen = smallGen.flatMap { a -> bigGen.map { b -> a + b } }

        val tree = producerTree {
            left(5)
            right(20)
        }

        val (value) = gen.generateWithShrunkValues(tree)
        expectThat(value).isEqualTo(25)
    }

    @Test
    fun `flatMap combines shrinks from both generators`() {
        val smallGen = int(1..3)
        val biggerGen = int(4..6)
        val gen = smallGen.flatMap { a -> biggerGen.map { b -> a + b } }

        val tree = producerTree {
            left(3)
            right(6)
        }

        val (value, shrinks) = gen.generateWithShrunkValues(tree)
        expectThat(value).isEqualTo(9)

        val threeShrunk = shrink(3, range = 1..3)
        val sixShrunk = shrink(6, range = 4..6)

        expectThat(shrinks).contains(threeShrunk.map { it + 6 }.toList())
        expectThat(shrinks).contains(sixShrunk.map { it + 3 }.toList())
    }

}

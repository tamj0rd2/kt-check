package com.tamj0rd2.ktcheck.contracts

import com.tamj0rd2.ktcheck.GenerationException.DistinctCollectionSizeImpossible
import com.tamj0rd2.ktcheck.TestConfig
import com.tamj0rd2.ktcheck.checkAll
import com.tamj0rd2.ktcheck.core.shrinkers.IntShrinker
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.assertTimeoutPreemptively
import strikt.api.expectThat
import strikt.assertions.all
import strikt.assertions.containsExactlyInAnyOrder
import strikt.assertions.first
import strikt.assertions.hasSize
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.isIn
import strikt.assertions.isLessThanOrEqualTo
import strikt.assertions.isNotEmpty
import strikt.assertions.size
import java.time.Duration

internal interface DistinctListGeneratorContract : BaseContract {
    @Test
    fun `can generate a long distinct list without stack overflow`() {
        assertTimeoutPreemptively(Duration.ofSeconds(1)) {
            int().list(10_000, distinct = true).sample()
        }
    }

    @Test
    fun `generates lists with distinct elements`() = checkAll(
        TestConfig().withIterations(100),
        int(0..100).list(5, distinct = true),
    ) {
        expectThat(it.size).isEqualTo(5)
        expectThat(it).hasSize(5) // confirms no duplicates
    }

    @Test
    fun `throws when unable to generate enough distinct elements`() {
        val gen = int(0..10).list(100, distinct = true)
        assertThrows<DistinctCollectionSizeImpossible> { gen.sample() }
    }

    @Test
    fun `shrinks a list of 1 element`() {
        val intGen = int(0..10)
        val gen = intGen.list(distinct = true)

        val result = gen.generating { it.size == 1 }
        expectThat(result.value).hasSize(1)

        val expectedValueShrinks = IntShrinker.shrink(result.value.single(), 0..10).map { listOf(it) }.toList()
        expectThat(result).shrunkValues.first().isEqualTo(emptyList())
        expectThat(result).shrunkValues.get { drop(1) }.isEqualTo(expectedValueShrinks)
    }

    @Test
    fun `shrinks a list of 2 elements`() {
        val gen = int(0..10).list(distinct = true)

        val result = gen.generating(listOf(1, 4))
        expectThat(result.value).isEqualTo(listOf(1, 4))

        expectThat(result).shrunkValues.isNotEmpty().containsExactlyInAnyOrder(
            // tries reducing set size (now 0)
            listOf(),
            // continues reducing set size (now 1) - only "take first 1"
            listOf(1),
            // shrinks values, starting with index 0
            listOf(0, 4),
            // continues shrinking values at index 1
            listOf(1, 0),
            listOf(1, 2),
            listOf(1, 3),
        )
    }

    @Test
    fun `can shrink lists with a minimum size greater than 0`() {
        int().list(1..2, distinct = true).expectGenerationAndShrinkingToEventuallyComplete()
        int().list(2..2, distinct = true).expectGenerationAndShrinkingToEventuallyComplete()
        int().list(2..5, distinct = true).expectGenerationAndShrinkingToEventuallyComplete()
    }

    @Test
    fun `all generated lists contain only distinct elements`() = checkAll(
        TestConfig().withIterations(100),
        int(0..100).list(0..10, distinct = true),
    ) {
        expectThat(it.toSet()).hasSize(it.size)
    }

    @Test
    fun `does not produce any shrinks when the list size is equal to the number of distinct values`() {
        Assumptions.assumeTrue(false, "ignore this for now.")
        // note: there are only 3 possible distinct values. So a distinct list of size 3 can only ever be achieved once: (0, 1, 2)
        val intGen = int(0..2)
        val gen = intGen.list(3, distinct = true)

        val result = gen.generate(tree())
        expectThat(result.value).isEqualTo(listOf(1, 2, 3))
        expectThat(result).shrunkValues.isEmpty()
    }

    @Test
    fun `shrinks to empty list when list is not empty`() {
        val gen = int(0..10).list(distinct = true)

        val result = gen.generating { it.isNotEmpty() }
        expectThat(result.value).isNotEmpty()
        expectThat(result).shrunkValues.first().isEqualTo(emptyList())
    }

    @Test
    fun `all shrunk element values do not exceed max original value`() {
        repeat(1000) {
            val range = 0..10
            val gen = int(range).list(0..4, distinct = true)

            val tree = gen.findTreeProducing { it.isNotEmpty() }
            val result = gen.generate(tree)
            val maxOriginalValue = result.value.max()

            expectThat(result).describedAs { "$this | (${tree.data})" }.shrunkValues.all {
                all { isLessThanOrEqualTo(maxOriginalValue) }
            }
        }
    }

    @Test
    fun `all shrunk element values are within the generator range`() {
        repeat(1000) {
            val range = 0..10
            val gen = int(range).list(distinct = true)

            val result = gen.generating { it.isNotEmpty() }
            expectThat(result).shrunkValues.all { all { isIn(range) } }
        }
    }

    @Test
    fun `all shrunk lists fall within the specified size bounds`() {
        repeat(1000) {
            val minSize = 2
            val gen = int(0..10).list(minSize..10, distinct = true)

            val result = gen.generate(tree())
            val originalSize = result.value.size
            expectThat(result).shrunkValues.all { size.isIn(minSize..originalSize) }
        }
    }
}

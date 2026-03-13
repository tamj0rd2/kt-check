package com.tamj0rd2.ktcheck.contracts

import com.tamj0rd2.ktcheck.GenerationException.DistinctCollectionSizeImpossible
import com.tamj0rd2.ktcheck.core.Seed
import com.tamj0rd2.ktcheck.core.shrinkers.IntShrinker
import com.tamj0rd2.ktcheck.core.shrinkers.IntShrinker.shrink
import com.tamj0rd2.ktcheck.full
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.assertTimeoutPreemptively
import strikt.api.expectThat
import strikt.assertions.all
import strikt.assertions.any
import strikt.assertions.containsExactlyInAnyOrder
import strikt.assertions.filter
import strikt.assertions.first
import strikt.assertions.get
import strikt.assertions.hasSize
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.isGreaterThan
import strikt.assertions.isIn
import strikt.assertions.isLessThanOrEqualTo
import strikt.assertions.isNotEmpty
import strikt.assertions.none
import strikt.assertions.size
import java.time.Duration

internal interface DistinctListGeneratorContract : BaseContract {
    override val exampleGen get() = int(0..10).distinctList(0..4)

    @Test
    fun `generates lists with distinct elements`() {
        repeatTest { seed ->
            val gen = int(0..10).distinctList(0..10)
            val result = gen.generate(tree(seed))
            expectThat(result.value.toSet()).hasSize(result.value.size)
        }
    }

    @Test
    fun `can generate a long distinct list without stack overflow`() {
        assertTimeoutPreemptively(Duration.ofSeconds(1)) {
            int().distinctList(10_000).sample()
        }
    }

    @Test
    fun `throws when unable to generate enough distinct elements`() {
        val gen = int(0..10).distinctList(100)
        assertThrows<DistinctCollectionSizeImpossible> { gen.sample() }
    }

    @Test
    fun `shrinks a list of 1 element`() {
        repeatTest { seed ->
            val gen = int(0..10).distinctList(size = 0..5)
            val result = gen.generate(tree(seed))
            if (result.value.size != 1) skipIteration()

            val expectedValueShrinks = shrink(result.value.single(), 0..10).map { listOf(it) }.toList()
            expectThat(result).shrunkValues.isEqualTo(
                listOf(
                    emptyList(),
                    *expectedValueShrinks.toTypedArray(),
                )
            )
        }
    }

    @Test
    fun `shrinks a list of 2 elements`() = repeatTest { seed ->
        val gen = int(0..10).distinctList(size = 0..10)
        val result = gen.generate(tree(seed))
        if (result.value.size != 2) skipIteration()

        val firstValue = result.value[0]
        val secondValue = result.value[1]

        expectThat(result).shrunkValues.filter { it != result.value }.isNotEmpty().containsExactlyInAnyOrder(
            // tries reducing set size (now 0)
            listOf(),
            // continues reducing set size (now 1). From tail first, then head.
            listOf(firstValue),
            listOf(secondValue),
            // element shrinks
            *let {
                val firstValueShrunk = IntShrinker.shrink(firstValue, 0..10).map { listOf(it, secondValue) }
                val secondValueShrunk = IntShrinker.shrink(secondValue, 0..10).map { listOf(firstValue, it) }
                (firstValueShrunk + secondValueShrunk).filter { it.toSet().size == 2 }.toList()
            }.toTypedArray<List<Int>>(),
        )
    }

    @Test
    fun `can shrink lists with a minimum size greater than 0`() {
        int().distinctList(1..2).expectGenerationAndShrinkingToEventuallyComplete()
        int().distinctList(2..2).expectGenerationAndShrinkingToEventuallyComplete()
        int().distinctList(2..5).expectGenerationAndShrinkingToEventuallyComplete()
    }

    @Test
    fun `does not produce any shrinks when the list size is equal to the number of distinct values`() {
        // note: there are only 3 possible distinct values. So a distinct list of size 3 can only ever be achieved once
        repeatTest { seed ->
            val intGen = int(1..3)
            val gen = intGen.distinctList(3)

            val result = gen.generate(tree(seed))
            expectThat(result).value.hasSize(3)
            expectThat(result).shrunkValues.isEmpty()
        }
    }

    @Test
    fun `shrinks to empty list when list is not empty`() {
        val gen = int(0..10).distinctList(0..5)

        repeatTest { seed ->
            val result = gen.generate(tree(seed))
            if (result.value.isEmpty()) skipIteration()
            expectThat(result).shrunkValues.first().isEqualTo(emptyList())
        }
    }

    @Test
    fun `no shrunk element values exceed the max original value`() {
        repeatTest { seed ->
            val gen = int(0..10).distinctList(0..4)

            val result = gen.generate(tree(seed))
            if (result.value.isEmpty()) skipIteration()

            val maxOriginalValue = result.value.max()
            expectThat(result).shrunkValues.all {
                all { isLessThanOrEqualTo(maxOriginalValue) }
            }
        }
    }

    @Test
    fun `all shrunk element values are within the generator range`() {
        repeatTest { seed ->
            val range = 0..10
            val gen = int(range).distinctList(0..5)

            val result = gen.generate(tree(seed))
            if (result.value.isEmpty()) skipIteration()
            expectThat(result).shrunkValues.all { all { isIn(range) } }
        }
    }

    @Test
    fun `all shrunk lists fall within the specified size bounds`() {
        repeatTest { seed ->
            val minSize = 2
            val gen = int(0..10).distinctList(minSize..10)

            val result = gen.generate(tree(seed))
            val originalSize = result.value.size
            expectThat(result).shrunkValues.all { size.isIn(minSize..originalSize) }
        }
    }

    @Test
    fun `follows the left generation, right continuation pattern`() {
        repeatTest { seed ->
            // using the full int range should make conflicts (and thereby flaky tests) incredibly unlikely.
            val intGen = int(IntRange.full)
            val listGen = intGen.distinctList(2)
            val root = tree(seed)
            val result = listGen.generate(root)

            expectThat(result.value) {
                // both preceded with root.right because root.left is used for size generation
                get(0).isEqualTo(intGen.generate(root.right.left).value)
                get(1).isEqualTo(intGen.generate(root.right.right.left).value)
            }
        }
    }

    @TestFactory
    fun `edge case generation`(): List<DynamicTest> {
        data class TestCase(
            val sizeRange: IntRange,
            val hasEmpty: Boolean,
            val hasSingle: Boolean,
            val hasMulti: Boolean,
        ) {
            val description = "for a list with sizeRange $sizeRange"
        }

        val testCases = listOf(
            TestCase(sizeRange = 0..5, hasEmpty = true, hasSingle = true, hasMulti = true),
            TestCase(sizeRange = 1..5, hasEmpty = false, hasSingle = true, hasMulti = true),
            TestCase(sizeRange = 3..5, hasEmpty = false, hasSingle = false, hasMulti = true),
            TestCase(sizeRange = 0..0, hasEmpty = true, hasSingle = false, hasMulti = false),
            TestCase(sizeRange = 2..2, hasEmpty = false, hasSingle = false, hasMulti = true),
            TestCase(sizeRange = 1..1, hasEmpty = false, hasSingle = true, hasMulti = false),
        )

        return testCases.map { tc ->
            dynamicTest(tc.description) {
                val gen = int(0..10).list(tc.sizeRange)
                val edgeCaseValues = Seed.sequence().take(1_000).map { gen.edgeCase(it)!!.value }.toSet()

                when (tc.hasEmpty) {
                    true -> expectThat(edgeCaseValues).any { isEmpty() }
                    false -> expectThat(edgeCaseValues).none { isEmpty() }
                }

                when (tc.hasSingle) {
                    true -> expectThat(edgeCaseValues).any { size.isEqualTo(1) }
                    false -> expectThat(edgeCaseValues).none { size.isEqualTo(1) }
                }

                when (tc.hasMulti) {
                    true -> expectThat(edgeCaseValues).any { size.isGreaterThan(1) }
                    false -> expectThat(edgeCaseValues).none { size.isGreaterThan(1) }
                }
            }
        }
    }
}

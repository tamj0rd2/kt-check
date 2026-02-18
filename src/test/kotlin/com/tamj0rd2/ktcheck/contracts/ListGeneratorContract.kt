package com.tamj0rd2.ktcheck.contracts

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import strikt.api.expectThat
import strikt.assertions.all
import strikt.assertions.any
import strikt.assertions.contains
import strikt.assertions.first
import strikt.assertions.isEqualTo
import strikt.assertions.isIn
import strikt.assertions.isLessThan
import strikt.assertions.isLessThanOrEqualTo
import strikt.assertions.isNotEmpty
import strikt.assertions.size

internal interface ListGeneratorContract : BaseContract {
    @Test
    fun `can generate a long list without stack overflow`() {
        constant(1).list(10_000).sample()
    }

    @Test
    fun `shrinks a list of 1 element`() {
        val gen = int(0..4).list()

        val result = gen.generating(listOf(4))

        expectThat(result).shrunkValues.isEqualTo(
            listOf(
                // shrinks the size
                emptyList(),
                // shrinks the value
                listOf(0),
                listOf(2),
                listOf(3),
            )
        )
    }

    @Test
    fun `recursively shrinks a list of 2 elements`() {
        val gen = int(0..4).list()

        val result = gen.generating(listOf(3, 4))

        expectThat(result).shrunkValues.isNotEmpty().contains(
            // size shrinks
            emptyList(),
            listOf(3),
            listOf(4),
            // element shrinks
            listOf(0, 4),
            listOf(3, 0),
        )
    }

    @Test
    fun `once the elements of a list have been shrunk, the resultant shrinks can also be shrunk by size`() {
        val gen = int(0..10).list(0..3)

        val root = gen.generating(listOf(3, 4))

        val firstNonSizeShrink = root.shrinks.first { it.value.size == root.value.size }
        expectThat(firstNonSizeShrink.shrinks.toList())
            .describedAs("shrinks of ${firstNonSizeShrink.value}")
            .any { get { value }.size.isLessThan(root.value.size) }
    }

    @Test
    fun `size shrink from 4 to 2 includes both first 2 and last 2 elements`() {
        val gen = int(0..10).list()

        val result = gen.generating { list ->
            list.size == 4 &&
                    list.take(2) != list.takeLast(2)
        }

        expectThat(result.value).size.isEqualTo(4)
        expectThat(result).shrunkValues.contains(
            result.value.take(2),
            result.value.takeLast(2),
        )
    }

    @Test
    fun `shrinks to empty list when list is not empty`() {
        val gen = int(0..10).list()

        val result = gen.generating { it.isNotEmpty() }
        expectThat(result.value).isNotEmpty()
        expectThat(result).shrunkValues.first().isEqualTo(emptyList())
    }

    @Test
    fun `all shrunk element values do not exceed max original value`() {
        repeat(1000) {
            val range = 0..10
            val gen = int(range).list(0..4)

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
            val gen = int(range).list()

            val result = gen.generating { it.isNotEmpty() }
            expectThat(result).shrunkValues.all { all { isIn(range) } }
        }
    }

    @TestFactory
    fun `edge case generation`(): List<DynamicTest> {
        data class TestCase(
            val sizeRange: IntRange,
            val expectedInclusions: Set<List<Int>>,
            val expectedExclusions: Set<List<Int>>,
        ) {
            val description = "for a list with sizeRange $sizeRange"
        }

        val testCases = listOf(
            TestCase(
                sizeRange = 0..10,
                expectedInclusions = setOf(emptyList(), listOf(0), listOf(10), listOf(0, 0), listOf(10, 10)),
                expectedExclusions = emptySet()
            ),
            TestCase(
                sizeRange = 1..5,
                expectedInclusions = setOf(listOf(0), listOf(10), listOf(0, 0), listOf(10, 10)),
                expectedExclusions = setOf(emptyList())
            ),
            TestCase(
                sizeRange = 3..10,
                expectedInclusions = setOf(listOf(0, 0, 0), listOf(10, 10, 10)),
                expectedExclusions = setOf(emptyList(), listOf(0), listOf(10), listOf(0, 0), listOf(10, 10))
            ),
            TestCase(
                sizeRange = 5..10,
                expectedInclusions = setOf(listOf(0, 0, 0, 0, 0), listOf(10, 10, 10, 10, 10)),
                expectedExclusions = setOf(emptyList(), listOf(0), listOf(10), listOf(0, 0), listOf(10, 10))
            ),
            TestCase(
                sizeRange = 0..0,
                expectedInclusions = setOf(emptyList()),
                expectedExclusions = setOf(listOf(0), listOf(10), listOf(0, 0), listOf(10, 10))
            ),
            TestCase(
                sizeRange = 2..2,
                expectedInclusions = setOf(listOf(0, 0), listOf(10, 10)),
                expectedExclusions = setOf(emptyList(), listOf(0), listOf(10))
            ),
            TestCase(
                sizeRange = 1..1,
                expectedInclusions = setOf(listOf(0), listOf(10)),
                expectedExclusions = setOf(emptyList(), listOf(0, 0), listOf(10, 10))
            )
        )

        return testCases.map { tc ->
            dynamicTest(tc.description) {
                val gen = int(0..10).list(tc.sizeRange)
                val edgeCaseValues = gen.edgeCases().map { it.value }.toList()

                if (tc.expectedInclusions.isNotEmpty()) {
                    expectThat(edgeCaseValues).contains(tc.expectedInclusions)
                }

                if (tc.expectedExclusions.isNotEmpty()) {
                    expectThat(edgeCaseValues).not().contains(tc.expectedExclusions)
                }
            }
        }
    }
}

package com.tamj0rd2.ktcheck.contracts

import org.junit.jupiter.api.Test
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

    @Test
    fun `edge cases include an empty list when size range allows`() {
        val gen = int().list(0..10)

        val edgeCaseValues = gen.edgeCases().map { it.value }.toList()

        expectThat(edgeCaseValues).contains(listOf(emptyList()))
    }

    @Test
    fun `edge cases include singleton lists with element edge cases`() {
        val gen = int(0..100).list(0..10)

        val edgeCaseValues = gen.edgeCases().map { it.value }.toList()

        expectThat(edgeCaseValues).contains(listOf(listOf(0), listOf(100)))
    }

    @Test
    fun `edge cases include duplicate lists with element edge cases`() {
        val gen = int(0..100).list(0..10)

        val edgeCaseValues = gen.edgeCases().map { it.value }.toList()

        expectThat(edgeCaseValues).contains(listOf(listOf(0, 0), listOf(100, 100)))
    }

    @Test
    fun `edge cases for list(0 to 10) includes empty, singleton, and duplicate at size 2`() {
        val gen = int(0..100).list(0..10)

        val edgeCaseValues = gen.edgeCases().map { it.value }.toList()

        expectThat(edgeCaseValues).contains(
            listOf(
                emptyList(),
                listOf(0),
                listOf(100),
                listOf(0, 0),
                listOf(100, 100)
            )
        )
    }

    @Test
    fun `edge cases for list(1 to 5) includes singleton and duplicate but not empty`() {
        val gen = int(0..100).list(1..5)

        val edgeCaseValues = gen.edgeCases().map { it.value }.toList()

        expectThat(edgeCaseValues).contains(
            listOf(
                listOf(0),
                listOf(100),
                listOf(0, 0),
                listOf(100, 100)
            )
        )
        expectThat(edgeCaseValues).not().contains(listOf(emptyList()))
    }

    @Test
    fun `edge cases for list(3 to 10) includes duplicate at size 3 but not empty or singleton`() {
        val gen = int(0..100).list(3..10)

        val edgeCaseValues = gen.edgeCases().map { it.value }.toList()

        expectThat(edgeCaseValues).contains(
            listOf(
                listOf(0, 0, 0),
                listOf(100, 100, 100)
            )
        )
        expectThat(edgeCaseValues).not().contains(
            listOf(
                emptyList(),
                listOf(0),
                listOf(100),
                listOf(0, 0),
                listOf(100, 100)
            )
        )
    }

    @Test
    fun `edge cases for list(5 to 10) includes duplicate at size 5 but not empty or singleton`() {
        val gen = int(0..100).list(5..10)

        val edgeCaseValues = gen.edgeCases().map { it.value }.toList()

        expectThat(edgeCaseValues).contains(
            listOf(
                listOf(0, 0, 0, 0, 0),
                listOf(100, 100, 100, 100, 100)
            )
        )
        expectThat(edgeCaseValues).not().contains(
            listOf(
                emptyList(),
                listOf(0),
                listOf(100),
                listOf(0, 0),
                listOf(100, 100),
                listOf(0, 0, 0),
                listOf(100, 100, 100)
            )
        )
    }

    @Test
    fun `edge cases for list(0 to 0) includes only empty list`() {
        val gen = int(0..100).list(0..0)

        val edgeCaseValues = gen.edgeCases().map { it.value }.toList()

        expectThat(edgeCaseValues).contains(listOf(emptyList()))
        expectThat(edgeCaseValues).not().contains(
            listOf(
                listOf(0),
                listOf(100),
                listOf(0, 0),
                listOf(100, 100)
            )
        )
    }

    @Test
    fun `edge cases for list(2 to 2) includes duplicate at size 2 but not empty or singleton`() {
        val gen = int(0..100).list(2..2)

        val edgeCaseValues = gen.edgeCases().map { it.value }.toList()

        expectThat(edgeCaseValues).contains(
            listOf(
                listOf(0, 0),
                listOf(100, 100)
            )
        )
        expectThat(edgeCaseValues).not().contains(
            listOf(
                emptyList(),
                listOf(0),
                listOf(100)
            )
        )
    }

    @Test
    fun `edge cases for list(1 to 1) includes only singleton`() {
        val gen = int(0..100).list(1..1)

        val edgeCaseValues = gen.edgeCases().map { it.value }.toList()

        expectThat(edgeCaseValues).contains(
            listOf(
                listOf(0),
                listOf(100)
            )
        )
        expectThat(edgeCaseValues).not().contains(
            listOf(
                emptyList(),
                listOf(0, 0),
                listOf(100, 100)
            )
        )
    }
}

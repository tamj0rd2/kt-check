package com.tamj0rd2.ktcheck.contracts

import com.tamj0rd2.ktcheck.Gen
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.all
import strikt.assertions.first
import strikt.assertions.isEmpty
import strikt.assertions.isIn
import strikt.assertions.isNotEmpty
import strikt.assertions.size

internal interface BehavesLikeAListGeneratorContract {
    fun newListLikeGen(sizeRange: IntRange): Gen<List<Any?>>

    @Test
    fun `shrinks to empty value when the original value is not empty`() {
        val gen = newListLikeGen(0..10)

        repeatTest { seed ->
            val (originalList, shrunkValues) = gen.collectShrunkValues(
                seed = seed,
                startShrinkingOnce = { it.isNotEmpty() }
            )
            expectThat(originalList).isNotEmpty()
            expectThat(shrunkValues).describedAs("shrunk values").first().isEmpty()
        }
    }

    @Test
    fun `empty values don't shrink`() {
        val gen = newListLikeGen(0..10)

        repeatTest { seed ->
            val (originalList, shrunkValues) = gen.collectShrunkValues(
                seed = seed,
                startShrinkingOnce = { it.isEmpty() }
            )
            expectThat(originalList).isEmpty()
            expectThat(shrunkValues).describedAs("shrunk values").isEmpty()
        }
    }

    @Test
    fun `all shrunk values are within the specified size bounds`() {
        val sizeRange = 0..10
        val gen = newListLikeGen(sizeRange)

        repeatTest { seed ->
            val (originalList, shrunkValues) = gen.collectShrunkValues(
                seed = seed,
                startShrinkingOnce = { it.isNotEmpty() && it != listOf(0) }
            )

            expectThat(shrunkValues)
                .describedAs { "shrinks of $originalList" }
                .isNotEmpty()
                .all { size.isIn(sizeRange) }
        }
    }
}

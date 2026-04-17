package com.tamj0rd2.ktcheck.contracts

import com.tamj0rd2.ktcheck.core.shrinkers.IntShrinker
import com.tamj0rd2.ktcheck.stats.Percentage.Companion.percent
import com.tamj0rd2.ktcheck.stats.withCounter
import org.junit.jupiter.api.Test
import strikt.api.expectDoesNotThrow
import strikt.api.expectThat
import strikt.assertions.all
import strikt.assertions.contains
import strikt.assertions.isContainedIn
import strikt.assertions.isEqualTo
import strikt.assertions.isIn
import strikt.assertions.isNotEmpty

internal interface FlatMappingGeneratorContract : BaseContract {
    override val exampleGen get() = int(0..5).flatMap { int(10..10 + it) }

    @Test
    fun `generates the second value based on the first`() {
        val smallGen = int(0..5)
        val bigGen = int(10..15)
        val gen = smallGen.flatMap { a -> bigGen.map { b -> a + b } }

        repeatTest { seed ->
            val tree = ctx(seed)
            val value = gen.generate(tree).value
            expectThat(value).isIn(10..20)
        }
    }

    @Test
    fun `combines shrinks from both generators`() {
        val oneToThree = int(1..3)
        val fourToSix = int(4..6)
        val gen = oneToThree.flatMap { outer ->
            fourToSix.map { inner ->
                Pair(outer, inner)
            }
        }

        repeatTest { seed ->
            val result = gen.generate(ctx())
            if (result.value == 1 to 4) skipIteration()

            expectThat(result).shrunkValues.isNotEmpty().isEqualTo(
                listOf(
                    IntShrinker.shrink(result.value.first, 1..3).map { result.value.copy(first = it) }.toList(),
                    IntShrinker.shrink(result.value.second, 4..6).map { result.value.copy(second = it) }.toList()
                ).flatten()
            )
        }
    }

    @Test
    fun `allows changing the constraints of the inner generator`() {
        val gen = int(0..2).flatMap { int(10..10 + it) }

        // would require that the outer generator produced a 2
        val result = gen.generating(12)
        expectThat(result.value).isEqualTo(12)
        expectDoesNotThrow { result.shrunkValues.toSet() }
    }
}

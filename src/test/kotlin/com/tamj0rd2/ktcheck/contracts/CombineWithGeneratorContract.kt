package com.tamj0rd2.ktcheck.contracts

import com.tamj0rd2.ktcheck.core.shrinkers.IntShrinker
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.first
import strikt.assertions.isIn
import strikt.assertions.isNotEmpty
import strikt.assertions.second

internal interface CombineWithGeneratorContract : BaseContract {
    override val exampleGen get() = int().combineWith(int(), ::Pair)

    @Test
    fun `combineWith merges two independent generators`() {
        val smallGen = int(0..5)
        val bigGen = int(10..20)
        val gen = smallGen.combineWith(bigGen, ::Pair)

        repeatTest { seed ->
            val tree = ctx(seed)
            val value = gen.generate(tree).value
            expectThat(value).first.isIn(0..5)
            expectThat(value).second.isIn(10..20)
        }
    }

    @Test
    fun `combineWith combines shrinks from both generators`() {
        val oneToThree = int(1..3)
        val fourToSix = int(4..6)
        val gen = oneToThree.combineWith(fourToSix, ::Pair)

        repeatTest { seed ->
            val result = gen.generate(ctx())
            if (result.value == 1 to 4) skipIteration()

            // todo: later, I want to change this to isEqualTo and stop providing the cartesian product.
            //  I want a different generate to inject duplicates.
            expectThat(result).shrunkValues.isNotEmpty().contains(
                listOf(
                    IntShrinker.shrink(result.value.first, 1..3).map { result.value.copy(first = it) }.toList(),
                    IntShrinker.shrink(result.value.second, 4..6).map { result.value.copy(second = it) }.toList()
                ).flatten()
            )
        }
    }
}

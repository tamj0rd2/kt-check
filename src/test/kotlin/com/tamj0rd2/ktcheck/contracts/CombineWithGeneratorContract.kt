package com.tamj0rd2.ktcheck.contracts

import com.tamj0rd2.ktcheck.core.Seed
import com.tamj0rd2.ktcheck.core.shrinkers.IntShrinker
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.first
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.isNotEmpty
import strikt.assertions.isNotNull
import strikt.assertions.isNull
import strikt.assertions.second

internal interface CombineWithGeneratorContract : BaseContract {
    override val exampleGen get() = int().combineWith(int(), ::Pair)

    @Test
    fun `combineWith merges two independent generators`() {
        val smallGen = int(0..5)
        val bigGen = int(10..20)
        val gen = smallGen.combineWith(bigGen) { a, b -> a + b }

        repeatTest { seed ->
            val tree = tree(seed)
            val expectedOuterValue = smallGen.generate(tree.left).value
            val expectedInnerValue = bigGen.generate(tree.right).value

            val value = gen.generate(tree).value
            expectThat(value).isEqualTo(expectedOuterValue + expectedInnerValue)
        }
    }

    @Test
    fun `combineWith combines shrinks from both generators`() {
        val oneToThree = int(1..3)
        val fourToSix = int(4..6)
        val gen = oneToThree.combineWith(fourToSix, ::Pair)

        val tree = tree()
            .withLeft(oneToThree.findTreeProducing(3))
            .withRight(fourToSix.findTreeProducing(6))

        val result = gen.generate(tree)
        expectThat(result.value).isEqualTo(3 to 6)
        expectThat(result).shrunkValues.isNotEmpty().contains(
            // first value shrunk
            1 to 6,
            // second value shrunk
            3 to 4,
        )
    }

    @Test
    fun `combineWith produces edge case permutations from both generators`() {
        val gen1 = int(0..10)
        val gen2 = int(0..10)
        val combined = gen1.combineWith(gen2, ::Pair)

        val edgeCases = combined.edgeCases(Seed.random())
        expectThat(edgeCases.map { it.value }).contains(
            0 to 0,
            0 to 10,
            10 to 0,
            10 to 10,
        )

        val shrinksFor10 = IntShrinker.shrink(10, 0..10, 0).toList()
        val edgeCaseWhereBothAre10 = edgeCases.single { it.value == Pair(10, 10) }
        expectThat(edgeCaseWhereBothAre10).shrunkValues.contains(
            listOf(
                shrinksFor10.map { Pair(it, it) },
                shrinksFor10.map { Pair(it, 10) },
                shrinksFor10.map { Pair(10, it) },
            ).flatten().distinct()
        )
    }

    @Test
    fun `combineWith can still produce edge cases for the first generator if the second generator has no edge cases`() {
        val gen1 = int(0..10)
        val gen2 = constant("hello")
        val combinedGen = gen1.combineWith(gen2, ::Pair)

        repeatTest { seed ->
            val gen1EdgeCase = gen1.edgeCase(seed)
            val gen2EdgeCase = gen2.edgeCase(seed)

            expectThat(gen1EdgeCase).isNotNull()
            expectThat(gen2EdgeCase).isNull()
            expectThat(combinedGen.edgeCase(seed))
                .isNotNull()
                .value
                .first
                .isEqualTo(gen1EdgeCase!!.value)
        }
    }

    @Test
    fun `combineWith can still produce edge cases for the second generator if the first generator has no edge cases`() {
        val gen1 = constant("hello")
        val gen2 = int(0..10)
        val combinedGen = gen1.combineWith(gen2, ::Pair)

        repeatTest { seed ->
            val gen1EdgeCase = gen1.edgeCase(seed)
            val gen2EdgeCase = gen2.edgeCase(seed)

            expectThat(gen1EdgeCase).isNull()
            expectThat(gen2EdgeCase).isNotNull()
            expectThat(combinedGen.edgeCase(seed))
                .isNotNull()
                .value
                .second
                .isEqualTo(gen2EdgeCase!!.value)
        }
    }

    @Test
    fun `combineWith doesn't yield any edge cases if neither generator has any`() {
        val gen1 = constant("hello")
        val gen2 = constant("world")
        val combinedGen = gen1.combineWith(gen2, ::Pair)

        val seed = Seed.random()
        expectThat(gen1.edgeCases(seed)).describedAs { "gen1 edge cases - $this" }.isEmpty()
        expectThat(gen2.edgeCases(seed)).describedAs { "gen2 edge cases - $this" }.isEmpty()
        expectThat(combinedGen.edgeCases(seed)).isEmpty()
    }
}

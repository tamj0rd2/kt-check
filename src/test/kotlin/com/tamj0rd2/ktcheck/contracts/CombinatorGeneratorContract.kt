package com.tamj0rd2.ktcheck.contracts

import org.junit.jupiter.api.Test
import strikt.api.expectDoesNotThrow
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.isNotEmpty

internal interface CombinatorGeneratorContract : BaseContract {
    @Test
    fun `constant always produces the same value and doesn't shrink`() {
        // todo: move this test elsewhere.
        val result = constant(10).generate(tree())
        expectThat(result.value).isEqualTo(10)
        expectThat(result).shrunkValues.isEmpty()
    }

    @Test
    fun `same seed produces same sample`() {
        // todo: move this test elsewhere.
        val seed = 12345L
        val gen = int(-1000..1000)

        val firstRun = gen.samples(seed).take(100).toList()
        val secondRun = gen.samples(seed).take(100).toList()

        expectThat(secondRun).isEqualTo(firstRun)
    }

    @Test
    fun `map maps the original value and shrinks`() {
        val originalGen = int(0..10)
        val doublingGen = originalGen.map { it * 2 }

        val tree = tree()
        val originalResult = originalGen.generate(tree)
        val doubledResult = doublingGen.generate(tree)

        expectThat(doubledResult.value).isEqualTo(originalResult.value * 2)
        expectThat(doubledResult).shrunkValues.isEqualTo(originalResult.shrunkValues.map { it * 2 })
    }

    @Test
    fun `flatMap generates the second value based on the first`() {
        val smallGen = int(0..5)
        val bigGen = int(10..20)
        val gen = smallGen.flatMap { a -> bigGen.map { b -> a + b } }

        val tree = tree()
            .withLeft(smallGen.findTreeProducing(5))
            .withRight(bigGen.findTreeProducing(20))

        val value = gen.generate(tree).value
        expectThat(value).isEqualTo(25)
    }

    @Test
    fun `flatMap combines shrinks from both generators`() {
        val oneToThree = int(1..3)
        val fourToSix = int(4..6)
        val gen = oneToThree.flatMap { outer ->
            fourToSix.map { inner ->
                Pair(outer, inner)
            }
        }

        val tree = tree()
            .withLeft(oneToThree.findTreeProducing(3))
            .withRight(fourToSix.findTreeProducing(6))

        val result = gen.generate(tree)
        expectThat(result.value).isEqualTo(3 to 6)
        expectThat(result).shrunkValues.contains(
            // inner value shrunk
            3 to 4,
            // outer value shrunk
            1 to 6,
        )
    }

    @Test
    fun `flatMap allows changing the constraints of the inner generator`() {
        val gen = int(0..2).flatMap { int(10..10 + it) }

        // would require that the outer generator produced a 2
        val result = gen.generating(12)
        expectThat(result.value).isEqualTo(12)
        expectDoesNotThrow { result.shrunkValues.toSet() }
    }

    @Test
    fun `combineWith merges two independent generators`() {
        val smallGen = int(0..5)
        val bigGen = int(10..20)
        val gen = smallGen.combineWith(bigGen) { a, b -> a + b }

        val tree = tree()
            .withLeft(smallGen.findTreeProducing(5))
            .withRight(bigGen.findTreeProducing(20))

        val value = gen.generate(tree).value
        expectThat(value).isEqualTo(25)
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
            // inner value shrunk
            3 to 4,
            // outer value shrunk
            1 to 6,
        )
    }

    @Test
    fun `combineWith produces edge case permutations from both generators`() {
        val gen1 = int(0..100)
        val gen2 = int(0..100)
        val combined = gen1.combineWith(gen2, ::Pair)

        val edgeCases = combined.edgeCases().map { it.value }.toSet()

        expectThat(edgeCases).contains(
            0 to 0,
            0 to 100,
            100 to 0,
            100 to 100,
        )
    }
}

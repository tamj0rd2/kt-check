package com.tamj0rd2.ktcheck.contracts

import com.tamj0rd2.ktcheck.stats.withCounter
import org.junit.jupiter.api.Test
import strikt.api.expectDoesNotThrow
import strikt.api.expectThat
import strikt.assertions.all
import strikt.assertions.contains
import strikt.assertions.isContainedIn
import strikt.assertions.isEqualTo
import strikt.assertions.isIn

internal interface FlatMapGeneratorContract : BaseContract {
    override val exampleGen get() = int(0..5).flatMap { int(10..10 + it) }

    @Test
    fun `generates the second value based on the first`() {
        val smallGen = int(0..5)
        val bigGen = int(10..20)
        val gen = smallGen.flatMap { a -> bigGen.map { b -> a + b } }

        repeatTest { seed ->
            val tree = tree(seed)
            val expectedOuterValue = smallGen.generate(tree.left).value
            val expectedInnerValue = bigGen.generate(tree.right).value

            val value = gen.generate(tree).value
            expectThat(value).isEqualTo(expectedOuterValue + expectedInnerValue)
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

        val tree = tree()
            .withLeft(oneToThree.findTreeProducing(3))
            .withRight(fourToSix.findTreeProducing(6))

        val result = gen.generate(tree)
        expectThat(result.value).isEqualTo(3 to 6)
        expectThat(result).shrunkValues.contains(
            // outer value shrunk
            1 to 6,
            // inner value shrunk
            3 to 4,
        )
    }

    @Test
    fun `allows changing the constraints of the inner generator`() {
        val gen = int(0..2).flatMap { int(10..10 + it) }

        // would require that the outer generator produced a 2
        val result = gen.generating(12)
        expectThat(result.value).isEqualTo(12)
        expectDoesNotThrow { result.shrunkValues.toSet() }
    }

    @Test
    fun `edge cases combine the outer generators edge cases with the inner generator's derived edge cases`() {
        val expectedEdgeCases = setOf(
            // inner edge cases, with max increased by 0 due to outer
            10, 11, 14, 15,
            // inner edge cases, with max increased by 1 due to outer
            10, 11, 15, 16,
            // inner edge cases, with max increased by 4 due to outer
            10, 11, 18, 19,
            // inner edge cases, with max increased by 5 due to outer
            10, 11, 19, 20,
        )

        withCounter {
            repeatTest { seed ->
                val gen = int(0..5).flatMap { outer -> int(10..15 + outer) }
                val edgeCase = gen.edgeCase(seed)!!

                collect(edgeCase.value)

                expectThat(edgeCase).and {
                    value.isContainedIn(expectedEdgeCases)

                    val originalValue = subject.value
                    // this does allow for shrunk values to include the original value, which can be argued is not a shrink.
                    // explanation is detailed below. It's a known problem that I'm not going to work-around.
                    shrunkValues.all { isIn(10..originalValue) }
                }

                if (edgeCase.value == 18) {
                    /**
                     * So here, we're looking at the edge case 18. That edge case is reached by setting left = 4, right = 18.
                     * In the state where the edge case is created, the maximum value of the inner generator is 19.
                     *
                     * When we take that edge case (left = 4, right = 18) and shrink the left side (left = 3, right = 18)
                     * the new maximum of the inner generator is 18.
                     *
                     * When we try to generate a value using that tere, we end up with the value 18. That's
                     * specifically because the predetermined value 18 in the right tree DOES still fall into the new maximum
                     * range of the inner generator (18).
                     *
                     * Note that if left had shrunk such that 18 wasn't in range (i.e left = 0, right = 18, so max = 15),
                     * RandomTree would just generate a new value based on the new constraints.
                     */
                    expectThat(edgeCase).shrunkValues.contains(18)
                }
            }
            // ensures that each edge case does actually appear
        }.checkPercentages(expectedEdgeCases.associateWith { 1.0 })
    }
}

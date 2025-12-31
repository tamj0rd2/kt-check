package com.tamj0rd2.ktcheck.genv2

import com.tamj0rd2.ktcheck.genv2.Value.Predetermined
import com.tamj0rd2.ktcheck.genv2.Value.Predetermined.Choice.IntChoice
import org.junit.jupiter.api.Test
import strikt.api.expectDoesNotThrow
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.contains
import strikt.assertions.isA
import strikt.assertions.isNotNull
import strikt.assertions.message

/**
 * Demonstrates that the TODO in ValueTree can be hit when using flatMap with a function
 * that returns different generator types based on its input.
 *
 * The issue:
 * - flatMap generates with one generator type (Gen.int) which creates shrinks with IntChoice
 * - During shrinking, both sides shrink: the decision changes AND the int shrinks
 * - The new decision causes flatMap to switch to a different generator type (Gen.bool)
 * - Gen.bool tries to read from a tree that has an IntChoice -> TODO!
 */
class OneOfGeneratorTest {
    @Test
    fun `can choose between two generators`() {
        val treeYieldingZeroOnLeftTreeFirst = generateSequence(0L) { it + 1 }
            .first { ValueTree.fromSeed(it).left.value.int(0..1) == 0 }
            .let { ValueTree.fromSeed(it) }

        val intGen = Gen.int(0..10).map { it as Any }
        val boolGen = Gen.bool().map { it as Any }

        // Create a generator that returns different types based on a boolean
        val gen = Gen.oneOf(intGen, boolGen)

        // Generate a value
        val (value, _) = gen.generate(treeYieldingZeroOnLeftTreeFirst)

        // Value is either Int or Boolean
        expectThat(value).isA<Int>()
    }

    @Test
    fun `flatMap with type-switching function can hit the TODO - using oneOf generator`() {
        val treeThatGeneratesOneOnLeftTree = generateSequence(0L) { it + 1 }
            .first { ValueTree.fromSeed(it).left.value.int(0..1) == 1 }
            .let { ValueTree.fromSeed(it) }

        val multiTypeGen = Gen.oneOf(
            Gen.bool().map { it as Any },
            Gen.int(0..10).map { it as Any },
        )

        // Generate: bool=true -> Gen.int(0..10) -> produces an Int
        val (_, shrinks) = multiTypeGen.generate(treeThatGeneratesOneOnLeftTree)

        println("initial shrinks:")
        shrinks.forEach { println(it) }

        // Take a right shrink (int shrank to a smaller value)
        val firstRightShrink = shrinks.first { it.right.value is Predetermined }
        println("first right shrink:\n$firstRightShrink")
        expectThat(firstRightShrink.right.value).isA<Predetermined>().get { choice }.isA<IntChoice>()

        val (_, nestedShrinks) = multiTypeGen.generate(firstRightShrink)

        // The first nested shrink shrinks BOTH sides:
        // left=predetermined(false), right=predetermined(IntChoice)
        // Now flatMap calls fn(false) -> Gen.bool()
        // Gen.bool() tries to read from right which has IntChoice -> TODO!
        val nestedShrinkTree = nestedShrinks.first()
        println("nested shrink tree:\n$nestedShrinkTree")

        expectDoesNotThrow { multiTypeGen.generate(nestedShrinkTree) }
    }

    @Test
    fun `flatMap with type-switching function can hit the TODO`() {
        // Find a seed where the boolean is true
        val seed = generateSequence(0L) { it + 1 }
            .first { ValueTree.fromSeed(it).left.value.bool() }

        // Create a generator that returns different types based on a boolean
        val multiTypeGen = Gen.bool().flatMap { useInt ->
            if (useInt) Gen.int(0..10) else Gen.bool()
        }

        // Generate: bool=true -> Gen.int(0..10) -> produces an Int
        val (_, shrinks) = multiTypeGen.generate(ValueTree.fromSeed(seed))

        // Take a right shrink (int shrank to a smaller value)
        // This tree has: left=undetermined(true), right=predetermined(IntChoice)
        val rightShrink = shrinks.drop(1).first()
        val (_, nestedShrinks) = multiTypeGen.generate(rightShrink)

        // The first nested shrink shrinks BOTH sides:
        // left=predetermined(false), right=predetermined(IntChoice)
        // Now flatMap calls fn(false) -> Gen.bool()
        // Gen.bool() tries to read from right which has IntChoice -> TODO!
        val nestedShrinkTree = nestedShrinks.first()

        expectThrows<NotImplementedError> { multiTypeGen.generate(nestedShrinkTree) }.message.isNotNull()
            .contains("handle non-")
    }
}


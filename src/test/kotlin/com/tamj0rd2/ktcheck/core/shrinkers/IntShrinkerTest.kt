package com.tamj0rd2.ktcheck.core.shrinkers

import com.tamj0rd2.ktcheck.contracts.repeatTest
import com.tamj0rd2.ktcheck.contracts.skipIteration
import com.tamj0rd2.ktcheck.full
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.all
import strikt.assertions.contains
import strikt.assertions.doesNotContain
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.isLessThan
import strikt.assertions.isNotEmpty
import kotlin.math.absoluteValue
import kotlin.random.Random

class IntShrinkerTest {
    @Test
    fun `10 shrinks correctly`() {
        val shrinks = IntShrinker.shrink(10, 0..10).toList()
        expectThat(shrinks).isEqualTo(listOf(0, 5, 8, 9))
    }

    @Test
    fun `-10 shrinks correctly`() {
        val shrinks = IntShrinker.shrink(-10, -10..0).toList()
        expectThat(shrinks).isEqualTo(listOf(0, -5, -8, -9))
    }

    @Test
    fun `when the original number is equal to the shrink target, does not yield any shrinks`() {
        val range = Int.MIN_VALUE..Int.MAX_VALUE

        repeatTest { seed ->
            val originalNumber = range.random(Random(seed.value))
            val shrinkTarget = originalNumber
            val shrinks = IntShrinker.shrink(originalNumber, range, shrinkTarget).toList()
            expectThat(shrinks).isEmpty()
        }
    }

    @Test
    fun `when the original number is not equal to the shrink target, it yields the shrink target`() {
        val range = -50..50

        repeatTest { seed ->
            val random = Random(seed.value)
            val originalNumber = range.random(random)
            val shrinkTarget = range.minus(originalNumber).random(random)
            val shrinks = IntShrinker.shrink(originalNumber, range, shrinkTarget).toList()
            expectThat(shrinks).isNotEmpty().contains(shrinkTarget)
        }
    }

    @Test
    fun `the original number is not included in shrinks`() {
        val range = -50..50

        repeatTest { seed ->
            val originalNumber = range.random(Random(seed.value))
            val shrinks = IntShrinker.shrink(originalNumber, range).toList()
            expectThat(shrinks).doesNotContain(originalNumber)
        }
    }

    @Test
    fun `shrinks are closer to the shrink target than the original number`() {
        val range = -50..50

        repeatTest { seed ->
            val originalNumber = range.random(Random(seed.value))
            if (originalNumber == IntShrinker.defaultShrinkTarget(range)) skipIteration()

            val shrinks = IntShrinker.shrink(originalNumber, range).toList()
            expectThat(shrinks).isNotEmpty().all {
                get { absoluteValue }.describedAs("shrunk distance from 0").isLessThan(originalNumber.absoluteValue)
            }
        }
    }

    @Test
    fun `shrinks with custom shrink target in positive range`() {
        val shrinks = IntShrinker.shrink(10, 0..10, 5).toList()
        expectThat(shrinks).isEqualTo(listOf(5, 8, 9))
    }

    @Test
    fun `shrinks with custom shrink target in negative range`() {
        val shrinks = IntShrinker.shrink(-10, -20..-10, -15).toList()
        expectThat(shrinks).isEqualTo(listOf(-15, -12, -11))
    }

    @Test
    fun `shrinks with custom shrink target in mixed range`() {
        val shrinks = IntShrinker.shrink(7, -5..10, 3).toList()
        expectThat(shrinks).isEqualTo(listOf(3, 5, 6))
    }

    @Test
    fun `shrink throws if shrink target not in range`() {
        expectThrows<IllegalArgumentException> {
            IntShrinker.shrink(10, 0..10, 20).toList()
        }
    }

    @Test
    fun `shrinking works across the full int range`() {
        expectThat(IntShrinker.shrink(Int.MAX_VALUE, IntRange.full, 0).toList()).isNotEmpty()
        expectThat(IntShrinker.shrink(Int.MIN_VALUE, IntRange.full, 0).toList()).isNotEmpty()
    }
}

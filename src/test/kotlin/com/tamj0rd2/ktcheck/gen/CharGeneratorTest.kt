package com.tamj0rd2.ktcheck.gen

import com.tamj0rd2.ktcheck.gen.Gen.Companion.samples
import com.tamj0rd2.ktcheck.gen.ListGeneratorTest.Companion.generateWithDepthFirstShrinks
import com.tamj0rd2.ktcheck.producer.ProducerTree
import com.tamj0rd2.ktcheck.stats.Counter.Companion.withCounter
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import strikt.api.expectThat
import strikt.assertions.all
import strikt.assertions.doesNotContain
import strikt.assertions.first
import strikt.assertions.isContainedIn
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.isLessThan
import strikt.assertions.isNotEmpty

// todo: most these tests could be made more generic and framed around the oneOfValues generator
class CharGeneratorTest {
    @Nested
    inner class Generation {
        @TestFactory
        fun `can generate a character from a collection`(): List<DynamicTest> {
            val testCases = mapOf(
                "single lowercase char" to listOf('a'),
                "single uppercase char" to listOf('Z'),
                "single digit" to listOf('5'),
                "single special char" to listOf('!'),
                "lowercase range" to ('a'..'z'),
                "uppercase range" to ('A'..'Z'),
                "digit range" to ('0'..'9'),
                "mixed chars" to listOf('a', 'B', '3', '!', ' '),
                "special chars" to listOf('!', '@', '#', '$', '%'),
            )

            return testCases.map { (desc, chars) ->
                DynamicTest.dynamicTest(desc) {
                    Gen.char(chars)
                        .samples()
                        .take(1000)
                        .forEach { expectThat(it).isContainedIn(chars) }
                }
            }
        }

        @Test
        fun `generates a variety of characters over multiple runs`() {
            withCounter {
                Gen.char(('a'..'z').toList()).samples().take(100000).forEach { value ->
                    collect(
                        when (value) {
                            in 'a'..'i' -> "a-i"
                            in 'j'..'r' -> "j-r"
                            in 's'..'z' -> "s-z"
                            else -> "other"
                        }
                    )
                }
            }.checkPercentages(
                mapOf(
                    "a-i" to 30.0,
                    "j-r" to 30.0,
                    "s-z" to 30.0
                )
            )
        }

        @Test
        fun `using the same seed generates the same values`() {
            val seed = 12345L
            val gen = Gen.char(('a'..'z').toList())
            val firstRun = gen.samples(seed).take(100).toList()
            val secondRun = gen.samples(seed).take(100).toList()
            expectThat(secondRun).isEqualTo(firstRun)
        }
    }

    @Nested
    inner class Shrinking {
        @Test
        fun `shrinks toward the smallest character, smallest first`() {
            val chars = 'a'..'d'
            val gen = Gen.char(chars)
            val tree = ProducerTree.new().withValue(chars.indexOf('d'))

            val (originalValue, shrunkValues) = gen.generateWithDepthFirstShrinks(tree)
            expectThat(originalValue).isEqualTo('d')
            // index 0, 2, 1
            expectThat(shrunkValues).isEqualTo(listOf('a', 'c', 'b'))
        }

        @Test
        fun `if the smallest character was generated, it won't yield any shrinks`() {
            val chars = 'a'..'d'
            val gen = Gen.char(chars)
            val tree = ProducerTree.new().withValue(chars.indexOf('a'))

            val (originalValue, shrunkValues) = gen.generateWithDepthFirstShrinks(tree)
            expectThat(originalValue).isEqualTo('a')
            expectThat(shrunkValues).isEmpty()
        }

        @Test
        fun `shrinks for the non-lowest character always yield the smallest character`() {
            val chars = ('a'..'z').toList()
            val gen = Gen.char(chars)

            Gen.tree().samples().map { gen.generateWithDepthFirstShrinks(it) }
                .filter { (originalValue) -> originalValue != chars.first() }
                .take(100)
                .forEach { (_, shrunkValues) -> expectThat(shrunkValues).first().isEqualTo(chars.first()) }
        }

        @Test
        fun `the original generated character is not included in shrinks`() {
            val chars = ('a'..'z').toList()
            val gen = Gen.char(chars)

            Gen.tree().samples().map { gen.generateWithDepthFirstShrinks(it) }
                .take(100)
                .forEach { (originalValue, shrunkValues) ->
                    expectThat(shrunkValues).doesNotContain(originalValue)
                }
        }

        @Test
        fun `shrinks are closer to the lowest character than the original generated character`() {
            val chars = ('a'..'z').toList()
            val gen = Gen.char(chars)
            val lowestChar = chars.first()

            Gen.tree().samples().map { gen.generateWithDepthFirstShrinks(it) }
                .filter { (originalValue) -> originalValue != lowestChar }
                .take(100)
                .forEach { (originalValue, shrunkValues) ->
                    val originalIndex = chars.indexOf(originalValue)

                    expectThat(shrunkValues).isNotEmpty().all {
                        get { chars.indexOf(this) }
                            .describedAs("shrunk index (closer to lowest)")
                            .isLessThan(originalIndex)
                    }
                }
        }
    }
}

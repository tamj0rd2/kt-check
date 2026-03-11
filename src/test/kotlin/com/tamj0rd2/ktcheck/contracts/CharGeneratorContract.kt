package com.tamj0rd2.ktcheck.contracts

import com.tamj0rd2.ktcheck.Counter.Companion.withCounter
import org.junit.jupiter.api.DynamicTest
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

internal interface CharGeneratorContract : BaseContract {
    // todo: once the default char set exists, remove 'a'..'z' defined here
    override val exampleGen get() = char('a'..'z')

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
                char(chars)
                    .samples()
                    .take(1000)
                    .forEach { expectThat(it).isContainedIn(chars) }
            }
        }
    }

    @Test
    fun `generates a variety of characters over multiple runs`() {
        val chars = 'a'..'z'
        withCounter {
            char(chars).samples().take(100000).forEach { collect(it) }
        }.checkPercentages(chars.associateWith { (100.0 / chars.count()) - 1 })
    }

    @Test
    fun `shrinks for non-minimal values always yield the minimal value`() {
        val chars = 'a'..'z'
        val gen = char(chars)

        repeatTest { seed ->
            val result = gen.generate(tree(seed))
            if (result.value == chars.first()) skipIteration()
            expectThat(result).shrunkValues.first().isEqualTo(chars.first())
        }
    }

    @Test
    fun `shrinks never yield the value being shrunk`() {
        val gen = char('a'..'z')

        repeatTest { seed ->
            val result = gen.generate(tree(seed))
            expectThat(result).shrunkValues.doesNotContain(result.value)
        }
    }

    @Test
    fun `when the minimal value is generated, no shrinks are yielded`() {
        val chars = 'a'..'z'

        repeatTest {
            val result = char(chars).generate(tree(it))
            if (result.value != chars.first()) skipIteration()
            expectThat(result).shrunkValues.isEmpty()
        }
    }

    @Test
    fun `shrinks are closer to the minimal character than the original generated character`() {
        val chars = 'a'..'z'
        val minimal = chars.first()
        val gen = char(chars)

        repeatTest { seed ->
            val result = gen.generate(tree(seed))
            if (result.value == minimal) skipIteration()

            val originalIndex = chars.indexOf(result.value)

            expectThat(result).shrunkValues.isNotEmpty().all {
                get { chars.indexOf(this) }
                    .describedAs("shrunk index (closer to lowest)")
                    .isLessThan(originalIndex)
            }
        }
    }
}

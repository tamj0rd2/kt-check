package com.tamj0rd2.ktcheck.contracts


import com.tamj0rd2.ktcheck.Gen
import com.tamj0rd2.ktcheck.TestConfig
import com.tamj0rd2.ktcheck.string
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

// todo: this should have all the same properties as Int, as it's just int with a map applied...
internal interface CharGeneratorContract : BaseContract, CanProduceStringsContract {
    override val exampleGen get() = char()

    override fun newStringLikeGenerator(sizeRange: IntRange): Gen<String> = char().string(sizeRange)

    @TestFactory
    fun `can generate a character within a range`(): List<DynamicTest> {
        val testCases = mapOf(
            "single lowercase char" to 'a'..'a',
            "uppercase range" to 'A'..'Z',
            "digit range" to '0'..'9',
            "mixed range" to '!'..'Z',
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

    // todo: very similar to the IntGenerator test... which makes sense, given it's just int, mapped
    //  it should be possible to express this property regardless of the kind of generator it is :)
    @Test
    fun `generates a variety of characters over multiple runs`() {
        val chars = 'a'..'z'
        val seenValues = char(chars).samples().take(TestConfig.DEFAULT_ITERATIONS).toSet()
        expectThat(seenValues).isEqualTo(chars.toSet())
    }

    @Test
    fun `shrinks for non-minimal values always yield the minimal value`() {
        val chars = 'a'..'z'
        val gen = char(chars)

        repeatTest { seed ->
            val result = gen.generate(ctx(seed))
            if (result.value == chars.first()) skipIteration()
            expectThat(result).shrunkValues.first().isEqualTo(chars.first())
        }
    }

    @Test
    fun `shrinks never yield the value being shrunk`() {
        val gen = char('a'..'z')

        repeatTest { seed ->
            val result = gen.generate(ctx(seed))
            expectThat(result).shrunkValues.doesNotContain(result.value)
        }
    }

    @Test
    fun `when the minimal value is generated, no shrinks are yielded`() {
        val chars = 'a'..'z'

        repeatTest {
            val result = char(chars).generate(ctx(it))
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
            val result = gen.generate(ctx(seed))
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

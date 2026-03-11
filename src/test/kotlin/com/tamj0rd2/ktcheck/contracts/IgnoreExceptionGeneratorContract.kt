package com.tamj0rd2.ktcheck.contracts

import com.tamj0rd2.ktcheck.Counter.Companion.withCounter
import com.tamj0rd2.ktcheck.GenerationException.FilterLimitReached
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertTimeoutPreemptively
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.all
import strikt.assertions.isEqualTo
import strikt.assertions.isLessThanOrEqualTo
import strikt.assertions.isNotEqualTo
import java.time.Duration


internal interface IgnoreExceptionGeneratorContract : BaseContract {
    private class TestException : Exception()

    override val exampleGen
        get() = int(1..3)
            .map {
                when (it) {
                    1 -> throw TestException()
                    else -> it
                }
            }
            .ignoreExceptions(TestException::class)

    @Test
    fun `can ignore exceptions in generated values and shrinks`() {
        val possiblyThrowingGen = int(1..3)
            .map {
                when (it) {
                    1 -> throw TestException()
                    else -> it
                }
            }
            .ignoreExceptions(TestException::class)

        withCounter {
            repeatTest { seed ->
                val result = possiblyThrowingGen.generate(tree(seed))
                expectThat(result).value.isNotEqualTo(1)
                expectThat(result).shrunkValues.all { isNotEqualTo(1) }
                collect("has-shrinks", result.shrunkValues.isNotEmpty())
            }
        }.checkPercentages("has-shrinks", mapOf(true to 45.0))

        // todo: add some - deeply shrunk values are finite function. call it above.
        possiblyThrowingGen.expectGenerationAndShrinkingToEventuallyComplete()
    }

    @Test
    fun `shrinks are never greater than the originally generated value`() {
        withCounter {
            val gen = int(1..10)
                .map {
                    when (it) {
                        1 -> throw TestException()
                        else -> it
                    }
                }
                .ignoreExceptions(TestException::class)

            repeatTest { seed ->
                val result = gen.generate(tree(seed))
                if (result.value <= 2) skipIteration()
                expectThat(result).shrunkValues.all { isLessThanOrEqualTo(result.value) }
                collect("has-shrinks", result.shrunkValues.any())
            }
        }.checkPercentages("has-shrinks", mapOf(true to 10.0))
    }

    @Test
    fun `if an ignored exception is thrown more times than the threshold, throws an error`() {
        class IgnoredException : Exception()

        val throwingGen = bool()
            .map { throw IgnoredException() }
            .ignoreExceptions(IgnoredException::class)

        assertTimeoutPreemptively(Duration.ofSeconds(1)) {
            expectThrows<FilterLimitReached> { throwingGen.samples().first() }
        }
    }

    @Test
    fun `if a non-ignored exception is thrown, it propagates`() {
        class IgnoredException : Exception()
        class NotIgnoredException : Exception()

        val throwingGen = bool()
            .map { throw NotIgnoredException() }
            .ignoreExceptions(IgnoredException::class)

        expectThrows<NotIgnoredException> { throwingGen.generate() }
    }

    @Test
    fun `can ignore multiple exceptions types`() {
        class IgnoredException1 : Exception()
        class IgnoredException2 : Exception()

        val possiblyThrowingGen = int(1..3)
            .map {
                when (it) {
                    1 -> throw IgnoredException1()
                    2 -> throw IgnoredException2()
                    else -> it
                }
            }
            .ignoreExceptions(IgnoredException1::class)
            .ignoreExceptions(IgnoredException2::class)

        repeatTest { seed ->
            val result = possiblyThrowingGen.generate(tree(seed))
            expectThat(result).value.isEqualTo(3)
        }
    }
}

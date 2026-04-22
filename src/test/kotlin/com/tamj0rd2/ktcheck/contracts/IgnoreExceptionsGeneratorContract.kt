package com.tamj0rd2.ktcheck.contracts

import com.tamj0rd2.ktcheck.GenerationException.FilterLimitReached
import com.tamj0rd2.ktcheck.stats.Percentage.Companion.percent
import com.tamj0rd2.ktcheck.stats.withLabelledCounter
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertTimeoutPreemptively
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.all
import strikt.assertions.isEqualTo
import strikt.assertions.isLessThanOrEqualTo
import strikt.assertions.isNotEqualTo
import java.time.Duration

internal interface IgnoreExceptionsGeneratorContract : BaseContract {
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

        withLabelledCounter {
            repeatTest { seed ->
                val (originalValue, shrinks) = possiblyThrowingGen.collectShrunkValues(seed)
                expectThat(originalValue).isNotEqualTo(1)
                expectThat(shrinks).describedAs("shrinks of $originalValue") all { isNotEqualTo(1) }
                collect("has-shrinks", shrinks.isNotEmpty())
            }
        }.checkPercentages("has-shrinks", mapOf(true to 40.percent))

        possiblyThrowingGen.expectGenerationAndShrinkingToEventuallyComplete()
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

        expectThrows<NotIgnoredException> { throwingGen.generate(ctx()) }
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
            val result = possiblyThrowingGen.generate(ctx(seed))
            expectThat(result).value.isEqualTo(3)
        }
    }

    @Test
    fun `shrinks are never greater than the originally generated value`() {
        withLabelledCounter {
            val gen = int(1..10)
                .map {
                    when (it) {
                        1 -> throw TestException()
                        else -> it
                    }
                }
                .ignoreExceptions(TestException::class)

            repeatTest { seed ->
                val (originalValue, shrinks) = gen.collectShrunkValues(seed)
                if (originalValue <= 2) skipIteration()
                expectThat(shrinks).describedAs("shrinks of $originalValue").all { isLessThanOrEqualTo(originalValue) }
                collect("has-shrinks", shrinks.any())
            }
        }.checkPercentages("has-shrinks", mapOf(true to 10.percent))
    }
}

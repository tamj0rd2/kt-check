package com.tamj0rd2.ktcheck.contracts

import com.tamj0rd2.ktcheck.GenerationException.FilterLimitReached
import com.tamj0rd2.ktcheck.core.Seed
import com.tamj0rd2.ktcheck.stats.withLabelledCounter
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertTimeoutPreemptively
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.all
import strikt.assertions.isEqualTo
import strikt.assertions.isLessThanOrEqualTo
import strikt.assertions.isNotEqualTo
import strikt.assertions.isNull
import java.time.Duration

internal interface IgnoreExceptionsGeneratorContract : BaseContract {
    private class TestException : Exception()

    // todo: implement edge cases for this generator
    override val genSupportsEdgeCases: Boolean get() = false

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
            fun checkResult(result: GenResults<Int>?) {
                if (result == null) skipIteration()
                expectThat(result).value.isNotEqualTo(1)
                expectThat(result).shrunkValues.all { isNotEqualTo(1) }
                collect("has-shrinks", result.shrunkValues.isNotEmpty())
            }

            repeatTest { seed -> checkResult(possiblyThrowingGen.generate(tree(seed))) }
            if (genSupportsEdgeCases) repeatTest { seed -> checkResult(possiblyThrowingGen.edgeCase(seed)) }
        }.checkPercentages("has-shrinks", mapOf(true to 40.0))

        // todo: add some - deeply shrunk values are finite function. call it above.
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

        expectThrows<NotIgnoredException> { throwingGen.generate(tree()) }
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

            fun checkResult(result: GenResults<Int>?) {
                if (result == null) skipIteration()
                if (result.value <= 2) skipIteration()
                expectThat(result).shrunkValues.all { isLessThanOrEqualTo(result.value) }
                collect("has-shrinks", result.shrunkValues.any())
            }

            repeatTest { seed -> checkResult(gen.generate(tree(seed))) }
            if (genSupportsEdgeCases) repeatTest { seed -> checkResult(gen.edgeCase(seed)) }
        }.checkPercentages("has-shrinks", mapOf(true to 10.0))
    }

    @Test
    fun `does not produce any edge cases`() {
        expectThat(exampleGen.edgeCase(Seed.random())).isNull()
    }

    @Test
    fun `ignoreExceptions propagates edge cases from underlying generator`() {
        runIfGenSupportsEdgeCases()
        TODO("write this test")
    }
}

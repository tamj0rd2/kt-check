package com.tamj0rd2.ktcheck.genv2

import com.tamj0rd2.ktcheck.genv2.Gen.Companion.samples
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.all
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse

class FilterGeneratorTest {
    @Test
    fun `can filter generated values`() {
        val gen = Gen.int(1..10).filter { it % 2 == 0 }

        gen.samples().take(100).onEach { expectThat(it % 2).isEqualTo(0) }.toList()
    }

    @Test
    fun `throws if the filter threshold is exceeded`() {
        val gen = Gen.int(1..10).filter { it > 10 }

        expectThrows<FilterLimitReached> { gen.samples().first() }
    }

    @Test
    fun `can ignore exceptions in generated values`() {
        class TestException : Exception()

        val possiblyThrowingGen = Gen.boolean()
            .map { if (it) throw TestException() else false }
            .ignoreExceptions(TestException::class)

        val values = possiblyThrowingGen.samples().take(100).toList()
        expectThat(values).all { isFalse() }
    }

    @Test
    fun `if an ignored exception is thrown more times than the threshold, throws an error`() {
        class IgnoredException : Exception()

        val throwingGen = Gen.boolean()
            .map { throw IgnoredException() }
            .ignoreExceptions(IgnoredException::class)

        expectThrows<FilterLimitReached> { throwingGen.samples().first() }
    }

    @Test
    fun `if a non-ignored exception is thrown, it propagates`() {
        class IgnoredException : Exception()
        class NotIgnoredException : Exception()

        val throwingGen = Gen.boolean()
            .map { throw NotIgnoredException() }
            .ignoreExceptions(IgnoredException::class)

        expectThrows<NotIgnoredException> { throwingGen.samples().first() }
    }

    @Test
    fun `can ignore multiple exceptions types`() {
        class IgnoredException1 : Exception()
        class IgnoredException2 : Exception()

        val possiblyThrowingGen = Gen.int(1..3)
            .map {
                when (it) {
                    1 -> throw IgnoredException1()
                    2 -> throw IgnoredException2()
                    else -> it
                }
            }
            .ignoreExceptions(IgnoredException1::class)
            .ignoreExceptions(IgnoredException2::class)

        val values = possiblyThrowingGen.samples().take(100).toList()
        expectThat(values).all { isEqualTo(3) }
    }
}

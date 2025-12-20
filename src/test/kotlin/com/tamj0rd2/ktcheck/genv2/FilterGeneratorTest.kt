package com.tamj0rd2.ktcheck.genv2

import org.junit.jupiter.api.Test
import strikt.api.expectThrows

class FilterGeneratorTest {
    @Test
    fun `can filter generated values`() {
        val gen = Gen.int(1..10).filter { it % 2 == 0 }
        forAll(gen) { it % 2 == 0 }
    }

    @Test
    fun `can ignore exceptions in generated values`() {
        class TestException : Throwable()

        val possiblyThrowingGen = Gen.boolean().map { if (it) throw TestException() else false }
        forAll(possiblyThrowingGen.ignoreExceptions(TestException::class)) { true }
    }

    @Test
    fun `if an ignored exception is thrown more times than the threshold, throws an error`() {
        class IgnoredException : Throwable()

        val throwingGen = Gen.boolean().map { throw IgnoredException() }

        expectThrows<ExceptionLimitReached> {
            forAll(throwingGen.ignoreExceptions(IgnoredException::class)) { true }
        }
    }

    @Test
    fun `if a non-ignored exception is thrown, it propagates`() {
        class IgnoredException : Throwable()
        class NotIgnoredException : Throwable()

        val throwingGen = Gen.boolean().map { throw NotIgnoredException() }

        expectThrows<NotIgnoredException> {
            forAll(throwingGen.ignoreExceptions(IgnoredException::class)) { true }
        }
    }
}

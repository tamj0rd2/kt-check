package com.tamj0rd2.ktcheck.gen

import com.tamj0rd2.ktcheck.gen.Gen.Companion.samples
import com.tamj0rd2.ktcheck.gen.GenTests.Companion.expectGenerationAndShrinkingToEventuallyComplete
import com.tamj0rd2.ktcheck.producer.PredeterminedValue
import com.tamj0rd2.ktcheck.producer.ProducerTree
import com.tamj0rd2.ktcheck.producer.ProducerTreeDsl.Companion.producerTree
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import strikt.api.Assertion
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.all
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isNotEmpty
import strikt.assertions.isNotEqualTo


class FilterGeneratorTest {
    @Nested
    inner class PredicateFiltering {
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
    }

    @Nested
    inner class IgnoreExceptions {
        @Test
        fun `can ignore exceptions in generated values`() {
            class TestException : Exception()

            val possiblyThrowingGen = Gen.bool()
                .map { if (it) throw TestException() else false }
                .ignoreExceptions(TestException::class)

            val values = possiblyThrowingGen.samples().take(100).toList()
            expectThat(values).all { isFalse() }
        }

        @Test
        fun `ignore exceptions doesn't produce shrinks that would cause the exception, which would otherwise lead to infinite shrinking`() {
            class TestException : Exception()

            val possiblyThrowingGen = Gen.int(1..3)
                .map {
                    when (it) {
                        1 -> throw TestException()
                        else -> it
                    }
                }
                .ignoreExceptions(TestException::class)

            val tree = producerTree {
                left(3)
                right {
                    left(2)
                }
            }

            val (value, shrunkTrees) = possiblyThrowingGen.generate(tree, GenMode.Initial)
            expectThat(value).isEqualTo(3)
            expectThat(shrunkTrees.toList())
                .describedAs("shrunk trees")
                .isNotEmpty()
                .all { leftProducer.isNotEqualTo(PredeterminedValue(1)) }
            possiblyThrowingGen.expectGenerationAndShrinkingToEventuallyComplete(shrunkValueRequired = false)
        }

        @Test
        fun `if an ignored exception is thrown more times than the threshold, throws an error`() {
            class IgnoredException : Exception()

            val throwingGen = Gen.bool()
                .map { throw IgnoredException() }
                .ignoreExceptions(IgnoredException::class)

            expectThrows<FilterLimitReached> { throwingGen.samples().first() }
        }

        @Test
        fun `if a non-ignored exception is thrown, it propagates`() {
            class IgnoredException : Exception()
            class NotIgnoredException : Exception()

            val throwingGen = Gen.bool()
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

    private val Assertion.Builder<ProducerTree>.leftProducer get() = get { left.producer }
}

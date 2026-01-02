package com.tamj0rd2.ktcheck.genv2

import com.tamj0rd2.ktcheck.stats.Counter
import com.tamj0rd2.ktcheck.stats.Counter.Companion.withCounter
import com.tamj0rd2.ktcheck.testing.HardcodedTestConfig
import com.tamj0rd2.ktcheck.testing.TestConfig
import com.tamj0rd2.ktcheck.testing.TestReporter
import com.tamj0rd2.ktcheck.testing.TestResult
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.isEqualTo
import strikt.assertions.isLessThan
import strikt.assertions.isLessThanOrEqualTo

class Shrinking {

    @Test
    fun `can shrink a number`() = testShrinking(
        test = { testConfig -> forAll(testConfig, Gen.int(1..100)) { false } },
        didShrinkCorrectly = { it.single() == 1 },
    )

    @Test
    fun `can shrink a list`() = testShrinking(
        test = { testConfig -> forAll(testConfig, Gen.int().list()) { false } },
        didShrinkCorrectly = { it.single() == emptyList<Int>() },
    )

    @OptIn(HardcodedTestConfig::class)
    @Test
    @Disabled
    fun `lengthList repro`() {
        val gen = Gen.int(0..1000).list(1..100)
        expectThrows<AssertionError> {
            checkAll(TestConfig().replay(-781415703, 1), gen) { ls -> expectThat(ls.max()).isLessThan(900) }
        }
    }

    // based on https://github.com/jlink/shrinking-challenge/tree/main/challenges
    @Nested
    inner class Challenges {
        @Test
        fun reverse() = testShrinking(
            test = { testConfig ->
                checkAll(testConfig, Gen.int().list()) {
                    expectThat(it.reversed()).isEqualTo(it)
                }
            },
            didShrinkCorrectly = { it.single() in setOf(listOf(0, 1), listOf(0, -1)) },
        )

        @Test
        fun nestedLists() {
            testShrinking(
                test = { testConfig ->
                    val gen = Gen.int(Int.MIN_VALUE..Int.MAX_VALUE).list().list()
                    checkAll(testConfig, gen) { listOfLists ->
                        expectThat(listOfLists.sumOf { it.size }).isLessThanOrEqualTo(10)
                    }
                },
                didShrinkCorrectly = { it.single() == listOf(listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)) },
                minConfidence = 85.0,
            )
        }

        @Test
        @Disabled
        fun lengthList() {
            testShrinking(
                test = { testConfig ->
                    val gen = Gen.int(0..1000).list(1..100)
                    checkAll(testConfig, gen) { ls -> expectThat(ls.max()).isLessThan(900) }
                },
                didShrinkCorrectly = { it.single() == listOf(900) },
                // Most of the time the shrinker provides a much smaller counter example, but very rarely the minimal one.
                minConfidence = 5.0,
            )
        }
    }

    private fun testShrinking(
        testConfig: TestConfig = TestConfig().withIterations(500),
        test: (TestConfig) -> Unit,
        didShrinkCorrectly: (List<Any?>) -> Boolean,
        minConfidence: Double = 100.0,
        categoriseBadShrinks: Counter.(List<Any?>, List<Any?>) -> Unit = { _, _ -> },
    ) {
        val exceptionsWithBadShrinks = mutableListOf<PropertyFalsifiedException>()

        val counter = withCounter {
            checkAll(testConfig, Gen.long()) { seed ->
                val exception = expectThrows<PropertyFalsifiedException> {
                    test(TestConfig().withSeed(seed).withReporter(NoOpTestReporter))
                }.subject

                val originalArgs = exception.originalResult.args
                val shrunkArgs = exception.shrunkResult.args

                val fullyShrunk = didShrinkCorrectly(shrunkArgs)
                collect("shrunk fully", fullyShrunk)

                if (!fullyShrunk) {
                    exceptionsWithBadShrinks.add(exception)
                    categoriseBadShrinks(originalArgs, shrunkArgs)
                }
            }
        }

        if (exceptionsWithBadShrinks.isNotEmpty()) {
            println("\nSome bad shrinks encountered:")

            exceptionsWithBadShrinks
                .sortedBy { it.shrunkResult.args.toString().length }
                .take(5)
                .forEach { println(it.asBadShrinkExample()) }
        }

        counter.checkPercentages("shrunk fully", mapOf(true to minConfidence))
    }

    private fun PropertyFalsifiedException.asBadShrinkExample(): String {
        val shortenedOriginalArgs = originalResult.args.toString().let {
            if (it.length > 100) it.take(100) + " (remaining args truncated)" else it
        }

        return """
            |----
            |Seed: $seed
            |Iteration: $iteration
            |Original args: $shortenedOriginalArgs
            |Shrunk args: ${shrunkResult.args}
            """.trimMargin()
    }

    private object NoOpTestReporter : TestReporter {
        override fun reportSuccess(iterations: Int) {}
        override fun reportFailure(
            seed: Long,
            failedIteration: Int,
            originalFailure: TestResult.Failure,
            shrunkFailure: TestResult.Failure,
        ) {
        }
    }
}

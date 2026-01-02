package com.tamj0rd2.ktcheck.genv2

import com.tamj0rd2.ktcheck.stats.Counter
import com.tamj0rd2.ktcheck.stats.Counter.Companion.withCounter
import com.tamj0rd2.ktcheck.testing.TestByBool
import com.tamj0rd2.ktcheck.testing.TestConfig
import com.tamj0rd2.ktcheck.testing.TestReporter
import com.tamj0rd2.ktcheck.testing.TestResult
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import strikt.api.expectThrows

class Shrinking {

    @Test
    fun `can shrink a number`() = testShrinking(
        gen = Gen.int(1..100),
        test = { false },
        didShrinkCorrectly = { it == 1 },
    )

    @Test
    fun `can shrink a list`() = testShrinking(
        gen = Gen.int().list(),
        test = { false },
        didShrinkCorrectly = { it.isEmpty() },
    )

    // based on https://github.com/jlink/shrinking-challenge/tree/main/challenges
    @Nested
    inner class Challenges {
        @Test
        fun reverse() = testShrinking(
            gen = Gen.int().list(),
            test = { it.reversed() == it },
            didShrinkCorrectly = { it in setOf(listOf(0, 1), listOf(0, -1)) },
        )

        @Test
        fun nestedLists() {
            testShrinking(
                gen = Gen.int(Int.MIN_VALUE..Int.MAX_VALUE).list().list(),
                test = { listOfLists -> listOfLists.sumOf { it.size } <= 10 },
                // todo: although it works, it'd may be nice if later I can make it normalise the list to a single list.
                didShrinkCorrectly = { listOfLists ->
                    val flattened = listOfLists.flatten()
                    flattened.size == 11 && flattened.all { it == 0 }
                },
            )
        }

        @Test
        fun lengthList() {
            testShrinking(
                gen = Gen.int(0..1000).list(1..100),
                test = { it.max() < 900 },
                didShrinkCorrectly = { it == listOf(900) },
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> testShrinking(
        testConfig: TestConfig = TestConfig().withIterations(500),
        gen: Gen<T>,
        test: TestByBool<T>,
        didShrinkCorrectly: (T) -> Boolean,
        minConfidence: Double = 100.0,
        categoriseShrinks: Counter.(Boolean, T, T) -> Unit = { _, _, _ -> },
    ) {
        val exceptionsWithBadShrinks = mutableListOf<PropertyFalsifiedException>()

        val counter = withCounter {
            checkAll(testConfig, Gen.long()) { seed ->
                val exception = expectThrows<PropertyFalsifiedException> {
                    forAll(TestConfig().withSeed(seed).withReporter(NoOpTestReporter), gen, test)
                }.subject

                val originalArgs = exception.originalResult.input as T
                val shrunkArgs = exception.shrunkResult.input as T

                val fullyShrunk = didShrinkCorrectly(shrunkArgs)
                collect("shrunk fully", fullyShrunk)
                categoriseShrinks(fullyShrunk, originalArgs, shrunkArgs)

                if (fullyShrunk) collect("fully shrunk args", shrunkArgs.toString())
                else exceptionsWithBadShrinks.add(exception)
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
            originalFailure: TestResult.Failure<*>,
            shrunkFailure: TestResult.Failure<*>,
        ) {
        }
    }
}

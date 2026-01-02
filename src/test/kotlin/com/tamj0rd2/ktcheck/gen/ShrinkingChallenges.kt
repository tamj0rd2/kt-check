package com.tamj0rd2.ktcheck.gen

import com.tamj0rd2.ktcheck.stats.Counter
import com.tamj0rd2.ktcheck.stats.Counter.Companion.withCounter
import com.tamj0rd2.ktcheck.testing.PropertyFalsifiedException
import com.tamj0rd2.ktcheck.testing.TestByBool
import com.tamj0rd2.ktcheck.testing.TestConfig
import com.tamj0rd2.ktcheck.testing.TestReporter
import com.tamj0rd2.ktcheck.testing.checkAll
import com.tamj0rd2.ktcheck.testing.forAll
import org.junit.jupiter.api.Test
import strikt.api.expectThrows

// based on https://github.com/jlink/shrinking-challenge/tree/main/challenges
class ShrinkingChallenges {
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

                @Suppress("UNCHECKED_CAST")
                val originalArgs = exception.originalResult.input as T

                @Suppress("UNCHECKED_CAST")
                val shrunkArgs = exception.shrunkResult.input as T

                val fullyShrunk = didShrinkCorrectly(shrunkArgs)
                collect("fully shrunk", fullyShrunk)

                if (fullyShrunk) {
                    collect("fully shrunk steps", exception.shrinkSteps.bucket(size = 50))
                    collect("fully shrunk args", shrunkArgs.toString())
                } else {
                    exceptionsWithBadShrinks.add(exception)
                }

                categoriseShrinks(fullyShrunk, originalArgs, shrunkArgs)
            }
        }

        if (exceptionsWithBadShrinks.isNotEmpty()) {
            println("\nSome bad shrinks encountered:")

            exceptionsWithBadShrinks
                .sortedBy { it.shrunkResult.args.toString().length }
                .take(5)
                .forEach { println(it.asBadShrinkExample()) }
        }

        counter.checkPercentages("fully shrunk", mapOf(true to minConfidence))
    }

    private fun Int.bucket(size: Int): String {
        val lowerBound = (this / size) * size
        val upperBound = lowerBound + size
        return "$lowerBound-$upperBound"
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
        override fun reportFailure(exception: PropertyFalsifiedException) {}
    }
}

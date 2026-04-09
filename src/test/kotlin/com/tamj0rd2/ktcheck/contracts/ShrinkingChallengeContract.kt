package com.tamj0rd2.ktcheck.contracts

import com.tamj0rd2.ktcheck.BooleanProperty
import com.tamj0rd2.ktcheck.Gen
import com.tamj0rd2.ktcheck.GenBuilders
import com.tamj0rd2.ktcheck.PropertyFalsifiedException
import com.tamj0rd2.ktcheck.TestConfig
import com.tamj0rd2.ktcheck.core.tuple
import com.tamj0rd2.ktcheck.forAll
import com.tamj0rd2.ktcheck.positive
import com.tamj0rd2.ktcheck.stats.Percentage.Companion.percent
import com.tamj0rd2.ktcheck.stats.withLabelledCounter
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.time.Duration
import kotlin.math.abs

// based on https://github.com/jlink/shrinking-challenge/tree/main/challenges
internal interface ShrinkingChallengeContract : GenBuilders {
    // can falsify by producing duplicates within the list and shrinking the in a way that maintains duplication
    @Test
    fun deletion() {
        // todo: for this to fail, list needs to produce duplicates.
        //  can raise the likelihood by causing elements of the list to be edge cases.
        testShrinking(
            gen = zip(int().list(), int(0..10)).filter { (list, index) -> index < list.size },
            test = { (list, index) ->
                val element = list[index]
                element !in list.toMutableList().apply { remove(element) }
            },
            didShrinkCorrectly = { it == tuple(listOf(0, 0), 0) },
        )
    }

    // can falsify by producing duplicates and shrinking them in a way that maintains the duplication
    @Test
    fun `difference must not be zero`() {
        testShrinking(
            gen = zip(int(IntRange.positive), int(IntRange.positive)),
            test = { (a, b) -> a < 10 || abs(a - b) != 0 },
            didShrinkCorrectly = { it == tuple(10, 10) },
        )
    }

    @Test
    fun `difference must not be small`() {
        testShrinking(
            gen = zip(int(IntRange.positive), int(IntRange.positive)),
            test = { (a, b) -> a < 10 || abs(a - b) !in 1..4 },
            didShrinkCorrectly = { it == tuple(10, 6) },
        )
    }

    // can falsify by producing range.max and range.max-1 and shrinking them in a way that maintains the distance
    @Test
    fun `difference must not be one`() {
        testShrinking(
            gen = zip(int(IntRange.positive), int(IntRange.positive)),
            test = { (a, b) -> a < 10 || abs(a - b) != 1 },
            didShrinkCorrectly = { it == tuple(10, 9) },
        )
    }

    @Test
    fun distinct() {
        testShrinking(
            gen = int().list(),
            test = { it.distinct().size < 3 },
            didShrinkCorrectly = { it.toSet() in setOf(setOf(0, 1, 2), setOf(0, -1, -2), setOf(0, 1, -1)) },
        )
    }

    @Test
    fun `large union list`() {
        testShrinking(
            gen = int().list(0..4).list(0..4),
            test = { it.flatten().toSet().size <= 4 },
            didShrinkCorrectly = { list ->
                val flattened = list.flatten()
                flattened.size == 5 && flattened.all { it in -4..4 }
            },
        )
    }

    @Test
    fun `length list`() {
        testShrinking(
            gen = int(0..1000).list(1..100),
            test = { it.max() < 900 },
            didShrinkCorrectly = { it == listOf(900) },
        )
    }

    @Test
    fun `nested lists`() {
        testShrinking(
            gen = int(Int.MIN_VALUE..Int.MAX_VALUE).list().list(),
            test = { listOfLists -> listOfLists.sumOf { it.size } <= 10 },
            // todo: although it works, it'd may be nice if later I can make it normalise the list to a single list.
            didShrinkCorrectly = { listOfLists ->
                val flattened = listOfLists.flatten()
                flattened.size == 11 && flattened.all { it == 0 }
            },
        )
    }

    @Test
    fun reverse() = testShrinking(
        gen = int().list(),
        test = { it.reversed() == it },
        didShrinkCorrectly = { it in setOf(listOf(0, 1), listOf(1, 0), listOf(-1, 0), listOf(0, -1)) },
    )

    private fun <T> testShrinking(
        testConfig: TestConfig = TestConfig(),
        gen: Gen<T>,
        test: BooleanProperty<T>,
        didShrinkCorrectly: (T) -> Boolean,
    ) {
        val exceptionsWithBadShrinks = mutableListOf<PropertyFalsifiedException>()

        val counter = withLabelledCounter {
            repeatTest(testConfig, timeout = Duration.ofSeconds(5)) { seed ->
                val exception = runCatching {
                    forAll(
                        config = TestConfig()
                            .withSeed(seed.value)
                            .withoutReporting()
                            .printShrinkSteps(testConfig.printShrinkSteps),
                        gen = gen,
                        property = test
                    )
                }.exceptionOrNull() ?: fail("the property was not falsified")

                if (exception !is PropertyFalsifiedException) {
                    fail("property failed due to uncaught exception", exception)
                }

                @Suppress("UNCHECKED_CAST")
                val shrunkArgs = exception.smallest.input as T

                val fullyShrunk = didShrinkCorrectly(shrunkArgs)
                collect("fully shrunk", fullyShrunk)

                if (fullyShrunk) {
                    collect("fully shrunk steps", exception.shrinkSteps.bucket(size = 50))
                    collect("fully shrunk args", shrunkArgs.toString())
                } else {
                    exceptionsWithBadShrinks.add(exception)
                }
            }
        }

        if (exceptionsWithBadShrinks.isNotEmpty()) {
            println("\nSome bad shrinks encountered:")

            exceptionsWithBadShrinks
                .distinctBy { "${it.seed}_${it.iteration}" }
                .sortedBy { it.smallest.input.toString().length }
                .take(5)
                .forEach { println(it.asBadShrinkExample(testConfig)) }
        }

        counter.checkPercentages("fully shrunk", mapOf(true to 100.percent))
    }

    private fun Int.bucket(size: Int): String {
        val lowerBound = (this / size) * size
        val upperBound = lowerBound + size
        return "$lowerBound-$upperBound"
    }

    private fun PropertyFalsifiedException.asBadShrinkExample(testConfig: TestConfig?): String {
        val shortenedOriginalInput = original.input.toString().let {
            if (it.length > 100 && testConfig == null) it.take(100) + " (remaining args truncated)" else it
        }

        return """
            |----
            |Seed: $seed
            |Iteration: $iteration
            |Original args: $shortenedOriginalInput
            |Shrunk args: ${smallest.input}
            |Shrink steps: $shrinkSteps
            """.trimMargin()
    }
}

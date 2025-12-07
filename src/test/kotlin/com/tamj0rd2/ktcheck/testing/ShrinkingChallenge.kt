package com.tamj0rd2.ktcheck.testing

import com.tamj0rd2.ktcheck.gen.Gen
import com.tamj0rd2.ktcheck.gen.int
import com.tamj0rd2.ktcheck.gen.list
import com.tamj0rd2.ktcheck.gen.map
import com.tamj0rd2.ktcheck.testing.Stats.Companion.withStats
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.isEqualTo
import strikt.assertions.isLessThan
import strikt.assertions.isLessThanOrEqualTo
import java.io.ByteArrayOutputStream
import java.io.PrintStream

// based on https://github.com/jlink/shrinking-challenge/tree/main/challenges
class ShrinkingChallenge {
    @Test
    fun reverse() = expectShrunkOutput("Arg 0 -> [0, 1]") { seed, printStream ->
        val gen = Gen.int(Int.MIN_VALUE..Int.MAX_VALUE).list(0..10000)
        test(
            property = checkAll(gen) { initial -> expectThat(initial.reversed()).isEqualTo(initial) },
            seed = seed,
            testReporter = PrintingTestReporter(printStream, false),
        )
    }

    @Test
    fun nestedLists() =
        expectShrunkOutput("Arg 0 -> [[0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]]", minConfidence = 80.0) { seed, printStream ->
            test(
                property = checkAll(Gen.int(Int.MIN_VALUE..Int.MAX_VALUE).list().list()) { ls ->
                    expectThat(ls.sumOf { it.size }).isLessThanOrEqualTo(10)
                },
                seed = seed,
                testReporter = PrintingTestReporter(printStream, false),
            )
        }

    @Test
    // Most of the time the shrinker provides a much smaller counter example, but very rarely the minimal one.
    fun lengthList() = expectShrunkOutput("Arg 0 -> [900]", minConfidence = 5.0) { seed, printStream ->
        val gen = Gen.int(0..1000).list(1..100)
        test(
            property = checkAll(gen) { ls -> expectThat(ls.max()).isLessThan(900) },
            seed = seed,
            testReporter = PrintingTestReporter(printStream, false),
        )
    }

    // runs the property as property so we can assert on confidence levels of shrinking.
    private fun expectShrunkOutput(
        expected: String,
        minConfidence: Double = 100.0,
        block: (Long, PrintStream) -> Unit,
    ) {
        // todo: make an actual Long generator.
        val seedGen = Gen.int(0..Int.MAX_VALUE).map { it.toLong() }
        withStats { stats ->
            test(checkAll(seedGen) { seed ->
                val outputStream = ByteArrayOutputStream()
                val printStream = PrintStream(outputStream)
                expectThrows<AssertionError> { block(seed, printStream) }

                val relevantLine = outputStream.toString().lines().single { it.startsWith("Arg 0 -> ") }
                val containedExpectedString = relevantLine.contains(expected)
                stats.collect(containedExpectedString.toString())

                if (!containedExpectedString) println("Bad sample for seed $seed: $relevantLine")
            }, iterations = 100)

            stats.checkPercentages(mapOf("true" to minConfidence))
        }
    }
}

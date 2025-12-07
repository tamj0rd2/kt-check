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
import strikt.assertions.isNotNull

// based on https://github.com/jlink/shrinking-challenge/tree/main/challenges
class ShrinkingChallenge {
    @Test
    fun reverse() = expectShrunkArgs(mapOf(0 to listOf(0, 1))) { config ->
        val gen = Gen.int(Int.MIN_VALUE..Int.MAX_VALUE).list(0..10000)
        test(
            property = checkAll(gen) { initial -> expectThat(initial.reversed()).isEqualTo(initial) },
            config = config,
        )
    }

    @Test
    fun nestedLists() =
        expectShrunkArgs(
            mapOf(0 to listOf(listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0))),
            minConfidence = 80.0
        ) { config ->
            test(
                property = checkAll(Gen.int(Int.MIN_VALUE..Int.MAX_VALUE).list().list()) { ls ->
                    expectThat(ls.sumOf { it.size }).isLessThanOrEqualTo(10)
                },
                config = config,
            )
        }

    @Test
    // Most of the time the shrinker provides a much smaller counter example, but very rarely the minimal one.
    fun lengthList() = expectShrunkArgs(mapOf(0 to listOf(900)), minConfidence = 5.0) { config ->
        val gen = Gen.int(0..1000).list(1..100)
        test(
            property = checkAll(gen) { ls -> expectThat(ls.max()).isLessThan(900) },
            config = config,
        )
    }

    // runs the property as property so we can assert on confidence levels of shrinking.
    private fun expectShrunkArgs(
        expected: Map<Int, Any?>,
        minConfidence: Double = 100.0,
        block: (TestConfig) -> Unit,
    ) {
        // todo: make an actual Long generator.
        val seedGen = Gen.int(0..Int.MAX_VALUE).map { it.toLong() }
        withStats { stats ->
            test(checkAll(seedGen) { seed ->
                val spyTestReporter = SpyTestReporter()
                expectThrows<AssertionError> { block(TestConfig(seed = seed, testReporter = spyTestReporter)) }

                val reportedFailure = expectThat(spyTestReporter.reportedFailure).isNotNull().subject
                val shrunkArgs = expectThat(reportedFailure).get { shrunkArgs }.isNotNull().subject
                val argsAreEqual = shrunkArgs == expected.entries.sortedBy { it.key }.map { it.value }
                stats.collect(argsAreEqual.toString())

                if (!argsAreEqual) println("Bad sample $reportedFailure")
            }, TestConfig(iterations = 100))

            stats.checkPercentages(mapOf("true" to minConfidence))
        }
    }

    private class SpyTestReporter : TestReporter {
        override fun reportSuccess(seed: Long, iterations: Int) {}

        data class ReportedFailure(
            val seed: Long,
            val originalArgs: List<Any?>,
            val shrunkArgs: List<Any?>?,
        )

        var reportedFailure: ReportedFailure? = null

        override fun reportFailure(
            seed: Long,
            failedIteration: Int,
            originalFailure: TestResult.Failure,
            shrunkFailure: TestResult.Failure?,
        ) {
            this.reportedFailure = ReportedFailure(
                seed = seed,
                originalArgs = originalFailure.args,
                shrunkArgs = shrunkFailure?.args,
            )
        }
    }
}

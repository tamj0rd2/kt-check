package com.tamj0rd2.ktcheck.genv2

import com.tamj0rd2.ktcheck.stats.Counter.Companion.withCounter
import com.tamj0rd2.ktcheck.testing.TestConfig
import com.tamj0rd2.ktcheck.testing.TestReporter
import com.tamj0rd2.ktcheck.testing.TestResult
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isLessThan
import strikt.assertions.isLessThanOrEqualTo
import strikt.assertions.isNotNull

class Shrinking {

    @Test
    fun `can shrink a number`() = expectShrunkArgs(expected = mapOf(0 to 1)) { config ->
        forAll(config, Gen.int(1..100)) { false }
    }

    @Test
    fun `can shrink a list`() = expectShrunkArgs(expected = mapOf(0 to emptyList<Int>())) { config ->
        forAll(config, Gen.int().list()) { false }
    }

    @Test
    @Disabled("todo: fix this next")
    fun repro() {
        checkAll(TestConfig().replay(778317316, 2), Gen.int(0..5).list(0..5).list(0..5)) { ls ->
            expectThat(ls.sumOf { it.size }).isLessThanOrEqualTo(10)
        }
    }

    // based on https://github.com/jlink/shrinking-challenge/tree/main/challenges
    @Nested
    inner class Challenges {
        @Test
        fun reverse() = expectShrunkArgs(
            expected = mapOf(0 to {
                (it as List<*>) in setOf(
                    listOf(0, 1),
                    listOf(1, 0),
                    listOf(-1, 0),
                    listOf(0, -1)
                )
            })
        ) { config ->
            val gen = Gen.int().list()
            checkAll(config, gen) { initial -> expectThat(initial.reversed()).isEqualTo(initial) }
        }

        @Test
        @Disabled
        fun nestedLists() = expectShrunkArgs(
            expected = mapOf(0 to listOf(listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0))),
            minConfidence = 80.0
        ) { config ->
            checkAll(config, Gen.int(Int.MIN_VALUE..Int.MAX_VALUE).list().list()) { ls ->
                expectThat(ls.sumOf { it.size }).isLessThanOrEqualTo(10)
            }
        }

        @Test
        @Disabled
        fun lengthList() = expectShrunkArgs(
            expected = mapOf(0 to listOf(900)),
            // Most of the time the shrinker provides a much smaller counter example, but very rarely the minimal one.
            minConfidence = 1.0
        ) { config ->
            val gen = Gen.int(0..1000).list(1..100)
            checkAll(config, gen) { ls -> expectThat(ls.max()).isLessThan(900) }
        }
    }

    companion object {
        @JvmName("expectShrunkArgsSimple")
        private fun expectShrunkArgs(
            expected: Map<Int, Any?>,
            minConfidence: Double = 100.0,
            block: (TestConfig) -> Unit,
        ) = expectShrunkArgs(
            expected = expected.mapValues { (_, expected) -> { it == expected } },
            minConfidence = minConfidence,
            block = block
        )

        // runs the property as property so we can assert on confidence levels of shrinking.
        private fun expectShrunkArgs(
            expected: Map<Int, (Any?) -> Boolean>,
            minConfidence: Double = 100.0,
            block: (TestConfig) -> Unit,
        ) {
            // todo: make an actual Long generator.
            val seedGen = Gen.int(0..Int.MAX_VALUE).map { it.toLong() }
            val failures = mutableListOf<SpyTestReporter.ReportedFailure>()
            val counter = withCounter {
                checkAll(TestConfig().withIterations(100), seedGen) { seed ->
                    val spyTestReporter = SpyTestReporter()
                    val x = runCatching { block(TestConfig().withSeed(seed).withReporter(spyTestReporter)) }
                        .exceptionOrNull()
                    when (x) {
                        null -> error("Expected property to fail for seed $seed, but succeeded")
                        !is AssertionError -> throw x
                    }

                    val reportedFailure = expectThat(spyTestReporter.reportedFailure).isNotNull().subject
                    val shrunkArgs = expectThat(reportedFailure).get { shrunkArgs }.subject
                    val results = expected.entries.map { (key, fn) -> fn(shrunkArgs[key]) }
                    val expectationsMet = results.all { it }
                    collect("shrank-as-expected", expectationsMet)

                    if (!expectationsMet) failures.add(reportedFailure)
                }
            }

            if (failures.isNotEmpty()) {
                println("Example failures:")
                failures
                    .sortedBy { it.shrunkArgs.toString().length }
                    .take(5)
                    .forEach { println("$it\n") }
            }

            counter.checkPercentages("shrank-as-expected", mapOf(true to minConfidence))

        }
    }

    private class SpyTestReporter : TestReporter {
        override fun reportSuccess(seed: Long, iterations: Int) {}

        data class ReportedFailure(
            val seed: Long,
            val iteration: Int,
            val originalArgs: List<Any?>,
            val shrunkArgs: List<Any?>,
        )

        var reportedFailure: ReportedFailure? = null

        override fun reportFailure(
            seed: Long,
            failedIteration: Int,
            originalFailure: TestResult.Failure,
            shrunkFailure: TestResult.Failure,
        ) {
            this.reportedFailure = ReportedFailure(
                seed = seed,
                iteration = failedIteration,
                originalArgs = originalFailure.args,
                shrunkArgs = shrunkFailure.args,
            )
        }
    }
}

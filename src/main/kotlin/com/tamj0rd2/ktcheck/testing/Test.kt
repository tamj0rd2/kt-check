package com.tamj0rd2.ktcheck.testing

import com.tamj0rd2.ktcheck.gen.ChoiceSequence
import com.tamj0rd2.ktcheck.gen.ChoiceSequence.Companion.shrink
import com.tamj0rd2.ktcheck.gen.Gen
import com.tamj0rd2.ktcheck.gen.InvalidReplay
import com.tamj0rd2.ktcheck.gen.WritableChoiceSequence
import kotlin.random.Random

sealed interface TestResult {
    val args: List<Any?>

    data class Success(override val args: List<Any?>) : TestResult
    data class Failure(override val args: List<Any?>, val failure: Throwable) : TestResult
}

typealias Property = Gen<TestResult>

fun test(
    arb: Property,
    // todo: make default configurable via a system property
    iterations: Int = 1000,
    seed: Long = Random.nextLong(),
    testReporter: TestReporter = PrintingTestReporter(),
) {
    val rand = Random(seed)

    fun getSmallestCounterExample(choices: ChoiceSequence): TestResult.Failure? {
        for (candidate in choices.shrink()) {
            val result =
                try {
                    arb.generate(candidate)
                } catch (_: InvalidReplay) {
                    continue
                }

            if (result is TestResult.Failure) {
                return getSmallestCounterExample(candidate) ?: result
            }
        }

        return null
    }

    (1..iterations).forEach { iteration ->
        val choices = WritableChoiceSequence(rand)
        when (val testResult = arb.generate(choices)) {
            is TestResult.Success -> return@forEach

            is TestResult.Failure -> {
                val shrunkResult = getSmallestCounterExample(choices)
                testReporter.reportFailure(
                    seed = seed,
                    failedIteration = iteration,
                    originalFailure = testResult,
                    shrunkFailure = shrunkResult
                )
                throw (shrunkResult ?: testResult).failure
            }
        }
    }

    testReporter.reportSuccess(seed, iterations)
}

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

data class TestConfig(
    // todo: make default configurable via a system property. also, extract this into some Config object?
    val iterations: Int = 1000,
    val seed: Long = Random.nextLong(),
    val testReporter: TestReporter = PrintingTestReporter(),
)

fun test(
    config: TestConfig = TestConfig(),
    property: Property,
) {
    val seed = config.seed
    val iterations = config.iterations
    val testReporter = config.testReporter
    val rand = Random(seed)

    (1..iterations).forEach { iteration ->
        val choices = WritableChoiceSequence(rand)
        when (val testResult = property.generate(choices)) {
            is TestResult.Success -> return@forEach

            is TestResult.Failure -> {
                val shrunkResult = property.getSmallestCounterExample(choices)
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

private fun Property.getSmallestCounterExample(choices: ChoiceSequence): TestResult.Failure? {
    for (candidate in choices.shrink()) {
        val result =
            try {
                generate(candidate)
            } catch (_: InvalidReplay) {
                continue
            }

        if (result is TestResult.Failure) {
            return getSmallestCounterExample(candidate) ?: result
        }
    }

    return null
}

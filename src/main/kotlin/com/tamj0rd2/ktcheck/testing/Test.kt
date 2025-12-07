package com.tamj0rd2.ktcheck.testing

import com.tamj0rd2.ktcheck.gen.ChoiceSequence
import com.tamj0rd2.ktcheck.gen.ChoiceSequence.Companion.shrink
import com.tamj0rd2.ktcheck.gen.Gen
import com.tamj0rd2.ktcheck.gen.InvalidReplay
import com.tamj0rd2.ktcheck.gen.WritableChoiceSequence
import com.tamj0rd2.ktcheck.gen.map
import com.tamj0rd2.ktcheck.util.Tuple
import kotlin.random.Random

sealed interface TestResult {
    val args: List<Any?>

    data class Success(override val args: List<Any?>) : TestResult
    data class Failure(override val args: List<Any?>, val failure: Throwable) : TestResult
}

data class TestConfig(
    // todo: make default configurable via a system property. also, extract this into some Config object?
    val iterations: Int = 1000,
    val seed: Long = Random.nextLong(),
    val testReporter: TestReporter = PrintingTestReporter(),
)

fun interface Test<T> {
    /** Runs the test on the given input. Should throw an AssertionError if the test fails. */
    operator fun invoke(input: T)
}

@Suppress("unused")
fun <T> checkAll(gen: Gen<T>, test: Test<T>) = checkAll(TestConfig(), gen, test)

fun <T> checkAll(
    config: TestConfig,
    gen: Gen<T>,
    test: Test<T>,
) {
    val testResultsGen = gen.map { test.getResultFor(it) }
    val seed = config.seed
    val iterations = config.iterations
    val testReporter = config.testReporter
    val rand = Random(seed)

    (1..iterations).forEach { iteration ->
        val choices = WritableChoiceSequence(rand)
        when (val testResult = testResultsGen.generate(choices)) {
            is TestResult.Success -> return@forEach

            is TestResult.Failure -> {
                val shrunkResult = testResultsGen.getSmallestCounterExample(choices)
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

private fun <T> Test<T>.getResultFor(t: T): TestResult {
    val args = when (t) {
        is Tuple -> t.values
        else -> listOf(t)
    }

    return try {
        this(t)
        TestResult.Success(args)
    } catch (e: AssertionError) {
        TestResult.Failure(args, e)
    }
}

private fun Gen<TestResult>.getSmallestCounterExample(choices: ChoiceSequence): TestResult.Failure? {
    for (choices in choices.shrink()) {
        val result =
            try {
                generate(choices)
            } catch (_: InvalidReplay) {
                continue
            }

        if (result is TestResult.Failure) {
            return getSmallestCounterExample(choices) ?: result
        }
    }

    return null
}

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
    data class Failure(override val args: List<Any?>, val failure: AssertionError) : TestResult
}

data class TestConfig(
    val iterations: Int = defaultIterations,
    val seed: Long = Random.nextLong(),
    val reporter: TestReporter = PrintingTestReporter(),
) {
    companion object {
        val defaultIterations get() = System.getProperty(SYSTEM_PROPERTY_TEST_ITERATIONS)?.toIntOrNull() ?: 1000

        internal const val SYSTEM_PROPERTY_TEST_ITERATIONS = "ktcheck.test.iterations"
    }
}

sealed interface Test<T> {
    fun test(input: T): AssertionError?
}

fun interface TestByThrowing<T> : Test<T> {
    /** Runs the test on the given input. Should throw an AssertionError if the test fails. */
    operator fun invoke(input: T)

    override fun test(input: T): AssertionError? = try {
        invoke(input)
        null
    } catch (e: AssertionError) {
        e
    }
}

fun interface TestByBool<T> : Test<T> {
    /** Runs the test on the given input. Should throw an AssertionError if the test fails. */
    operator fun invoke(input: T): Boolean

    override fun test(input: T): AssertionError? =
        if (invoke(input)) null else AssertionError("Test falsified")
}


@Suppress("unused")
fun <T> forAll(gen: Gen<T>, test: TestByBool<T>) = forAll(TestConfig(), gen, test)
fun <T> forAll(config: TestConfig, gen: Gen<T>, test: TestByBool<T>) = test(config, gen, test as Test<T>)

@Suppress("unused")
fun <T> checkAll(gen: Gen<T>, test: TestByThrowing<T>) = checkAll(TestConfig(), gen, test)
fun <T> checkAll(config: TestConfig, gen: Gen<T>, test: TestByThrowing<T>) = test(config, gen, test as Test<T>)

private fun <T> test(config: TestConfig, gen: Gen<T>, test: Test<T>) {
    val testResultsGen = gen.map { test.getResultFor(it) }
    val seed = config.seed
    val iterations = config.iterations
    val testReporter = config.reporter
    val rand = Random(seed)

    (1..iterations).forEach { iteration ->
        val choices = WritableChoiceSequence(rand)
        when (val testResult = testResultsGen.generate(choices)) {
            is TestResult.Success -> return@forEach

            is TestResult.Failure -> {
                val shrunkResult = testResultsGen.getSmallestCounterExample(choices) ?: testResult
                testReporter.reportFailure(
                    seed = seed,
                    failedIteration = iteration,
                    originalFailure = testResult,
                    shrunkFailure = shrunkResult,
                )
                throw shrunkResult.failure
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

    val failure = test(t) ?: return TestResult.Success(args)
    return TestResult.Failure(args, failure)
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

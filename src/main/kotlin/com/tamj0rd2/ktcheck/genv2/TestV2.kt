package com.tamj0rd2.ktcheck.genv2

import com.tamj0rd2.ktcheck.gen.deriveSeed
import com.tamj0rd2.ktcheck.testing.HardcodedTestConfig
import com.tamj0rd2.ktcheck.testing.Test
import com.tamj0rd2.ktcheck.testing.TestByBool
import com.tamj0rd2.ktcheck.testing.TestByThrowing
import com.tamj0rd2.ktcheck.testing.TestConfig
import com.tamj0rd2.ktcheck.testing.TestResult
import com.tamj0rd2.ktcheck.util.Tuple

@Suppress("unused")
fun <T> forAll(gen: Gen<T>, test: TestByBool<T>) = forAll(TestConfig(), gen, test)
fun <T> forAll(config: TestConfig, gen: Gen<T>, test: TestByBool<T>) = test(config, gen, test as Test<T>)

@Suppress("unused")
fun <T> checkAll(gen: Gen<T>, test: TestByThrowing<T>) = checkAll(TestConfig(), gen, test)
fun <T> checkAll(config: TestConfig, gen: Gen<T>, test: TestByThrowing<T>) = test(config, gen, test as Test<T>)

@OptIn(HardcodedTestConfig::class)
private fun <T> test(config: TestConfig, gen: Gen<T>, test: Test<T>) {
    val testResultsGen = gen.map { test.getResultFor(it) }

    fun runIteration(iteration: Int) {
        val sampleTree = ValueTree.fromSeed(deriveSeed(config.seed, iteration))
        val (testResult, shrinks) = testResultsGen.generate(sampleTree)

        when (testResult) {
            is TestResult.Success -> return

            is TestResult.Failure -> {
                val shrunkResult = testResultsGen.getSmallestCounterExample(testResult, shrinks.iterator())
                config.reporter.reportFailure(
                    seed = config.seed,
                    failedIteration = iteration,
                    originalFailure = testResult,
                    shrunkFailure = shrunkResult,
                )

                throw PropertyFalsifiedException(
                    seed = config.seed,
                    iteration = iteration,
                    originalResult = testResult,
                    shrunkResult = shrunkResult,
                )
            }
        }
    }

    (1..config.iterations).forEach(::runIteration)
    config.reporter.reportSuccess(config.iterations)
}

private fun <T> Test<T>.getResultFor(t: T): TestResult {
    val args = when (t) {
        is Tuple -> t.values
        else -> listOf(t)
    }

    val failure = test(t) ?: return TestResult.Success(args)
    return TestResult.Failure(args, failure)
}

private tailrec fun Gen<TestResult>.getSmallestCounterExample(
    testResult: TestResult.Failure,
    iterator: Iterator<ValueTree>,
): TestResult.Failure {
    if (!iterator.hasNext()) return testResult

    val shrunkTree = iterator.next()
    val (shrunkTestResult, newShrinks) = generate(shrunkTree)

    return if (shrunkTestResult is TestResult.Failure) {
        getSmallestCounterExample(shrunkTestResult, newShrinks.iterator())
    } else {
        getSmallestCounterExample(testResult, iterator)
    }
}

internal class PropertyFalsifiedException(
    val seed: Long,
    val iteration: Int,
    val originalResult: TestResult.Failure,
    val shrunkResult: TestResult.Failure,
) : AssertionError() {
    override val cause: Throwable = shrunkResult.failure
}

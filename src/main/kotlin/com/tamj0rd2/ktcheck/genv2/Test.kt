package com.tamj0rd2.ktcheck.genv2

import com.tamj0rd2.ktcheck.testing.Test
import com.tamj0rd2.ktcheck.testing.TestByBool
import com.tamj0rd2.ktcheck.testing.TestByThrowing
import com.tamj0rd2.ktcheck.testing.TestConfig
import com.tamj0rd2.ktcheck.testing.TestResult
import com.tamj0rd2.ktcheck.util.Tuple
import kotlin.random.Random

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
    val random = Random(seed)

    (1..iterations).forEach { iteration ->
        val sampleTree = SampleTree.from(random.nextLong())
        val (testResult, shrinks) = testResultsGen.generate(sampleTree)

        when (testResult) {
            is TestResult.Success -> return@forEach

            is TestResult.Failure -> {
                val shrunkResult = testResultsGen.getSmallestCounterExample(testResult, shrinks.iterator())
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

private tailrec fun Gen<TestResult>.getSmallestCounterExample(
    testResult: TestResult.Failure,
    iterator: Iterator<SampleTree>,
): TestResult.Failure {
    if (!iterator.hasNext()) return testResult

    val shrunkTree = iterator.next()
    val (shrunkTestResult, newShrinks) = generate(shrunkTree)

    return if (shrunkTestResult is TestResult.Failure) {
        // Found a smaller failure, recursively shrink it further
        getSmallestCounterExample(shrunkTestResult, newShrinks.iterator())
    } else {
        // Continue searching through remaining shrinks
        getSmallestCounterExample(testResult, iterator)
    }
}

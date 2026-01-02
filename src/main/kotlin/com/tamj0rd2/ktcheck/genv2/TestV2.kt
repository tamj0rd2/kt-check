package com.tamj0rd2.ktcheck.genv2

import com.tamj0rd2.ktcheck.testing.HardcodedTestConfig
import com.tamj0rd2.ktcheck.testing.Test
import com.tamj0rd2.ktcheck.testing.TestByBool
import com.tamj0rd2.ktcheck.testing.TestByThrowing
import com.tamj0rd2.ktcheck.testing.TestConfig
import com.tamj0rd2.ktcheck.testing.TestResult

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
                val (shrunkResult, shrinkSteps) = testResultsGen.getSmallestCounterExample(
                    testResult,
                    shrinks.iterator()
                )

                PropertyFalsifiedException(
                    seed = config.seed,
                    iteration = iteration,
                    originalResult = testResult,
                    shrunkResult = shrunkResult,
                    shrinkSteps = shrinkSteps
                ).also {
                    config.reporter.reportFailure(it)
                    throw it
                }
            }
        }
    }

    val startingIteration = (config.replayIteration ?: 1)
    (startingIteration..<startingIteration + config.iterations).forEach(::runIteration)
    config.reporter.reportSuccess(config.iterations)
}

private fun <T> Test<T>.getResultFor(t: T): TestResult<T> {
    val failure = test(t) ?: return TestResult.Success(t)
    return TestResult.Failure(t, failure)
}

private tailrec fun <T> Gen<TestResult<T>>.getSmallestCounterExample(
    testResult: TestResult.Failure<T>,
    iterator: Iterator<ValueTree>,
    steps: Int = 0,
): Pair<TestResult.Failure<T>, Int> {
    if (!iterator.hasNext()) return testResult to steps

    val shrunkTree = iterator.next()
    val (shrunkTestResult, newShrinks) = generate(shrunkTree)

    return if (shrunkTestResult is TestResult.Failure) {
        getSmallestCounterExample(shrunkTestResult, newShrinks.iterator(), steps + 1)
    } else {
        getSmallestCounterExample(testResult, iterator, steps + 1)
    }
}

// todo: I wish this all lived inside of TestResult. having an extra things seems... extra
class PropertyFalsifiedException(
    val seed: Long,
    val iteration: Int,
    val originalResult: TestResult.Failure<*>,
    val shrunkResult: TestResult.Failure<*>,
    val shrinkSteps: Int,
) : AssertionError() {
    override val cause: Throwable = shrunkResult.failure
}

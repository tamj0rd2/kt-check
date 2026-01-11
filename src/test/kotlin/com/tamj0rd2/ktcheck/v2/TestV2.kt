package com.tamj0rd2.ktcheck.v2

import com.tamj0rd2.ktcheck.GenerationException
import com.tamj0rd2.ktcheck.testing.HardcodedTestConfig
import com.tamj0rd2.ktcheck.testing.PropertyFalsifiedException
import com.tamj0rd2.ktcheck.testing.Test
import com.tamj0rd2.ktcheck.testing.TestByBool
import com.tamj0rd2.ktcheck.testing.TestByThrowing
import com.tamj0rd2.ktcheck.testing.TestConfig
import com.tamj0rd2.ktcheck.testing.TestResult

@Suppress("unused")
fun <T> forAll(gen: GenV2<T>, test: TestByBool<T>) = forAll(TestConfig(), gen, test)
fun <T> forAll(config: TestConfig, gen: GenV2<T>, test: TestByBool<T>) = test(config, gen, test as Test<T>)

@Suppress("unused")
fun <T> checkAll(gen: GenV2<T>, test: TestByThrowing<T>) = checkAll(TestConfig(), gen, test)
fun <T> checkAll(config: TestConfig, gen: GenV2<T>, test: TestByThrowing<T>) = test(config, gen, test as Test<T>)

@OptIn(HardcodedTestConfig::class)
private fun <T> test(config: TestConfig, gen: GenV2<T>, test: Test<T>) {
    val testResultsGen = gen.map { test.getResultFor(it) }

    fun runIteration(iteration: Int) {
        val producer = RandomValueProducerV2(config.seed.next(iteration))
        val (testResult, shrinks) = testResultsGen.generate(producer)

        when (testResult) {
            is TestResult.Success -> return

            is TestResult.Failure -> {
                val (shrunkResult, shrinkSteps) = testResultsGen.getSmallestCounterExample(
                    testResult,
                    shrinks.iterator()
                )

                PropertyFalsifiedException(
                    seed = config.seed.value,
                    iteration = iteration,
                    originalResult = testResult,
                    shrunkResult = shrunkResult.takeIf { it.input != testResult.input },
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

private tailrec fun <T> GenV2<TestResult<T>>.getSmallestCounterExample(
    testResult: TestResult.Failure<T>,
    iterator: Iterator<GenResultV2<TestResult<T>>>,
    steps: Int = 0,
): Pair<TestResult.Failure<T>, Int> {
    if (!iterator.hasNext()) return testResult to steps

    val genResult = try {
        iterator.next()
    } catch (_: GenerationException) {
        null
    }

    return when (genResult) {
        null -> getSmallestCounterExample(testResult, iterator, steps + 1)
        else -> {
            val (shrunkTestResult, newShrinks) = genResult

            if (shrunkTestResult is TestResult.Failure) {
                getSmallestCounterExample(shrunkTestResult, newShrinks.iterator(), steps + 1)
            } else {
                getSmallestCounterExample(testResult, iterator, steps + 1)
            }
        }
    }
}

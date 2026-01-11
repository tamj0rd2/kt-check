package com.tamj0rd2.ktcheck.v2

import com.tamj0rd2.ktcheck.PropertyFalsifiedException
import com.tamj0rd2.ktcheck.Test
import com.tamj0rd2.ktcheck.TestConfig
import com.tamj0rd2.ktcheck.TestResult

internal fun <T> test(config: TestConfig, gen: GenV2<T>, test: Test<T>) {
    val testResultsGen = gen.map { test.getResultFor(it) }

    fun runIteration(iteration: Int) {
        val producer = RandomValueProducerV2(config.seed.next(iteration))
        val (testResult, shrinks) = testResultsGen.generate(producer)

        when (testResult) {
            is TestResult.Success -> return

            is TestResult.Failure -> {
                val (shrunkResult, shrinkSteps) = getSmallestCounterExample(
                    smallestResultSoFar = testResult,
                    iterator = shrinks.iterator()
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

private tailrec fun <T> getSmallestCounterExample(
    smallestResultSoFar: TestResult.Failure<T>,
    iterator: Iterator<GenResultV2<TestResult<T>>>,
    steps: Int = 0,
): Pair<TestResult.Failure<T>, Int> {
    if (!iterator.hasNext()) return smallestResultSoFar to steps

    val shrunkResult = iterator.next()

    return when (shrunkResult.value) {
        is TestResult.Failure -> {
            getSmallestCounterExample(
                smallestResultSoFar = shrunkResult.value,
                iterator = shrunkResult.shrinks.iterator(),
                steps = steps + 1
            )
        }

        is TestResult.Success<*> -> getSmallestCounterExample(
            smallestResultSoFar = smallestResultSoFar,
            iterator = iterator,
            steps = steps + 1
        )
    }
}

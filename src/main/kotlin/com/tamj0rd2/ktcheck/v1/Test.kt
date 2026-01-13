package com.tamj0rd2.ktcheck.v1

import com.tamj0rd2.ktcheck.GenerationException
import com.tamj0rd2.ktcheck.PropertyFalsifiedException
import com.tamj0rd2.ktcheck.Test
import com.tamj0rd2.ktcheck.TestConfig
import com.tamj0rd2.ktcheck.TestResult
import com.tamj0rd2.ktcheck.core.ProducerTree

internal fun <T> test(config: TestConfig, gen: GenV1<T>, test: Test<T>) {
    val testResultsGen = gen.map { test.getResultFor(it) }

    fun runIteration(iteration: Int) {
        val sampleTree = ProducerTree.new(config.seed.next(iteration))
        val (testResult, shrinks) = testResultsGen.generate(sampleTree, GenMode.Initial)

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

private tailrec fun <T> GenV1<TestResult<T>>.getSmallestCounterExample(
    testResult: TestResult.Failure<T>,
    iterator: Iterator<ProducerTree>,
    steps: Int = 0,
): Pair<TestResult.Failure<T>, Int> {
    if (!iterator.hasNext()) return testResult to steps

    val genResult = try {
        generate(iterator.next(), GenMode.Shrinking)
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

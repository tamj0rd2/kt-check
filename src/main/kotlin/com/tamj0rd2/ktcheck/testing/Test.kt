package com.tamj0rd2.ktcheck.testing

import com.tamj0rd2.ktcheck.gen.Gen
import com.tamj0rd2.ktcheck.gen.SampleTree
import com.tamj0rd2.ktcheck.gen.deriveSeed
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
        val sampleTree = SampleTree.from(deriveSeed(config.seed, iteration))
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
                throw shrunkResult.failure
            }
        }
    }

    if (config.replayIteration != null) {
        runIteration(config.replayIteration)
        config.reporter.reportSuccess(config.seed, 1)
        return
    }

    (1..config.iterations).forEach(::runIteration)
    config.reporter.reportSuccess(config.seed, config.iterations)
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
        getSmallestCounterExample(shrunkTestResult, newShrinks.iterator())
    } else {
        getSmallestCounterExample(testResult, iterator)
    }
}

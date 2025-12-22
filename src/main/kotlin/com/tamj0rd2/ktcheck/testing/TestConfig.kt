package com.tamj0rd2.ktcheck.testing

import kotlin.random.Random

sealed interface TestResult {
    val args: List<Any?>

    data class Success(override val args: List<Any?>) : TestResult
    data class Failure(override val args: List<Any?>, val failure: AssertionError) : TestResult
}

@RequiresOptIn(
    message = "Indicates that test configuration has been hardcoded for this test, which should only be done for local debugging purposes.",
    level = RequiresOptIn.Level.WARNING
)
annotation class HardcodedTestConfig

@ConsistentCopyVisibility
data class TestConfig private constructor(
    internal val iterations: Int,
    internal val seed: Long,
    internal val replayIteration: Int?,
    internal val reporter: TestReporter,
) {
    constructor() : this(
        iterations = System.getProperty(SYSTEM_PROPERTY_TEST_ITERATIONS)?.toIntOrNull() ?: 1000,
        seed = Random.nextLong(),
        replayIteration = null,
        reporter = PrintingTestReporter(),
    )

    fun withIterations(iterations: Int) = copy(iterations = iterations)

    fun withSeed(seed: Long) = copy(seed = seed)

    @HardcodedTestConfig
    fun replay(seed: Long, iteration: Int) = copy(iterations = 1, seed = seed, replayIteration = iteration)

    fun withReporter(reporter: TestReporter) = copy(reporter = reporter)

    companion object {
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

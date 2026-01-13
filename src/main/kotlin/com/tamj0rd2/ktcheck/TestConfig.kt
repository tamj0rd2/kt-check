package com.tamj0rd2.ktcheck

import com.tamj0rd2.ktcheck.core.Seed
import kotlin.random.Random

sealed class TestResult<T> {
    abstract val input: T

    data class Success<T>(override val input: T) : TestResult<T>()

    data class Failure<T>(override val input: T, val failure: AssertionError) : TestResult<T>()
}

@RequiresOptIn(
    message = "Indicates that test configuration has been hardcoded for this test, which should only be done for local debugging purposes.",
    level = RequiresOptIn.Level.WARNING
)
annotation class HardcodedTestConfig

@ConsistentCopyVisibility
data class TestConfig private constructor(
    internal val iterations: Int,
    internal val seed: Seed,
    internal val replayIteration: Int?,
    internal val reporter: TestReporter,
) {
    constructor() : this(
        iterations = System.getProperty(SYSTEM_PROPERTY_TEST_ITERATIONS)?.toIntOrNull() ?: 1000,
        seed = Seed(Random.nextLong()),
        replayIteration = null,
        reporter = PrintingTestReporter(),
    )

    fun withIterations(iterations: Int) = copy(iterations = iterations)

    fun withSeed(seed: Long) = copy(seed = Seed(seed))

    @HardcodedTestConfig
    fun replay(seed: Long, iteration: Int) = copy(
        iterations = 1,
        seed = Seed(seed),
        replayIteration = iteration
    )

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

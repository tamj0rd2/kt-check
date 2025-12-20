package com.tamj0rd2.ktcheck.testing

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

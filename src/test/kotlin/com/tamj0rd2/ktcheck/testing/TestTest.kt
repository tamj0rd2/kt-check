package com.tamj0rd2.ktcheck.testing

import com.tamj0rd2.ktcheck.gen.Gen
import com.tamj0rd2.ktcheck.gen.constant
import com.tamj0rd2.ktcheck.gen.int
import com.tamj0rd2.ktcheck.testing.TestTest.SpyTestReporter.Reporting
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.cause
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull

class TestTest {
    private val spyTestReporter = SpyTestReporter()
    private val testConfig get() = TestConfig().withReporter(reporter = spyTestReporter)

    @Test
    fun `forAll reports a success if the property holds true`() {
        forAll(testConfig, Gen.constant(true)) { it }
        expectThat(spyTestReporter.reporting).isA<Reporting.ReportedSuccess>()
    }

    @Test
    fun `forAll reports a failure if the property is falsified`() {
        expectThrows<AssertionError> { forAll(testConfig, Gen.constant(false)) { it } }
        expectThat(spyTestReporter.reporting).isA<Reporting.ReportedFailure>().get { error }.isA<AssertionError>()
    }

    @Test
    fun `forAll doesn't do any reporting if an exception is thrown - the error just bubbles up`() {
        val theError = AssertionError("uh oh!")
        expectThrows<AssertionError> { forAll(testConfig, Gen.constant(theError)) { throw it } }.isEqualTo(theError)
        expectThat(spyTestReporter.reporting).isA<Reporting.None>()
    }

    @Test
    fun `checkAll reports success if the property doesn't throw`() {
        checkAll(testConfig, Gen.constant(null)) { }
        expectThat(spyTestReporter.reporting).isA<Reporting.ReportedSuccess>()
    }

    @Test
    fun `checkAll reports a failure if the property threw an assertion error`() {
        val theError = AssertionError("boom!")
        expectThrows<PropertyFalsifiedException> {
            checkAll(
                testConfig,
                Gen.constant(theError)
            ) { throw it }
        }.cause.isEqualTo(theError)
        expectThat(spyTestReporter.reporting).isA<Reporting.ReportedFailure>().get { error }.isEqualTo(theError)
    }

    @Test
    fun `checkAll doesn't do any reporting if any other throwable is thrown - it just bubbles up`() {
        class MyThrowable : Throwable()
        val exception = MyThrowable()
        expectThrows<MyThrowable> { checkAll(testConfig, Gen.constant(exception)) { throw it } }.isEqualTo(exception)
        expectThat(spyTestReporter.reporting).isA<Reporting.None>()
    }

    @Test
    @OptIn(HardcodedTestConfig::class)
    fun `can hardcode a specific test iteration to run`() {
        val gen = Gen.int()

        var iterationCount = 0
        var valueOn5thIteration: Int? = null
        val initialConfig = testConfig

        forAll(initialConfig, gen) {
            iterationCount++
            if (iterationCount == 5) valueOn5thIteration = it
            true
        }

        expectThat(iterationCount).isEqualTo(testConfig.iterations)
        expectThat(valueOn5thIteration).isNotNull()

        var replayedIterations = 0
        var valueOnRetry: Int? = null
        val replayConfig = initialConfig.replay(initialConfig.seed.value, 5)

        forAll(replayConfig, gen) {
            replayedIterations++
            valueOnRetry = it
            true
        }

        expectThat(replayedIterations).isEqualTo(1)
        expectThat(valueOnRetry).isEqualTo(valueOn5thIteration)
    }

    private class SpyTestReporter : TestReporter {
        var reporting: Reporting = Reporting.None

        override fun reportSuccess(iterations: Int) {
            reporting = Reporting.ReportedSuccess(iterations)
        }

        override fun reportFailure(exception: PropertyFalsifiedException) {
            reporting = Reporting.ReportedFailure(exception.originalResult.failure)
        }

        sealed interface Reporting {
            data object None : Reporting
            data class ReportedSuccess(val iterations: Int) : Reporting
            data class ReportedFailure(val error: AssertionError) : Reporting
        }
    }
}

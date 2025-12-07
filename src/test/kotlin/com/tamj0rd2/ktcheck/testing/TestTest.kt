package com.tamj0rd2.ktcheck.testing

import com.tamj0rd2.ktcheck.gen.Gen
import com.tamj0rd2.ktcheck.gen.constant
import com.tamj0rd2.ktcheck.testing.TestTest.SpyTestReporter.Reporting
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.isA
import strikt.assertions.isEqualTo

class TestTest {
    private val reporter = SpyTestReporter()
    private val testConfig = TestConfig(reporter = reporter)

    @Test
    fun `forAll reports a success if the property holds true`() {
        forAll(testConfig, Gen.constant(true)) { it }
        expectThat(reporter.reporting).isA<Reporting.ReportedSuccess>()
    }

    @Test
    fun `forAll reports a failure if the property is falsified`() {
        expectThrows<AssertionError> { forAll(testConfig, Gen.constant(false)) { it } }
        expectThat(reporter.reporting).isA<Reporting.ReportedFailure>().get { error }.isA<AssertionError>()
    }

    @Test
    fun `forAll doesn't do any reporting if an exception is thrown - the error just bubbles up`() {
        val theError = AssertionError("uh oh!")
        expectThrows<AssertionError> { forAll(testConfig, Gen.constant(theError)) { throw it } }.isEqualTo(theError)
        expectThat(reporter.reporting).isA<Reporting.None>()
    }

    @Test
    fun `checkAll reports success if the property doesn't throw`() {
        checkAll(testConfig, Gen.constant(null)) { }
        expectThat(reporter.reporting).isA<Reporting.ReportedSuccess>()
    }

    @Test
    fun `checkAll reports a failure if the property threw an assertion error`() {
        val theError = AssertionError("boom!")
        expectThrows<AssertionError> { checkAll(testConfig, Gen.constant(theError)) { throw it } }.isEqualTo(theError)
        expectThat(reporter.reporting).isA<Reporting.ReportedFailure>().get { error }.isEqualTo(theError)
    }

    @Test
    fun `checkAll doesn't do any reporting if any other throwable is thrown - it just bubbles up`() {
        class MyThrowable : Throwable()
        val exception = MyThrowable()
        expectThrows<MyThrowable> { checkAll(testConfig, Gen.constant(exception)) { throw it } }.isEqualTo(exception)
        expectThat(reporter.reporting).isA<Reporting.None>()
    }

    private class SpyTestReporter : TestReporter {
        var reporting: Reporting = Reporting.None

        override fun reportSuccess(seed: Long, iterations: Int) {
            reporting = Reporting.ReportedSuccess
        }

        override fun reportFailure(
            seed: Long,
            failedIteration: Int,
            originalFailure: TestResult.Failure,
            shrunkFailure: TestResult.Failure?,
        ) {
            reporting = Reporting.ReportedFailure(originalFailure.failure)
        }

        sealed interface Reporting {
            data object None : Reporting
            data object ReportedSuccess : Reporting
            data class ReportedFailure(val error: AssertionError) : Reporting
        }
    }
}

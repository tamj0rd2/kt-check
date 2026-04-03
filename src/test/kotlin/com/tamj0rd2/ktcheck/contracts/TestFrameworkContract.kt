package com.tamj0rd2.ktcheck.contracts

import com.tamj0rd2.ktcheck.HardcodedTestConfig
import com.tamj0rd2.ktcheck.PropertyFalsifiedException
import com.tamj0rd2.ktcheck.TestConfig
import com.tamj0rd2.ktcheck.checkAll
import com.tamj0rd2.ktcheck.forAll
import org.junit.jupiter.api.Test
import strikt.api.expectDoesNotThrow
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.containsExactlyInAnyOrder
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull
import java.io.ByteArrayOutputStream

internal interface TestFrameworkContract : BaseContract {
    override val exampleGen get() = null

    @Test
    fun `forAll does not throw if the property holds true`() {
        val outputStream = ByteArrayOutputStream()
        val testConfig = TestConfig().withReportingStream(outputStream)

        expectDoesNotThrow { forAll(testConfig, constant(true)) { it } }
    }

    @Test
    fun `forAll throws if the property is falsified`() {
        expectThrows<PropertyFalsifiedException> { forAll(constant(false)) { it } }
    }

    @Test
    fun `forAll rethrows unexpected exceptions that occur`() {
        class MyThrowable : Throwable()
        val throwable = MyThrowable()

        expectThrows<MyThrowable> { forAll(constant(throwable)) { throw it } }.isEqualTo(throwable)
    }

    @Test
    fun `checkAll does not throw if the property doesn't throw`() {
        checkAll(constant(null)) { }

        expectDoesNotThrow { forAll(constant(true)) { it } }
    }

    @Test
    fun `checkAll rethrows unexpected exceptions that occur`() {
        class MyThrowable : Throwable()

        val throwable = MyThrowable()
        expectThrows<MyThrowable> { checkAll(constant(throwable)) { throw it } }.isEqualTo(throwable)
    }

    @Test
    fun `includes edge cases during test iterations`() {
        val seenValues = mutableSetOf<Int>()

        forAll(int()) {
            seenValues.add(it)
            true
        }

        val expectedEdges = setOf(0, 1, -1, Int.MIN_VALUE, Int.MIN_VALUE + 1, Int.MAX_VALUE, Int.MAX_VALUE - 1)
        expectThat(seenValues.take(expectedEdges.size)).containsExactlyInAnyOrder(expectedEdges)
    }

    @Test
    @OptIn(HardcodedTestConfig::class)
    fun `can hardcode a specific test iteration to run`() {
        val initialConfig = TestConfig().withIterations(100)
        val iterationToCheck = (1..100).random()
        val gen = int()


        var iterationCount = 0
        var valueOnSpecifiedIteration: Int? = null

        forAll(initialConfig, gen) {
            iterationCount++
            if (iterationCount == iterationToCheck) valueOnSpecifiedIteration = it
            true
        }

        expectThat(iterationCount).isEqualTo(initialConfig.iterations)
        expectThat(valueOnSpecifiedIteration).isNotNull()

        var replayedIterations = 0
        var valueOnRetry: Int? = null
        val replayConfig = initialConfig.replay(initialConfig.seed.value, iterationToCheck)

        forAll(replayConfig, gen) {
            replayedIterations++
            valueOnRetry = it
            true
        }

        expectThat(replayedIterations).isEqualTo(1)
        expectThat(valueOnRetry).isEqualTo(valueOnSpecifiedIteration)
    }
}

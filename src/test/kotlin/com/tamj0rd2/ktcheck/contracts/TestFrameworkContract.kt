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
import strikt.assertions.cause
import strikt.assertions.containsExactlyInAnyOrder
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull
import strikt.assertions.isNull

internal interface TestFrameworkContract : BaseContract {
    override val exampleGen get() = null

    @Test
    fun `does not throw if the property is not falsified`() {
        expectDoesNotThrow { forAll(int()) { true } }
        expectDoesNotThrow { checkAll(int()) { } }
    }

    @Test
    fun `throws if the property is falsified`() {
        expectThrows<PropertyFalsifiedException> { forAll(int()) { false } }.cause.isNull()

        val expectedError = AssertionError("bad")
        expectThrows<PropertyFalsifiedException> { checkAll(int()) { throw expectedError } }
            .cause.isEqualTo(expectedError)
    }

    @Test
    fun `rethrows unexpected exceptions that occur`() {
        class MyThrowable : Throwable()

        val throwable = MyThrowable()
        expectThrows<MyThrowable> { forAll(int()) { throw throwable } }.isEqualTo(throwable)
        expectThrows<MyThrowable> { checkAll(int()) { throw throwable } }.isEqualTo(throwable)
    }

    // todo: write another test to prove it includes user provided edge cases.
    @Test
    fun `includes edge cases during test iterations`() {
        val seenValues = mutableSetOf<Int>()
        val gen = int()
        val expectedEdges = setOf(0, 1, -1, Int.MIN_VALUE, Int.MIN_VALUE + 1, Int.MAX_VALUE, Int.MAX_VALUE - 1)

        forAll(gen) { seenValues.add(it); true }
        expectThat(seenValues.take(expectedEdges.size)).containsExactlyInAnyOrder(expectedEdges)

        seenValues.clear()
        checkAll(gen) { seenValues.add(it) }
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

        checkAll(initialConfig, gen) {
            iterationCount++
            if (iterationCount == iterationToCheck) valueOnSpecifiedIteration = it
        }

        expectThat(iterationCount).isEqualTo(initialConfig.iterations)
        expectThat(valueOnSpecifiedIteration).isNotNull()

        var replayedIterations = 0
        var valueOnRetry: Int? = null
        val replayConfig = initialConfig.replay(initialConfig.seed.value, iterationToCheck)

        checkAll(replayConfig, gen) {
            replayedIterations++
            valueOnRetry = it
        }

        expectThat(replayedIterations).isEqualTo(1)
        expectThat(valueOnRetry).isEqualTo(valueOnSpecifiedIteration)
    }
}

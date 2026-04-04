package com.tamj0rd2.ktcheck.contracts

import com.tamj0rd2.ktcheck.Gen
import com.tamj0rd2.ktcheck.GenBuilders
import com.tamj0rd2.ktcheck.HardcodedTestConfig
import com.tamj0rd2.ktcheck.PropertyFalsifiedException
import com.tamj0rd2.ktcheck.ShrinkingConstraintFactory
import com.tamj0rd2.ktcheck.TestConfig
import com.tamj0rd2.ktcheck.checkAll
import com.tamj0rd2.ktcheck.core.GenerationContext
import com.tamj0rd2.ktcheck.core.Seed
import com.tamj0rd2.ktcheck.forAll
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertTimeoutPreemptively
import org.junit.jupiter.api.fail
import org.opentest4j.TestSkippedException
import strikt.api.Assertion
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.containsExactlyInAnyOrder
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull
import java.time.Duration.ofSeconds

internal interface BaseContract : GenBuilders {
    val exampleGen: Gen<*>?
    val genSupportsShrinking: Boolean get() = true
    val genSupportsEdgeCases: Boolean get() = true

    fun getGenIfDefined(): Gen<Any> {
        val gen = exampleGen
        Assumptions.assumeTrue(gen != null)
        @Suppress("UNCHECKED_CAST")
        return gen as Gen<Any>
    }

    fun runIfGenSupportsShrinking() =
        Assumptions.assumeTrue(genSupportsShrinking, "skipped as this gen doesn't support shrinking")

    fun runIfGenSupportsEdgeCases() =
        Assumptions.assumeTrue(genSupportsEdgeCases, "skipped as this gen doesn't support edge cases")

    @Test
    fun `generated values are deterministic`() {
        repeatTest { seed ->
            val gen = getGenIfDefined()
            val originalResult = gen.generate(ctx(seed))
            val regenerated = gen.generate(ctx(seed))

            expectThat(regenerated).value.isEqualTo(originalResult.value)
        }
    }

    @Test
    fun `shrinks of generated values are deterministic`() {
        runIfGenSupportsShrinking()

        repeatTest { seed ->
            val gen = getGenIfDefined()
            val originalResult = gen.generate(ctx(seed))
            val regenerated = gen.generate(ctx(seed))

            expectThat(regenerated).shrunkValues.containsExactlyInAnyOrder(originalResult.shrunkValues)
            // this is the assertion I actually want, but the output is easier to read when split into 2 assertions.
            expectThat(regenerated).shrunkValues.isEqualTo(originalResult.shrunkValues)
        }
    }

    @Test
    fun `edge cases are deterministic`() {
        runIfGenSupportsEdgeCases()

        repeatTest { seed ->
            val gen = getGenIfDefined()
            val originalResult = gen.edgeCase(seed)
            val regenerated = gen.edgeCase(seed)
            if (originalResult == null) skipIteration()

            expectThat(regenerated).isNotNull().value.isEqualTo(originalResult.value)
        }
    }

    @Test
    fun `shrinks of edge cases are deterministic`() {
        runIfGenSupportsEdgeCases()
        runIfGenSupportsShrinking()

        repeatTest { seed ->
            val gen = getGenIfDefined()
            val originalResult = gen.edgeCase(seed)
            val regenerated = gen.edgeCase(seed)
            if (originalResult == null) skipIteration()

            expectThat(regenerated).isNotNull().shrunkValues.containsExactlyInAnyOrder(originalResult.shrunkValues)
            // this is the assertion I actually want, but the output is easier to read when split into 2 assertions.
            expectThat(regenerated).isNotNull().shrunkValues.isEqualTo(originalResult.shrunkValues)
        }
    }

    //=== Wiring ===//
    fun ctx(seed: Seed = Seed.random()): GenerationContext

    fun <T> Gen<T>.generate(ctx: GenerationContext): GenResults<T>

    fun <T> Gen<T>.edgeCase(seed: Seed): GenResults<T>?

    /** Retries generations until the exact [value] is produced. */
    fun <T> Gen<T>.generating(value: T): GenResults<T> =
        assertTimeoutPreemptively(ofSeconds(10)) {
            Seed.sequence(Seed.random())
                .map(::ctx)
                .take(1_000_000)
                .map { generate(it) }
                .first { it.value == value }
        }
}

internal class GenResults<T>(
    val value: T,
    val shrinks: Sequence<GenResults<T>>,
) {
    override fun toString(): String {
        return "GenResults(value=$value)"
    }

    val shrunkValues get() = shrinks.map { it.value }.distinct().toList()
}

fun <T> Gen<T>.expectGenerationAndShrinkingToEventuallyComplete() {
    var shrinksBeforeTimeout = -1
    try {
        assertTimeoutPreemptively(ofSeconds(1), "Shrinking took too long") {
            try {
                forAll(TestConfig().withoutReporting(), this) {
                    shrinksBeforeTimeout += 1
                    false
                }
                fail("Expected property to be falsified")
            } catch (e: PropertyFalsifiedException) {
                // do nothing
            }
        }
    } catch (e: Throwable) {
        println("managed $shrinksBeforeTimeout shrinks before exploding")
        throw e
    }
}

internal val <T> Assertion.Builder<GenResults<T>>.value get() = get { value }
internal val <T> Assertion.Builder<GenResults<T>>.shrunkValues get() = get { shrunkValues }.describedAs { "shrunk values: ($this)" }

/**
 * @return true if the property ran. false if the property was skipped
 */
internal fun <T> ignoreSkips(block: () -> T): Boolean =
    try {
        block()
        true
    } catch (e: TestSkippedException) {
        // skip and continue to the next iteration
        false
    }

internal fun repeatTest(property: (Seed) -> Unit) {
    assertTimeoutPreemptively(ofSeconds(2)) {
        var successCount = 0
        var iteration = 0

        while (successCount < 500) {
            iteration++

            val seed = Seed.random()
            try {
                if (ignoreSkips { property(seed) }) successCount += 1
            } catch (e: Throwable) {
                println("Test failed on iteration $iteration - $seed")
                println("Successes beforehand: $successCount")
                throw e
            }
        }
    }
}

@HardcodedTestConfig
@Suppress("unused")
internal fun repeatTest(seed: Long, property: (Seed) -> Unit) {
    assertTimeoutPreemptively(ofSeconds(2)) { property(Seed(seed)) }
}

internal fun skipIteration(): Nothing = throw TestSkippedException()

private class TestSkippedException : AssertionError("Test skipped")

internal fun <T> Gen<T>.collectShrunkValues(
    seed: Seed,
    startShrinkingOnce: (T) -> Boolean = { true },
): Pair<T, List<T>> {
    var originalValue: T? = null
    val seenShrinks = mutableListOf<T>()
    expectThrows<PropertyFalsifiedException> {
        val config = TestConfig()
            .withSeed(seed.value)
            .withShrinkingConstraint(ShrinkingConstraintFactory.infinite())
            .withoutReporting()

        checkAll(config, this) {
            when {
                originalValue != null -> seenShrinks.add(it)
                startShrinkingOnce(it) -> {
                    originalValue = it
                    throw AssertionError("failing to trigger shrinking.")
                }
            }
        }
    }

    return originalValue!! to seenShrinks
}

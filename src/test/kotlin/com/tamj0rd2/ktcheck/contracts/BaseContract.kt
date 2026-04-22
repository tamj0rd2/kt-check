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
import com.tamj0rd2.ktcheck.stats.Percentage
import com.tamj0rd2.ktcheck.stats.Percentage.Companion.percent
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertTimeoutPreemptively
import org.junit.jupiter.api.fail
import org.opentest4j.TestSkippedException
import strikt.api.Assertion
import strikt.api.expectThat
import strikt.assertions.containsExactlyInAnyOrder
import strikt.assertions.isEqualTo
import java.time.Duration
import java.time.Duration.ofSeconds
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

internal interface BaseContract : GenBuilders {
    val exampleGen: Gen<*>?
    val genSupportsShrinking: Boolean get() = true

    fun getGenIfDefined(): Gen<Any> {
        val gen = exampleGen
        Assumptions.assumeTrue(gen != null)
        @Suppress("UNCHECKED_CAST")
        return gen as Gen<Any>
    }

    fun runIfGenSupportsShrinking() =
        Assumptions.assumeTrue(genSupportsShrinking, "skipped as this gen doesn't support shrinking")

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

            expectThat(regenerated).shrunkValues
                .containsExactlyInAnyOrder(originalResult.shrunkValues)
                // this is the assertion I actually want, but the output is easier to read when split into 2 assertions.
                .isEqualTo(originalResult.shrunkValues)
        }
    }

    //=== Wiring ===//
    fun ctx(seed: Seed = Seed.random()): GenerationContext

    @Deprecated("use Gen.collectShrunkValues instead, or samples if shrinks are unnecessary.")
    fun <T> Gen<T>.generate(ctx: GenerationContext): GenResults<T>

    @Deprecated("todo: delete this. generate should produce edge cases now.")
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

    val shrunkValues by lazy { shrinks.map { it.value }.distinct().toList() }
}

@Deprecated("todo: delete this")
// todo: can get rid of this once everything is generating values via forAll/checkAll rather than directly.
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

internal val <T> Assertion.Builder<GenResults<T>>.value get() = get("value: %s") { value }
internal val <T> Assertion.Builder<GenResults<T>>.shrunkValues get() = get("shrunk values: (%s)") { shrunkValues }

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

@Suppress("unused")
internal fun <T> timed(description: String, block: () -> T) = measureTimedValue(block).also {
    println("$description took ${it.duration}")
}.value

internal fun repeatTest(
    testConfig: TestConfig = TestConfig().withIterations(500),
    timeout: Duration = ofSeconds(2),
    property: (Seed) -> Unit,
) {
    var successCount = 0
    var iteration = testConfig.replayIteration ?: 1
    val startingSeed = testConfig.seed
    val timings = mutableMapOf<Int, kotlin.time.Duration>()

    try {
        assertTimeoutPreemptively(timeout) {
            while (successCount < testConfig.effectiveIterations) {
                val seed = startingSeed.next(iteration)
                timings[iteration] = measureTime {
                    if (ignoreSkips { property(seed) }) successCount += 1
                }
                iteration++
            }
        }
    } catch (e: Throwable) {
        println("Test failed on iteration $iteration of $startingSeed")
        println("Successes beforehand: $successCount")
        throw e
    } finally {
        val timingList = timings.toList().sortedBy { it.second }
        println(
            """
            |--------
            |Timings for starting seed ${testConfig.seed.value}:
            |5th percentile:  ${timingList.percentile(5.percent)}
            |Median:          ${timingList.percentile(50.percent)}
            |95th percentile: ${timingList.percentile(95.percent)}
            |--------
            """.trimMargin()
        )
    }
}

fun <T> List<T>.percentile(percentile: Percentage): T? {
    if (this.isEmpty()) return null
    return this[(size * percentile.value).toInt().coerceAtMost(size - 1)]
}

internal fun skipIteration(): Nothing = throw TestSkippedException()

private class TestSkippedException : AssertionError("Test skipped")

@OptIn(HardcodedTestConfig::class)
internal fun <T> Gen<T>.collectShrunkValues(
    seed: Seed,
    startShrinkingOnce: (T) -> Boolean = { true },
): Pair<T, List<T>> {
    var originalValue: T? = null
    val seenShrinks = mutableListOf<T>()
    try {
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
        fail { "property was not falsified" }
    } catch (e: PropertyFalsifiedException) {
        // good
    }

    return originalValue!! to seenShrinks
}

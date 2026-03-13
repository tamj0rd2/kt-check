package com.tamj0rd2.ktcheck.contracts

import com.tamj0rd2.ktcheck.Gen
import com.tamj0rd2.ktcheck.GenBuilders
import com.tamj0rd2.ktcheck.HardcodedTestConfig
import com.tamj0rd2.ktcheck.PropertyFalsifiedException
import com.tamj0rd2.ktcheck.TestConfig
import com.tamj0rd2.ktcheck.core.Seed
import com.tamj0rd2.ktcheck.core.Tree
import com.tamj0rd2.ktcheck.forAll
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
import kotlin.random.Random

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
            val originalResult = gen.generate(tree(seed))
            val regenerated = gen.generate(tree(seed))

            expectThat(regenerated).value.isEqualTo(originalResult.value)
        }
    }

    @Test
    fun `shrinks of generated values are deterministic`() {
        runIfGenSupportsShrinking()

        repeatTest { seed ->
            val gen = getGenIfDefined()
            val originalResult = gen.generate(tree(seed))
            val regenerated = gen.generate(tree(seed))

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
            val originalResult = gen.edgeCases(seed).random(Random(seed.value))
            val regenerated = gen.edgeCases(seed).random(Random(seed.value))

            expectThat(regenerated).value.isEqualTo(originalResult.value)
        }
    }

    @Test
    fun `shrinks of edge cases are deterministic`() {
        runIfGenSupportsEdgeCases()
        runIfGenSupportsShrinking()

        repeatTest { seed ->
            val gen = getGenIfDefined()
            val originalResult = gen.edgeCases(seed).random(Random(seed.value))
            val regenerated = gen.edgeCases(seed).random(Random(seed.value))

            expectThat(regenerated).shrunkValues.containsExactlyInAnyOrder(originalResult.shrunkValues)
            // this is the assertion I actually want, but the output is easier to read when split into 2 assertions.
            expectThat(regenerated).shrunkValues.isEqualTo(originalResult.shrunkValues)
        }
    }

    //=== Wiring ===//
    fun tree(seed: Seed = Seed.random()): Tree<*>
    fun Tree<*>.withLeft(left: Tree<*>): Tree<*>
    fun Tree<*>.withRight(right: Tree<*>): Tree<*>

    fun trees(seed: Seed = Seed.random()) =
        Seed.sequence(seed).map(::tree)

    fun treeWhere(seed: Seed = Seed.random(), predicate: (Tree<*>) -> Boolean): Tree<*> =
        trees(seed).take(1_000_000).first(predicate)

    fun <T> Gen<T>.generate(tree: Tree<*> = tree()): GenResults<T>

    fun <T> Gen<T>.edgeCases(seed: Seed = Seed.random()): List<GenResults<T>>

    fun <T> Gen<T>.sequence(): Sequence<GenResults<T>> =
        generateSequence { generate() }

    /** Retries generations until the exact [value] is produced. */
    fun <T> Gen<T>.generating(value: T): GenResults<T> =
        generating { it == value }

    /** Retries generations until some value satisfying [predicate] is produced. */
    fun <T> Gen<T>.generating(predicate: (T) -> Boolean): GenResults<T> =
        generate(findTreeProducing(Seed.random(), predicate))

    fun <T> Gen<T>.findTreeProducing(value: T, seed: Seed = Seed.random()): Tree<*> =
        findTreeProducing(seed) { it == value }

    fun <T> Gen<T>.findTreeProducing(seed: Seed = Seed.random(), predicate: (T) -> Boolean): Tree<*> =
        assertTimeoutPreemptively(Duration.ofSeconds(10)) {
            treeWhere(seed) { predicate(generate(it).value) }
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
        assertTimeoutPreemptively(Duration.ofSeconds(1), "Shrinking took too long") {
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
    assertTimeoutPreemptively(Duration.ofSeconds(2)) {
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
    assertTimeoutPreemptively(Duration.ofSeconds(2)) { property(Seed(seed)) }
}

internal fun skipIteration(): Nothing = throw TestSkippedException()

private class TestSkippedException : AssertionError("Test skipped")

package com.tamj0rd2.ktcheck.contracts

import com.tamj0rd2.ktcheck.Gen
import com.tamj0rd2.ktcheck.GenBuilders
import com.tamj0rd2.ktcheck.NoOpTestReporter
import com.tamj0rd2.ktcheck.PropertyFalsifiedException
import com.tamj0rd2.ktcheck.TestConfig
import com.tamj0rd2.ktcheck.core.Seed
import com.tamj0rd2.ktcheck.current.RandomTree
import com.tamj0rd2.ktcheck.forAll
import org.junit.jupiter.api.assertTimeoutPreemptively
import org.junit.jupiter.api.fail
import strikt.api.Assertion
import strikt.api.expectThat
import strikt.assertions.isNotNull
import java.time.Duration

internal interface BaseContract : GenBuilders {
    fun tree(seed: Seed = Seed.random()): RandomTree

    fun trees(seed: Seed = Seed.random()) =
        Seed.sequence(seed).map(::tree)

    fun treeWhere(seed: Seed = Seed.random(), predicate: (RandomTree) -> Boolean): RandomTree =
        trees(seed).take(1_000_000).first(predicate)

    fun <T> Gen<T>.generate(tree: RandomTree = tree()): GenResults<T>

    fun <T> Gen<T>.edgeCases(): List<GenResults<T>>

    fun <T> Gen<T>.sequence(): Sequence<GenResults<T>> =
        generateSequence { generate() }

    /** Retries generations until the exact [value] is produced. */
    fun <T> Gen<T>.generating(value: T): GenResults<T> =
        generating { it == value }

    /** Retries generations until some value satisfying [predicate] is produced. */
    fun <T> Gen<T>.generating(predicate: (T) -> Boolean): GenResults<T> =
        generate(findTreeProducing(Seed.random(), predicate))

    fun <T> Gen<T>.findTreeProducing(value: T, seed: Seed = Seed.random()): RandomTree =
        findTreeProducing(seed) { it == value }

    fun <T> Gen<T>.findTreeProducing(seed: Seed = Seed.random(), predicate: (T) -> Boolean): RandomTree =
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

fun <T> Gen<T>.expectGenerationAndShrinkingToEventuallyComplete(shrunkValueRequired: Boolean = true) {
    var shrinksBeforeTimeout = -1
    try {
        assertTimeoutPreemptively(Duration.ofSeconds(1), "Shrinking took too long") {
            val ex = try {
                forAll(TestConfig().withReporter(NoOpTestReporter), this) {
                    shrinksBeforeTimeout += 1
                    false
                }
                fail("Expected property to be falsified")
            } catch (e: PropertyFalsifiedException) {
                e
            }

            if (shrunkValueRequired) {
                expectThat(ex).get { shrunk }.isNotNull()
            }
        }
    } catch (e: Throwable) {
        println("managed $shrinksBeforeTimeout shrinks before exploding")
        throw e
    }
}

internal val <T> Assertion.Builder<GenResults<T>>.value get() = get { value }
internal val <T> Assertion.Builder<GenResults<T>>.shrunkValues get() = get { shrunkValues }.describedAs { "shrunk values: ($this)" }

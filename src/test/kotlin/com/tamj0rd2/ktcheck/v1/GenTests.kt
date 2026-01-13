package com.tamj0rd2.ktcheck.v1

import com.tamj0rd2.ktcheck.NoOpTestReporter
import com.tamj0rd2.ktcheck.PropertyFalsifiedException
import com.tamj0rd2.ktcheck.TestConfig
import com.tamj0rd2.ktcheck.contracts.CommonGeneratorTestContract
import com.tamj0rd2.ktcheck.core.ProducerTree
import com.tamj0rd2.ktcheck.forAll
import org.junit.jupiter.api.assertTimeoutPreemptively
import strikt.api.expectThrows
import strikt.assertions.isNotNull
import java.time.Duration

internal class GenTests : BaseGenTest(), CommonGeneratorTestContract {

    companion object {
        /** For testing purposes only: generates a value along with all its shrunk values as a list. */
        internal fun <T> GenV1<T>.generateWithShrunkValues(tree: ProducerTree): Pair<T, List<T>> {
            val (value, shrinks) = generate(tree, GenMode.Initial)
            return value to shrinks.map { generate(it, GenMode.Shrinking).value }.toList()
        }

        internal fun <T> GenV1<T>.expectGenerationAndShrinkingToEventuallyComplete(
            shrunkValueRequired: Boolean = true,
        ) {
            var shrinksBeforeTimeout = -1
            try {
                assertTimeoutPreemptively(Duration.ofSeconds(1), "Shrinking took too long") {
                    val ex = expectThrows<PropertyFalsifiedException> {
                        forAll(TestConfig().withReporter(NoOpTestReporter), this) {
                            shrinksBeforeTimeout += 1
                            false
                        }
                    }

                    if (shrunkValueRequired) {
                        ex.get { shrunkResult }.isNotNull()
                    }
                }
            } catch (e: Throwable) {
                println("managed $shrinksBeforeTimeout shrinks before exploding")
                throw e
            }
        }
    }
}

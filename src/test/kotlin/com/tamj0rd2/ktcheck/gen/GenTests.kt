package com.tamj0rd2.ktcheck.gen

import com.tamj0rd2.ktcheck.gen.Gen.Companion.samples
import com.tamj0rd2.ktcheck.producer.ProducerTree
import com.tamj0rd2.ktcheck.producer.ProducerTreeDsl.Companion.producerTree
import com.tamj0rd2.ktcheck.testing.NoOpTestReporter
import com.tamj0rd2.ktcheck.testing.PropertyFalsifiedException
import com.tamj0rd2.ktcheck.testing.TestConfig
import com.tamj0rd2.ktcheck.testing.forAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertTimeoutPreemptively
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.contains
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull
import java.time.Duration

class GenTests {
    @Nested
    inner class MapTests {
        @Test
        fun `map maps the original value and shrinks`() {
            val originalGen = Gen.int(0..10)
            val doublingGen = originalGen.map { it * 2 }

            val tree = ProducerTree.new()
            val (originalNumber, originalShrinks) = originalGen.generateWithShrunkValues(tree)
            val (doubledValue, doubledShrinks) = doublingGen.generateWithShrunkValues(tree)

            expectThat(doubledValue).isEqualTo(originalNumber * 2)
            expectThat(doubledShrinks).isEqualTo(originalShrinks.map { it * 2 })
        }
    }

    @Nested
    inner class FlatMapTests {
        @Test
        fun `flatMap generates the second value based on the first`() {
            val smallGen = Gen.int(0..5)
            val bigGen = Gen.int(10..20)
            val gen = smallGen.flatMap { a -> bigGen.map { b -> a + b } }

            val tree = producerTree {
                left(5)
                right(20)
            }

            val value = gen.generate(tree, GenMode.Initial).value
            expectThat(value).isEqualTo(25)
        }

        @Test
        fun `flatMap combines shrinks from both generators`() {
            val smallGen = Gen.int(1..3)
            val biggerGen = Gen.int(4..6)
            val gen = smallGen.flatMap { a -> biggerGen.map { b -> a + b } }

            val tree = producerTree {
                left(3)
                right(6)
            }

            val (value, shrinks) = gen.generateWithShrunkValues(tree)
            expectThat(value).isEqualTo(9)

            val threeShrunk = shrink(3, range = 1..3)
            val sixShrunk = shrink(6, range = 4..6)

            expectThat(shrinks).contains(threeShrunk.map { it + 6 }.toList())
            expectThat(shrinks).contains(sixShrunk.map { it + 3 }.toList())
        }
    }

    @Nested
    inner class CombineWithTests {
        @Test
        fun `combineWith merges two independent generators`() {
            val smallGen = Gen.int(0..5)
            val bigGen = Gen.int(10..20)
            val gen = smallGen.combineWith(bigGen) { a, b -> a + b }

            val tree = producerTree {
                left(5)
                right(20)
            }

            val value = gen.generate(tree, GenMode.Initial).value
            expectThat(value).isEqualTo(25)
        }

        @Test
        fun `combineWith combines shrinks from both generators`() {
            val smallGen = Gen.int(1..3)
            val bigGen = Gen.int(4..6)
            val gen = smallGen.combineWith(bigGen) { a, b -> a + b }

            val tree = producerTree {
                left(3)
                right(6)
            }

            val (value, shrinks) = gen.generateWithShrunkValues(tree)
            expectThat(value).isEqualTo(9)

            val threeShrunk = shrink(3, range = 1..3)
            val sixShrunk = shrink(6, range = 4..6)

            expectThat(shrinks).contains(threeShrunk.map { it + 6 }.toList())
            expectThat(shrinks).contains(sixShrunk.map { it + 3 }.toList())
        }
    }

    @Nested
    inner class SamplingTests {
        @Test
        fun `same seed produces same sample`() {
            val seed = 12345L
            val gen = Gen.int(-1000..1000)

            val firstRun = gen.samples(seed).take(100).toList()
            val secondRun = gen.samples(seed).take(100).toList()

            expectThat(secondRun).isEqualTo(firstRun)
        }
    }

    companion object {
        /** For testing purposes only: generates a value along with all its shrunk values as a list. */
        internal fun <T> Gen<T>.generateWithShrunkValues(tree: ProducerTree): Pair<T, List<T>> {
            val (value, shrinks) = generate(tree, GenMode.Initial)
            return value to shrinks.map { generate(it, GenMode.Shrinking).value }.toList()
        }

        internal fun <T> Gen<T>.expectGenerationAndShrinkingToEventuallyComplete(
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

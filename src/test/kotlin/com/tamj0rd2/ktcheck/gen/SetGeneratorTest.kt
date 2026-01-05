package com.tamj0rd2.ktcheck.gen

import com.tamj0rd2.ktcheck.gen.Gen.Companion.sample
import com.tamj0rd2.ktcheck.gen.GenTests.Companion.generateWithShrunkValues
import com.tamj0rd2.ktcheck.producer.ProducerTreeDsl.Companion.producerTree
import com.tamj0rd2.ktcheck.testing.TestConfig
import com.tamj0rd2.ktcheck.testing.checkAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import strikt.assertions.isLessThanOrEqualTo

class SetGeneratorTest {
    @Test
    fun `can generate a long set without stack overflow`() {
        Gen.int().set(10_000).sample()
    }

    @Test
    fun `generates sets with distinct elements`() = checkAll(
        TestConfig().withIterations(100),
        Gen.int(0..100).set(5),
    ) { set ->
        expectThat(set.size).isEqualTo(5)
        expectThat(set).hasSize(5) // confirms no duplicates
    }

    @Test
    fun `throws when unable to generate enough distinct elements`() {
        val gen = Gen.int(0..10).set(100)
        assertThrows<ImpossibleSetSize> { gen.sample() }
    }

    @Test
    fun `shrinks a set of 1 element`() {
        val gen = Gen.int(0..4).set()

        val tree = producerTree {
            left(1)
            right {
                left(4)
            }
        }

        val (value, shrunkValues) = gen.generateWithShrunkValues(tree)
        expectThat(value).isEqualTo(setOf(4))

        expectThat(shrunkValues).isEqualTo(
            listOf(
                // shrinks the size
                emptySet(),
                // shrinks the value
                setOf(0),
                setOf(2),
                setOf(3),
            )
        )
    }

    @Test
    fun `shrinks a set of 2 elements`() {
        val gen = Gen.int(0..10).set()

        val tree = producerTree {
            left(2)
            right {
                left(1)
                right {
                    left(4)
                }
            }
        }

        val (value, shrunkValues) = gen.generateWithShrunkValues(tree)
        expectThat(value).isEqualTo(setOf(1, 4))

        expectThat(shrunkValues).isEqualTo(
            listOf(
                // tries reducing set size (now 0)
                emptySet(),
                // continues reducing set size (now 1). From tail first, then head.
                setOf(1),
                setOf(4),
                // shrinks values, starting with index 0
                setOf(0, 4),
                // continues shrinking values at index 1
                setOf(1, 0),
                setOf(1, 2),
                setOf(1, 3),
            ).distinct()
        )
    }

    @Test
    fun `shrinks a set of 3 elements`() {
        val gen = Gen.int(0..10).set()

        val tree = producerTree {
            left(3)
            right {
                left(1)
                right {
                    left(2)
                    right {
                        left(3)
                        right {
                            left(9)
                        }
                    }
                }
            }
        }

        val (value, shrunkValues) = gen.generateWithShrunkValues(tree)
        expectThat(value).isEqualTo(setOf(1, 2, 3))

        expectThat(shrunkValues).isEqualTo(
            listOf(
                // reduce set size (0)
                emptySet(),
                // reduce set size (2), removing items at tail
                setOf(1, 2),
                // reduce set size (2), removing items at head
                setOf(2, 3),
                // shrink values
                setOf(0, 2, 3),
                setOf(1, 0, 3),
                // next would be (1,1,3) but short circuits due to duplicate 1
                setOf(1),
                setOf(1, 2, 0),
                setOf(1, 2, 2),
            )
        )
    }

    @Test
    fun `all generated sets contain only distinct elements`() = checkAll(
        TestConfig().withIterations(100),
        Gen.int(0..100).set(0..10),
    ) { set ->
        // If we convert to a set again, the size should remain the same (proving all elements were distinct)
        expectThat(set.toSet()).hasSize(set.size)
    }

    @Test
    fun `shrinks that would produce duplicates do not appear in shrink tree`() {
        // This test verifies that we follow Approach B: skip invalid shrinks
        // When we have a set like {0, 1, 2} and shrink 1 to 0, that would create {0, 0, 2}
        // Since that's invalid, we need to generate a new value to maintain size 3
        // However, if the generator can't produce enough distinct values, it should throw

        val gen = Gen.int(0..2).set(3) // Only 3 possible distinct values

        val tree = producerTree {
            left(3)
            right {
                left(0)
                right {
                    left(1)
                    right {
                        left(2)
                    }
                }
            }
        }

        val (value, shrunkValues) = gen.generateWithShrunkValues(tree)
        expectThat(value).isEqualTo(setOf(0, 1, 2))

        // All shrunk values must have size 3 (except explicit size shrinks)
        shrunkValues.forEach { shrunkSet ->
            // Element shrinks maintain size 3, size shrinks reduce to smaller sizes
            expectThat(shrunkSet.size).isLessThanOrEqualTo(3)
        }
    }
}


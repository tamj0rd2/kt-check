package com.tamj0rd2.ktcheck.gen

import com.tamj0rd2.ktcheck.gen.GenTests.Companion.generateWithShrunkValues
import com.tamj0rd2.ktcheck.producer.ProducerTreeDsl.Companion.producerTree
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class ListGeneratorTest {
    @Test
    fun `can generate a long list without stack overflow`() {
        Gen.constant(1).list(10_000).sample()
    }

    @Test
    fun `shrinks a list of 1 element`() {
        val gen = Gen.int(0..4).list()

        val tree = producerTree {
            left(1)
            right {
                left(4)
            }
        }

        val (value, shrunkValues) = gen.generateWithShrunkValues(tree)
        expectThat(value).isEqualTo(listOf(4))

        expectThat(shrunkValues).isEqualTo(
            listOf(
                // shrinks the size
                emptyList(),
                // shrinks the value
                listOf(0),
                listOf(2),
                listOf(3),
            )
        )
    }

    @Test
    fun `shrinks a list of 2 elements`() {
        val gen = Gen.int(0..5).list()

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
        expectThat(value).isEqualTo(listOf(1, 4))

        // 4 shrinks to 0, 2, 3
        // 3 shrinks to 0, 2
        // 2 shrinks to 0, 1
        // 1 shrinks to 0

        expectThat(shrunkValues).isEqualTo(
            listOf(
                // tries reducing list size (now 0)
                emptyList(),
                // continues reducing list size (now 1). From tail first, then head.
                listOf(1),
                listOf(4),
                // shrinks values, starting with index 1
                listOf(0, 4),
                // continues shrinking values at index 2
                listOf(1, 0),
                listOf(1, 2),
                listOf(1, 3),
            ).distinct()
        )
    }

    @Test
    fun `shrinks a list of 3 elements`() {
        val gen = Gen.int(0..4).list()

        val tree = producerTree {
            left(3)
            right {
                left(1)
                right {
                    left(2)
                    right {
                        left(3)
                    }
                }
            }
        }

        val (value, shrunkValues) = gen.generateWithShrunkValues(tree)
        expectThat(value).isEqualTo(listOf(1, 2, 3))

        // 3 shrinks to 0 and 2
        // 2 shrinks to 0 and 1
        // 1 shrinks to 0

        expectThat(shrunkValues).isEqualTo(
            listOf(
                // reduce list size (0)
                listOf(),
                // reduce list size (2), removing items at tail
                listOf(1, 2),
                // reduce list size (2), removing items at head
                listOf(2, 3),
                // shrink values
                listOf(0, 2, 3),
                listOf(1, 0, 3),
                listOf(1, 1, 3),
                listOf(1, 2, 0),
                listOf(1, 2, 2),
            )
        )
    }
}

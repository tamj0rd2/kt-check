package com.tamj0rd2.ktcheck.gen

import com.tamj0rd2.ktcheck.gen.Gen.Companion.sample
import com.tamj0rd2.ktcheck.producer.ProducerTree
import com.tamj0rd2.ktcheck.testing.TestConfig
import com.tamj0rd2.ktcheck.testing.checkAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertTimeoutPreemptively
import strikt.api.Assertion.Builder
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isNotEqualTo
import java.time.Duration

class ListGeneratorTest {
    @Test
    fun `can generate a long list without stack overflow`() {
        Gen.constant(1).list(10_000).sample()
    }

    @Test
    fun `shrinks a list of 1 element`() {
        val gen = Gen.int(0..4).list()

        val tree = ProducerTree.new()
            .withLeftValue(1)
            .withRight { withLeftValue(4) }

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

        val tree = ProducerTree.new()
            .withLeftValue(2)
            .withRight {
                this
                    .withLeftValue(1)
                    .withRight {
                        withLeftValue(4)
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

        val tree = ProducerTree.new()
            .withLeftValue(3)
            .withRight {
                this
                    .withLeftValue(1)
                    .withRight {
                        this
                            .withLeftValue(2)
                            .withRight {
                                withLeftValue(3)
                            }
                    }
            }

        val (value, shrunkValues) = gen.generateWithShrunkValues(tree)
        expectThat(value).isEqualTo(listOf(1, 2, 3))

        // 3 shrinks to 0 and 2
        // 2 shrinks to 0 and 1
        // 1 shrinks to 0

        expectThat(shrunkValues.toList()).isEqualTo(
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

    @Test
    fun `when a fixed sized list is shrunk, the number of elements stay the same`() = checkAll(
        TestConfig().withIterations(100),
        Gen.int(1..100),
    ) { size ->
        val (originalList, shrunkLists) = Gen.int().list(size)
            .generateWithDepthFirstShrinks(ProducerTree.new(), limit = 1000)
        expectThat(originalList.size).isEqualTo(size)

        assertTimeoutPreemptively(Duration.ofMillis(100)) {
            shrunkLists.forEach { shrunkList ->
                expectThat(shrunkList.size).isEqualTo(originalList.size)
            }
        }
    }

    @Test
    fun `when a fixed size list is shrunk, only one element changes at a time`() = checkAll(
        TestConfig().withIterations(100),
        Gen.int(1..100),
    ) { size ->
        val (originalList, shrunkLists) = Gen.int().list(size).generateWithShrunkValues(ProducerTree.new())

        assertTimeoutPreemptively(Duration.ofMillis(100)) {
            shrunkLists.forEach { shrunkList ->
                expectThat(shrunkList).exactly(1) { i -> isNotEqualTo(originalList[i]) }
            }
        }
    }

    companion object {
        private fun <T : Iterable<E>, E> Builder<T>.exactly(
            count: Int,
            predicate: Builder<E>.(Int) -> Unit,
        ): Builder<T> =
            compose("exactly $count elements match:") { subject ->
                subject.forEachIndexed { i, element ->
                    get("%s") { element }.apply { predicate(i) }
                }
            } then {
                if (passedCount == count) pass() else fail()
            }

        // generates the value and all shrinks depth-first. its done this way to avoid stack overflows and OOMs on large shrink trees.
        internal fun <T> Gen<T>.generateWithDepthFirstShrinks(
            tree: ProducerTree,
            limit: Int = 100_000,
        ): Pair<T, List<T>> {
            val collection = sequence {
                // Stack of iterators tracking our position in each level of the tree
                val stack = ArrayDeque<Iterator<ProducerTree>>()
                stack.addFirst(sequenceOf(tree).iterator())

                while (stack.isNotEmpty()) {
                    val currentIterator = stack.first()

                    if (!currentIterator.hasNext()) {
                        // Exhausted this level, pop it and backtrack
                        stack.removeFirst()
                        continue
                    }

                    val currentTree = currentIterator.next()
                    val (value, shrinks) = generate(currentTree)
                    yield(value)

                    // Push shrinks iterator onto stack to continue exploring depth-first
                    val shrinksIterator = shrinks.iterator()
                    if (shrinksIterator.hasNext()) {
                        stack.addFirst(shrinksIterator)
                    }
                }
            }

            // todo: when shrinking, there are millions of duplicates being produced. it might just be the reality of depth first shrinking though.
            val all = collection.take(limit).toList().distinct()
            return all.first() to all.drop(1)
        }
    }
}

package com.tamj0rd2.ktcheck.genv2

import com.tamj0rd2.ktcheck.genv2.Gen.Companion.sample
import com.tamj0rd2.ktcheck.testing.TestConfig
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertTimeoutPreemptively
import strikt.api.Assertion.Builder
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isNotEqualTo
import java.time.Duration

class ListGeneratorTest {
    private fun <T> ValueTree.Companion.`that will generate value using left tree`(gen: Gen<T>, value: T) =
        generateSequence(0) { it + 1 }
            .map { ValueTree.fromSeed(it.toLong()) }
            .first { gen.generate(it).value == value }

    @Test
    fun `can generate a long list without stack overflow`() {
        Gen.constant(1).list(10_000).sample()
    }

    @Test
    fun `shrinks a list of 1 element depth first`() {
        val gen = Gen.int(0..4).list(1)

        val tree = ValueTree.`that will generate value using left tree`(gen, listOf(4))
        val (value, shrunkValues) = gen.generateAllIncludingShrinks(tree)
        expectThat(value).isEqualTo(listOf(4))

        expectThat(shrunkValues).isEqualTo(
            listOf(
                // first shrink of 4 is 0
                listOf(0),
                // second shrink of 4 is 2
                listOf(2),
                // first shrink of 2 is 1
                listOf(1),
                // third shrink of 4 is 3
                listOf(3),
            )
        )
    }

    @Test
    fun `shrinks a list of 2 elements depth first`() {
        val gen = Gen.int(0..5).list(2)

        val tree = ValueTree.`that will generate value using left tree`(gen, listOf(1, 4))
        val (value, shrunkValues) = gen.generateAllIncludingShrinks(tree)
        expectThat(value).isEqualTo(listOf(1, 4))

        // 4 shrinks to 0, 2, 3
        // 3 shrinks to 0, 2
        // 2 shrinks to 0, 1
        // 1 shrinks to 0

        expectThat(shrunkValues).isEqualTo(
            listOf(
                listOf(0, 4),
                listOf(0, 0),
                listOf(0, 2),
                listOf(0, 1),
                listOf(0, 3),
                listOf(1, 0),
                listOf(1, 2),
                listOf(1, 1),
                listOf(1, 3),
            )
        )
    }

    @Test
    fun `shrinks a list of 3 elements depth first`() {
        val gen = Gen.int(0..4).list(3)

        val (value, shrunkValues) = gen.generateAllIncludingShrinks(ValueTree.fromSeed(2))
        expectThat(value).isEqualTo(listOf(2, 0, 3))

        // 3 shrinks to 0 and 2
        // 2 shrinks to 0 and 1
        // 1 shrinks to 0

        expectThat(shrunkValues.toList()).isEqualTo(
            listOf(
                listOf(0, 0, 3),
                listOf(0, 0, 0),
                listOf(0, 0, 2),
                listOf(0, 0, 1),
                listOf(1, 0, 3),
                listOf(1, 0, 0),
                listOf(1, 0, 2),
                listOf(1, 0, 1),
                listOf(2, 0, 0),
                listOf(2, 0, 2),
                listOf(2, 0, 1),
            )
        )
    }

    @Test
    fun `when a fixed sized list is shrunk, the number of elements stay the same`() = checkAll(
        TestConfig().withIterations(100),
        Gen.int(1..100),
    ) { size ->
        val (originalList, shrunkLists) = Gen.int().list(size)
            .generateAllIncludingShrinks(ValueTree.fromSeed(0))
        expectThat(originalList.size).isEqualTo(size)

        assertTimeoutPreemptively(Duration.ofMillis(100)) {
            shrunkLists.forEach { shrunkList ->
                expectThat(shrunkList.size).isEqualTo(originalList.size)
            }
        }
    }

    @Test
    fun `when a fixed size list is shrunk by depth of 1, only one element changes at a time`() = checkAll(
        TestConfig().withIterations(100),
        Gen.int(1..100),
    ) { size ->
        val (originalList, shrunkLists) = Gen.int().list(size)
            .generateAllIncludingShrinks(ValueTree.fromSeed(0), maxDepth = 1)

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
        internal fun <T> Gen<T>.generateAllIncludingShrinks(
            tree: ValueTree,
            maxDepth: Int? = null,
        ): Pair<T, List<T>> {
            val collection = sequence {
                // Stack of iterators tracking our position in each level of the tree
                val stack = ArrayDeque<Iterator<ValueTree>>()
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

                    if (maxDepth != null && stack.size > maxDepth) {
                        continue
                    }

                    // Push shrinks iterator onto stack to continue exploring depth-first
                    val shrinksIterator = shrinks.iterator()
                    if (shrinksIterator.hasNext()) {
                        stack.addFirst(shrinksIterator)
                    }
                }
            }

            // todo: when shrinking, there are millions of duplicates being produced. it might just be the reality of depth first shrinking though.
            val all = collection.take(1000).toList().distinct()
            return all.first() to all.drop(1)
        }
    }
}

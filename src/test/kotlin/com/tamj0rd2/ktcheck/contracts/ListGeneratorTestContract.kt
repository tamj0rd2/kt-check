package com.tamj0rd2.ktcheck.contracts

import com.tamj0rd2.ktcheck.contract.IGen
import com.tamj0rd2.ktcheck.gen.DistinctCollectionSizeImpossible
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import strikt.api.expectThat
import strikt.assertions.all
import strikt.assertions.contains
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull

internal interface ListGeneratorTestContract : BaseGeneratorContract {
    // todo: if there was an IListGen interface, this could be an extension on that instead. I know that different
    //  generators consume rng values differently.
    fun <T : Any> IGen<T>.generateWithShrunkValuesForListGen(rngValues: List<Any>): Pair<T, List<T>>

    @Test
    fun `can generate a long list without stack overflow`() {
        constantGen(1).listGen(10_000).sample()
    }

    @Test
    fun `empty list has no shrinks`() {
        val gen = intGen(0..5).listGen()
        val nav = gen.navigateRecursiveShrinks(listOf(0))  // size = 0

        expectThat(nav.value).isEmpty()
        expectThat(nav.getShrinks()).isEmpty()
    }

    @Test
    fun `single minimal element shrinks recursively`() {
        val gen = intGen(0..5).listGen()
        val nav = gen.navigateRecursiveShrinks(listOf(1, 0))  // [0]

        expectThat(nav.value).isEqualTo(listOf(0))

        // Find the empty list shrink
        val emptyListNav = nav.findShrinkByValue(emptyList())
        expectThat(emptyListNav).isNotNull()

        // Verify [] has no further shrinks
        expectThat(emptyListNav!!.value).isEmpty()
        expectThat(emptyListNav.getShrinks()).isEmpty()
    }

    @Test
    fun `single non-minimal element shrinks recursively`() {
        val gen = intGen(0..5).listGen()
        val nav = gen.navigateRecursiveShrinks(listOf(1, 4))  // [4]

        expectThat(nav.value).isEqualTo(listOf(4))

        // Find the [0] shrink (element shrink)
        val listOf0Nav = nav.findShrinkByValue(listOf(0))
        expectThat(listOf0Nav).isNotNull()

        // Level 2: [0] should shrink to []
        val listOf0Shrinks = listOf0Nav!!.getShrinks(limit = 5)
        expectThat(listOf0Shrinks.map { it.value }).contains(listOf(emptyList<Int>()))

        // Find the [2] shrink (another element shrink)
        val listOf2Nav = nav.findShrinkByValue(listOf(2))
        expectThat(listOf2Nav).isNotNull()

        // Level 2: [2] should shrink to [] (size) and [0], [1] (element)
        val listOf2Shrinks = listOf2Nav!!.getShrinks(limit = 5)
        expectThat(listOf2Shrinks.map { it.value }).contains(
            emptyList<Int>(),  // size shrink
            listOf(0),    // element shrink
            listOf(1),    // element shrink
        )
    }

    @Test
    fun `two-element list shrinks recursively`() {
        val gen = intGen(0..5).listGen()
        val nav = gen.navigateRecursiveShrinks(listOf(2, 1, 4))  // [1, 4]

        expectThat(nav.value).isEqualTo(listOf(1, 4))

        // Check that [1] (tail removal) has recursive shrinks
        val listOf1Nav = nav.findShrinkByValue(listOf(1))
        expectThat(listOf1Nav).isNotNull()

        val listOf1Shrinks = listOf1Nav!!.getShrinks(limit = 5)
        expectThat(listOf1Shrinks.map { it.value }).contains(
            emptyList<Int>(),  // size shrink
            listOf(0),    // element shrink
        )

        // Check that [4] (head removal) has recursive shrinks
        val listOf4Nav = nav.findShrinkByValue(listOf(4))
        expectThat(listOf4Nav).isNotNull()

        val listOf4Shrinks = listOf4Nav!!.getShrinks(limit = 10)
        expectThat(listOf4Shrinks.map { it.value }).contains(
            emptyList<Int>(),  // size shrink
            listOf(0),    // element shrink
            listOf(2),    // element shrink
        )

        // Check that [0, 4] (element shrink) has recursive shrinks
        val listOf0_4Nav = nav.findShrinkByValue(listOf(0, 4))
        expectThat(listOf0_4Nav).isNotNull()

        val listOf0_4Shrinks = listOf0_4Nav!!.getShrinks(limit = 10)
        expectThat(listOf0_4Shrinks.map { it.value }).contains(
            emptyList<Int>(),  // size shrink
            listOf(0),    // size shrink (tail removal)
            listOf(4),    // size shrink (head removal)
            listOf(0, 0), // element shrink
        )
    }

    @Test
    fun `three-level depth test`() {
        val gen = intGen(0..5).listGen()
        val nav = gen.navigateRecursiveShrinks(listOf(2, 2, 4))  // [2, 4]

        expectThat(nav.value).isEqualTo(listOf(2, 4))

        // Level 1: Find [2]
        val listOf2Nav = nav.findShrinkByValue(listOf(2))
        expectThat(listOf2Nav).isNotNull()

        // Level 2: [2] should shrink to [1] (among others)
        val listOf1Nav = listOf2Nav!!.findShrinkByValue(listOf(1), limit = 10)
        expectThat(listOf1Nav).isNotNull()

        // Level 3: [1] should shrink to [0]
        val level3Shrinks = listOf1Nav!!.getShrinks(limit = 10)
        expectThat(level3Shrinks.map { it.value }).contains(listOf(listOf(0)))

        // Level 4: [0] should shrink to []
        val listOf0Nav = listOf1Nav.findShrinkByValue(listOf(0), limit = 10)
        expectThat(listOf0Nav).isNotNull()

        val level4Shrinks = listOf0Nav!!.getShrinks(limit = 5)
        expectThat(level4Shrinks.map { it.value }).contains(listOf(emptyList<Int>()))
    }

    @Test
    fun `when all elements are minimal - only size shrinks recursively`() {
        val gen = intGen(0..5).listGen()
        val nav = gen.navigateRecursiveShrinks(listOf(3, 0, 0, 0))  // [0, 0, 0]

        expectThat(nav.value).isEqualTo(listOf(0, 0, 0))

        val level1Shrinks = nav.getShrinks(limit = 10)

        // Should have size shrinks but no element shrinks
        // We can identify this by checking all shrinks are shorter lists of 0s
        expectThat(level1Shrinks.map { it.value }).all {
            get { all { it == 0 } }.isEqualTo(true)
        }

        // Find [0, 0] and verify it has recursive shrinks
        val listOf0_0Nav = nav.findShrinkByValue(listOf(0, 0))
        expectThat(listOf0_0Nav).isNotNull()

        val level2Shrinks = listOf0_0Nav!!.getShrinks(limit = 5)
        expectThat(level2Shrinks.map { it.value }).contains(
            emptyList<Int>(),
            listOf(0),
        )
    }

    // ========== Distinct List Tests ==========

    @Test
    fun `generates lists with distinct elements when distinct=true`() {
        // Test with multiple examples to verify distinctness
        listOf(
            listOf(5, 1, 4, 7, 9, 2) to 5,      // 5 distinct elements
            listOf(3, 10, 20, 30) to 3,          // 3 distinct elements
            listOf(1, 42) to 1,                  // 1 distinct element
        ).forEach { (rngValues, expectedSize) ->
            val gen = intGen(0..100).listGen(size = expectedSize, distinct = true)
            val (value, _) = gen.generateWithShrunkValuesForListGen(rngValues)

            expectThat(value.size).isEqualTo(expectedSize)
            expectThat(value.toSet().size).isEqualTo(expectedSize) // Confirms no duplicates
        }
    }

    @Test
    fun `distinct list shrinks maintain distinctness`() {
        val gen = intGen(0..10).listGen(distinct = true)

        // Test with a few specific examples of different sizes
        listOf(
            listOf(1, 4),           // 1-element list
            listOf(2, 1, 4),        // 2-element list
            listOf(3, 1, 4, 7),     // 3-element list
        ).forEach { rngValues ->
            val (value, shrinks) = gen.generateWithShrunkValuesForListGen(rngValues)

            // Original value should be distinct
            expectThat(value.toSet().size).isEqualTo(value.size)

            // All shrinks should also be distinct
            shrinks.forEach { shrunkList ->
                expectThat(shrunkList.toSet().size).isEqualTo(shrunkList.size)
            }
        }
    }

    @Test
    fun `throws when unable to generate enough distinct elements`() {
        val gen = intGen(0..10).listGen(size = 100, distinct = true)

        assertThrows<DistinctCollectionSizeImpossible> { gen.sample() }
    }
}

package com.tamj0rd2.ktcheck.v2

import com.tamj0rd2.ktcheck.producer.Seed
import com.tamj0rd2.ktcheck.v2.Gen.Companion.samples
import com.tamj0rd2.ktcheck.v2.IntGenerator.Companion.int
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class GenTests {
    @Nested
    inner class MapTests {
        @Test
        fun `map maps the original value and shrinks`() {
            val originalGen = Gen.int(0..10)
            val doublingGen = originalGen.map { it * 2 }

            val seed = Seed.random()
            val (originalValue, originalShrinks) = originalGen.generateWithShrunkValues(seed)
            val (doubledValue, doubledShrinks) = doublingGen.generateWithShrunkValues(seed)

            expectThat(doubledValue).isEqualTo(originalValue * 2)
            expectThat(doubledShrinks).isEqualTo(originalShrinks.map { it * 2 })
        }
    }

    @Nested
    inner class FlatMapTests {
        //@Test
        //fun `flatMap generates the second value based on the first`() {
        //    val smallGen = Gen.int(0..5)
        //    val bigGen = Gen.int(10..20)
        //    val gen = smallGen.flatMap { a -> bigGen.map { b -> a + b } }
        //
        //    val producer = StubValueProducer(listOf(5, 20))
        //
        //    val value = gen.generate(producer, GenMode.Initial).value
        //    expectThat(value).isEqualTo(25)
        //}

        //@Test
        //fun `flatMap combines shrinks from both generators`() {
        //    val smallGen = Gen.int(1..3)
        //    val biggerGen = Gen.int(4..6)
        //    val gen = smallGen.flatMap { a -> biggerGen.map { b -> a + b } }
        //
        //    val tree = producerTree {
        //        left(3)
        //        right(6)
        //    }
        //
        //    val (value, shrinks) = gen.generateWithShrunkValues(tree)
        //    expectThat(value).isEqualTo(9)
        //
        //    val threeShrunk = shrink(3, range = 1..3)
        //    val sixShrunk = shrink(6, range = 4..6)
        //
        //    expectThat(shrinks).contains(threeShrunk.map { it + 6 }.toList())
        //    expectThat(shrinks).contains(sixShrunk.map { it + 3 }.toList())
        //}
    }

    @Nested
    inner class CombineWithTests {
        //    @Test
        //    fun `combineWith merges two independent generators`() {
        //        val smallGen = Gen.int(0..5)
        //        val bigGen = Gen.int(10..20)
        //        val gen = smallGen.combineWith(bigGen) { a, b -> a + b }
        //
        //        val tree = producerTree {
        //            left(5)
        //            right(20)
        //        }
        //
        //        val value = gen.generate(tree, GenMode.Initial).value
        //        expectThat(value).isEqualTo(25)
        //    }
        //
        //    @Test
        //    fun `combineWith combines shrinks from both generators`() {
        //        val smallGen = Gen.int(1..3)
        //        val bigGen = Gen.int(4..6)
        //        val gen = smallGen.combineWith(bigGen) { a, b -> a + b }
        //
        //        val tree = producerTree {
        //            left(3)
        //            right(6)
        //        }
        //
        //        val (value, shrinks) = gen.generateWithShrunkValues(tree)
        //        expectThat(value).isEqualTo(9)
        //
        //        val threeShrunk = shrink(3, range = 1..3)
        //        val sixShrunk = shrink(6, range = 4..6)
        //
        //        expectThat(shrinks).contains(threeShrunk.map { it + 6 }.toList())
        //        expectThat(shrinks).contains(sixShrunk.map { it + 3 }.toList())
        //    }
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
}

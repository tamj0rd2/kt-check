package com.tamj0rd2.ktcheck.gen

import com.tamj0rd2.ktcheck.gen.GenTests.Companion.generateWithShrunkValues
import com.tamj0rd2.ktcheck.producer.ProducerTreeDsl.Companion.producerTree
import com.tamj0rd2.ktcheck.producer.Seed
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.all
import strikt.assertions.any
import strikt.assertions.contains
import strikt.assertions.hasLength
import strikt.assertions.isEqualTo
import strikt.assertions.isIn
import strikt.assertions.isNotEmpty
import strikt.assertions.isNotNull
import strikt.assertions.message
import strikt.assertions.startsWith

class CombinerGeneratorTest {
    @Test
    fun `generators using the builder shrink correctly`() {
        data class Person(val name: String, val age: Int)

        val gen = Gen.combine {
            Person(
                name = Gen.char('a'..'d').string(1..5).bind(),
                age = Gen.int(0..150).bind()
            )
        }

        val tree = producerTree(Seed(1)) {
            // used for first gen - name
            left {
                left(4) // name length
                right {
                    left(3) // index 3 of chars - 'd'
                }
            }
            // used for second gen - age
            right {
                left(28) // age
            }
        }

        val (value, shrunkValues) = gen.generateWithShrunkValues(tree)

        expectThat(value) {
            get { name }.hasLength(4).startsWith('d')
            get { age }.isEqualTo(28)
        }

        expectThat(shrunkValues)
            .isNotEmpty()
            .any { get { name }.isEqualTo("d") }
            .any { get { age }.isEqualTo(0) }
    }

    @Test
    fun `conditionals work correctly when affecting the last bind`() {
        data class XY(val x: Int, val y: Int?)

        // When a conditional affects only the final bind call(s), the combiner works correctly.
        // The unconsumed tree parts are simply ignored.

        val gen = Gen.combine {
            val includeY = Gen.bool().bind()
            val x = Gen.int(0..10).bind()

            if (includeY) {
                val y = Gen.int(10..20).bind()
                XY(x, y)
            } else {
                XY(x, null)
            }
        }

        val tree = producerTree(Seed(1)) {
            left(true)  // includeY
            right {
                left(5)  // x
                right {
                    left(15)  // y
                }
            }
        }

        val (value, shrunkValues) = gen.generateWithShrunkValues(tree)
        expectThat(value).isEqualTo(XY(5, 15))

        // When includeY shrinks to false, we only bind 2 generators and get XY(5, null)
        expectThat(shrunkValues).isNotEmpty().contains(XY(5, null))

        // all shrinks meet their constraints
        expectThat(shrunkValues.map { it.x }).all { isIn(0..<10) }
        expectThat(shrunkValues.mapNotNull { it.y }).all { isIn(10..20) }
    }

    @Test
    fun `conditionals fail when affecting a middle bind`() {
        data class XY(val x: Int?, val y: Int)

        // When a conditional affects a bind that is NOT the last one, subsequent binds
        // consume the wrong tree positions, leading to incorrect values.

        val gen = Gen.combine {
            val includeX = Gen.bool().bind()

            if (includeX) {
                val x = Gen.int(0..10).bind()
                val y = Gen.int(10..20).bind()
                XY(x, y)
            } else {
                // Problem: y will consume position 2 instead of position 3!
                val y = Gen.int(10..20).bind()
                XY(null, y)
            }
        }

        val tree = producerTree(Seed(1)) {
            left(true)  // includeX = true
            right {
                left(5)  // position 2: should be x
                right {
                    left(15)  // position 3: should always be y
                }
            }
        }

        // When includeX shrinks to false, y incorrectly consumes position 2 (value 5) instead of position 3 (value 15).
        // This demonstrates the combiner doesn't handle non-tail conditional binds well.
        expectThrows<IllegalStateException> { gen.generateWithShrunkValues(tree) }
            .message.isNotNull().startsWith("5 not in range 10..20")
    }
}

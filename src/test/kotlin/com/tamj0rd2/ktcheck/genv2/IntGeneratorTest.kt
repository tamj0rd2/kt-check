package com.tamj0rd2.ktcheck.genv2

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.message

class IntGeneratorTest {

    @Test
    fun `can generate a non-negative int`() {
        val gen = Gen2.int(0..10)
        expectThat(gen.generate(MinimalSampleTree).first).isEqualTo(0)
    }

    @Test
    fun `can generate a non-negative int with a specific origin`() {
        val gen = Gen2.int(0..10, origin = 5)
        expectThat(gen.generate(MinimalSampleTree).first).isEqualTo(5)
    }

    @Test
    fun `can generate a non-positive int`() {
        val gen = Gen2.int(-10..0)
        expectThat(gen.generate(MinimalSampleTree).first).isEqualTo(0)
    }

    @Test
    fun `can generate a non-positive int with specific origin`() {
        val gen = Gen2.int(-10..0, origin = -5)
        expectThat(gen.generate(MinimalSampleTree).first).isEqualTo(-5)
    }

    @Test
    fun `can generate an int with mixed signs`() {
        val gen = Gen2.int(-5..5)
        expectThat(gen.generate(MinimalSampleTree).first).isEqualTo(0)
    }

    @Test
    fun `can generate an int with mixed signs, with a negative origin`() {
        val gen = Gen2.int(-5..5, origin = -5)
        expectThat(gen.generate(MinimalSampleTree).first).isEqualTo(-5)
    }

    @Test
    fun `can generate an int with mixed signs, with a positive origin`() {
        val gen = Gen2.int(-5..5, origin = 5)
        expectThat(gen.generate(MinimalSampleTree).first).isEqualTo(5)
    }

    @Test
    fun `can generate an int up to the maximum value in a non-negative range`() {
        val gen = Gen2.int(0..Int.MAX_VALUE, origin = 0)
        expectThat(gen.generate(SampleTree.constant(ULong.MAX_VALUE)).first).isEqualTo(Int.MAX_VALUE)
    }

    @Test
    fun `can generate an int up to the minimum value in a non-positive range`() {
        val gen = Gen2.int(Int.MIN_VALUE..0, origin = Int.MIN_VALUE)
        expectThat(gen.generate(MinimalSampleTree).first).isEqualTo(Int.MIN_VALUE)
    }

    @Test
    fun `can generate an int up to the maximum value in a mixed sign range`() {
        val gen = Gen2.int(Int.MIN_VALUE..Int.MAX_VALUE, origin = Int.MAX_VALUE)
        expectThat(gen.generate(MinimalSampleTree).first).isEqualTo(Int.MAX_VALUE)
    }

    @Test
    fun `can generate an int down to the minimum value in a mixed sign range`() {
        val gen = Gen2.int(Int.MIN_VALUE..Int.MAX_VALUE, origin = Int.MIN_VALUE)
        expectThat(gen.generate(MinimalSampleTree).first).isEqualTo(Int.MIN_VALUE)
    }

    @Test
    fun `can generate an int from a single value range`() {
        val gen = Gen2.int(42..42)
        expectThat(gen.generate(MinimalSampleTree).first).isEqualTo(42)
    }

    @Test
    fun `can generate an int from a single value range with explicit origin`() {
        val gen = Gen2.int(42..42, origin = 42)
        expectThat(gen.generate(SampleTree.constant(ULong.MAX_VALUE)).first).isEqualTo(42)
    }

    @Test
    fun `can generate an int from a positive range that does not include 0`() {
        val gen = Gen2.int(5..10)
        expectThat(gen.generate(MinimalSampleTree).first).isEqualTo(5)
    }

    @Test
    fun `can generate an int from a negative range that does not include 0`() {
        val gen = Gen2.int(-10..-5)
        expectThat(gen.generate(MinimalSampleTree).first).isEqualTo(-10)
    }

    @Test
    fun `throws exception when origin is below range`() {
        val exception = assertThrows<IllegalArgumentException> {
            Gen2.int(5..10, origin = 4)
        }
        expectThat(exception).message.isEqualTo("Origin must be within 5..10")
    }

    @Test
    fun `throws exception when origin is above range`() {
        val exception = assertThrows<IllegalArgumentException> {
            Gen2.int(5..10, origin = 11)
        }
        expectThat(exception).message.isEqualTo("Origin must be within 5..10")
    }

    @Test
    fun `generates value closer to origin when origin is in middle of positive range`() {
        // With MinimalSampleTree (sample value = 0), both ranges generate their min values
        // Lower range: 5..7 generates 5, Upper range: 7..10 generates 7
        // Since |5-7| = 2 and |7-7| = 0, should pick 7
        val gen = Gen2.int(5..10, origin = 7)
        expectThat(gen.generate(MinimalSampleTree).first).isEqualTo(7)
    }

    @Test
    fun `generates value closer to origin when origin is in middle of negative range`() {
        // With MinimalSampleTree (sample value = 0), both ranges generate their min values
        // Lower range: -10..-5 generates -10, Upper range: -5..-1 generates -5
        // Since |-10-(-5)| = 5 and |-5-(-5)| = 0, should pick -5
        val gen = Gen2.int(-10..-1, origin = -5)
        expectThat(gen.generate(MinimalSampleTree).first).isEqualTo(-5)
    }

    @Test
    fun `generates value closer to origin when origin is in middle of mixed sign range`() {
        // With MinimalSampleTree (sample value = 0), both ranges generate their min values
        // Lower range: -5..3 generates -5, Upper range: 3..10 generates 3
        // Since |-5-3| = 8 and |3-3| = 0, should pick 3
        val gen = Gen2.int(-5..10, origin = 3)
        expectThat(gen.generate(MinimalSampleTree).first).isEqualTo(3)
    }

    @Test
    fun `can use default origin for positive range not including 0`() {
        // When range doesn't include 0, default origin should be range.first (5)
        val gen = Gen2.int(5..10)
        // With MinimalSampleTree, it should generate the origin (5)
        expectThat(gen.generate(MinimalSampleTree).first).isEqualTo(5)
    }

    @Test
    fun `can use default origin for negative range not including 0`() {
        // When range doesn't include 0, default origin should be range.first (-10)
        val gen = Gen2.int(-10..-5)
        // With MinimalSampleTree, it should generate the origin (-10)
        expectThat(gen.generate(MinimalSampleTree).first).isEqualTo(-10)
    }
}

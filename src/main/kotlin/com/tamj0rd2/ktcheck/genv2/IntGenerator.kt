package com.tamj0rd2.ktcheck.genv2

import kotlin.math.abs
import kotlin.math.floor

/**
 * Produces a generator that generates integers within the specified [range], shrinking towards the [origin].
 * If an origin is not provided, it defaults to zero if zero is within the range, otherwise to the start of the range.
 *
 * @param range The range of integers to generate.
 * @param origin The integer value to shrink towards. Must be within the [range].
 * @return A generator that produces integers within the specified range.
 * @throws IllegalArgumentException if the origin is not within the specified range.
 */
fun Gen.Companion.int(
    range: IntRange = Int.MIN_VALUE..Int.MAX_VALUE,
    origin: Int = range.defaultOrigin(),
): Gen<Int> {
    require(origin in range) { "Origin must be within $range" }

    return Gen.sample().map { sample ->
        val min = range.first
        val max = range.last

        when {
            min == max -> min
            origin == min -> sample.toInt(min, max)
            origin == max -> sample.toInt(max, min)
            else -> {
                val valueInLowerRange = sample.toInt(min, origin)
                val valueInUpperRange = sample.toInt(origin, max)
                setOf(valueInLowerRange, valueInUpperRange).minBy { abs(it.toLong() - origin.toLong()) }
            }
        }
    }
}

private fun IntRange.defaultOrigin() = if (first <= 0 && last >= 0) 0 else first

/**
 * Maps a [Sample] to an integer within the specified [min] and [max] bounds.
 *
 * @param min The minimum integer value (inclusive).
 * @param max The maximum integer value (inclusive).
 * @return An integer within the specified range.
 */
private fun Sample.toInt(min: Int, max: Int): Int {
    val fraction = value.toDouble() / ULong.MAX_VALUE.toDouble()
    val range = max.toLong() - min.toLong()
    return floor(min + fraction * (range + 1.0)).toInt()
}

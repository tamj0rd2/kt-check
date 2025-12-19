package com.tamj0rd2.ktcheck.genv2

import kotlin.math.abs
import kotlin.math.floor

fun Gen.Companion.int(range: IntRange = Int.MIN_VALUE..Int.MAX_VALUE): Gen<Int> {
    val origin = if (range.first <= 0 && range.last >= 0) 0 else range.first
    return int(range, origin)
}

fun Gen.Companion.int(range: IntRange, origin: Int): Gen<Int> {
    require(origin in range) { "Origin must be within $range" }

    return Gen.sample().map { sample ->
        val min = range.first
        val max = range.last

        when {
            min == max -> min
            origin == min -> generateInt(min, max, sample)
            origin == max -> generateInt(max, min, sample)
            else -> {
                val valueInLowerRange = generateInt(min, origin, sample)
                val valueInUpperRange = generateInt(origin, max, sample)
                setOf(valueInLowerRange, valueInUpperRange).minBy { abs(it.toLong() - origin.toLong()) }
            }
        }
    }
}

private fun generateInt(min: Int, max: Int, sample: Sample): Int {
    val fraction = sample.value.toDouble() / ULong.MAX_VALUE.toDouble()
    val range = max.toLong() - min.toLong()
    return floor(min + fraction * (range + 1.0)).toInt()
}

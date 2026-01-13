package com.tamj0rd2.ktcheck.core

import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.random.nextLong
import kotlin.random.nextUInt
import kotlin.random.nextULong

internal sealed interface ValueProducer {
    fun int(range: IntRange): Int
    fun long(range: LongRange): Long
    fun uInt(range: UIntRange): UInt
    fun uLong(range: ULongRange): ULong
    fun byte(range: IntRange): Byte
    fun short(range: IntRange): Short
    fun double(range: ClosedFloatingPointRange<Double>): Double
    fun float(range: ClosedFloatingPointRange<Float>): Float
    fun bool(): Boolean
}

@JvmInline
internal value class RandomValueProducer(val seed: Seed) : ValueProducer {
    private val random get() = Random(seed.value)

    override fun toString(): String = "seed=${seed.value}"

    override fun int(range: IntRange): Int = random.nextInt(range)

    override fun long(range: LongRange): Long = random.nextLong(range)

    override fun uInt(range: UIntRange): UInt = random.nextUInt(range)

    override fun uLong(range: ULongRange): ULong = random.nextULong(range)

    override fun byte(range: IntRange): Byte = random.nextInt(range).toByte()

    override fun short(range: IntRange): Short = random.nextInt(range).toShort()

    override fun double(range: ClosedFloatingPointRange<Double>): Double {
        // Handle single-value range
        if (range.start == range.endInclusive) return range.start

        // Random.nextDouble(from, until) requires until to be finite and takes exclusive upper bound
        // For a ClosedFloatingPointRange, we need endInclusive to be included in possible values
        // We can use nextDouble(from, until) where until > endInclusive
        // The simplest approach is to use a very small increment, but that's imprecise
        // Better: use the next representable double after endInclusive
        val until = if (range.endInclusive.isFinite()) {
            // Math.nextUp would give us the next representable double, but we can approximate
            // For practical purposes with finite ranges, adding Double.MIN_VALUE works
            range.endInclusive + Math.ulp(range.endInclusive)
        } else {
            throw IllegalArgumentException("Range end must be finite for random generation: $range")
        }

        return random.nextDouble(range.start, until)
    }

    override fun float(range: ClosedFloatingPointRange<Float>): Float {
        // Handle single-value range
        if (range.start == range.endInclusive) return range.start

        val until = if (range.endInclusive.isFinite()) {
            range.endInclusive + Math.ulp(range.endInclusive)
        } else {
            throw IllegalArgumentException("Range end must be finite for random generation: $range")
        }

        val result = random.nextDouble(range.start.toDouble(), until.toDouble()).toFloat()
        // Clamp result to range in case of overflow during double->float conversion
        return result.coerceIn(range.start, range.endInclusive)
    }

    override fun bool(): Boolean = random.nextBoolean()
}

@JvmInline
internal value class PredeterminedValue(val value: Any) : ValueProducer {
    init {
        when (value) {
            is Int,
            is Long,
            is UInt,
            is ULong,
            is Byte,
            is Short,
            is Double,
            is Float,
            is Boolean,
                -> Unit

            else -> throw IllegalArgumentException("Unsupported predetermined value type: ${value::class.simpleName}")
        }
    }

    override fun toString(): String = "$value (${value::class.simpleName} value)"

    override fun int(range: IntRange): Int {
        val int = value as Int
        check(int in range) { "$int not in range $range. Are you using conditionals inside a generator?" }
        return int
    }

    override fun long(range: LongRange): Long {
        val long = value as Long
        check(long in range) { "$long not in range $range. Are you using conditionals inside a generator?" }
        return long
    }

    override fun uInt(range: UIntRange): UInt {
        val uInt = value as UInt
        check(uInt in range) { "$uInt not in range $range. Are you using conditionals inside a generator?" }
        return uInt
    }

    override fun uLong(range: ULongRange): ULong {
        val uLong = value as ULong
        check(uLong in range) { "$uLong not in range $range. Are you using conditionals inside a generator?" }
        return uLong
    }

    override fun byte(range: IntRange): Byte {
        val byte = value as Byte
        check(byte.toInt() in range) { "$byte not in range $range. Are you using conditionals inside a generator?" }
        return byte
    }

    override fun short(range: IntRange): Short {
        val short = value as Short
        check(short.toInt() in range) { "$short not in range $range. Are you using conditionals inside a generator?" }
        return short
    }

    override fun double(range: ClosedFloatingPointRange<Double>): Double {
        val double = value as Double
        // Don't check range for non-finite values (NaN, Infinity)
        if (!double.isFinite()) return double
        check(double in range) { "$double not in range $range. Are you using conditionals inside a generator?" }
        return double
    }

    override fun float(range: ClosedFloatingPointRange<Float>): Float {
        val float = value as Float
        // Don't check range for non-finite values (NaN, Infinity)
        if (!float.isFinite()) return float
        check(float in range) { "$float not in range $range. Are you using conditionals inside a generator?" }
        return float
    }

    override fun bool(): Boolean = value as Boolean
}

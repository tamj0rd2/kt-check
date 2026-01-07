package com.tamj0rd2.ktcheck.producer

import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.random.nextLong
import kotlin.random.nextUInt

internal sealed interface ValueProducer {
    fun int(range: IntRange): Int
    fun long(range: LongRange): Long
    fun uInt(range: UIntRange): UInt
    fun bool(): Boolean
}

@JvmInline
internal value class RandomValueProducer(val seed: Seed) : ValueProducer {
    private val random get() = Random(seed.value)

    override fun int(range: IntRange): Int = random.nextInt(range)

    override fun long(range: LongRange): Long = random.nextLong(range)

    override fun uInt(range: UIntRange): UInt = random.nextUInt(range)

    override fun bool(): Boolean = random.nextBoolean()
}

@JvmInline
internal value class PredeterminedValue(val value: Any) : ValueProducer {
    init {
        when (value) {
            is Int,
            is Long,
            is UInt,
            is Boolean,
                -> Unit

            else -> throw IllegalArgumentException("Unsupported predetermined value type: ${value::class.simpleName}")
        }
    }

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

    override fun bool(): Boolean = value as Boolean
}

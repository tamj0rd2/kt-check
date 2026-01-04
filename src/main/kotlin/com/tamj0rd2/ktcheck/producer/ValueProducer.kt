package com.tamj0rd2.ktcheck.producer

import kotlin.random.Random
import kotlin.random.nextInt

internal sealed interface ValueProducer {
    fun int(range: IntRange): Int
    fun bool(): Boolean
}

@JvmInline
internal value class RandomValueProducer(val seed: Seed) : ValueProducer {
    private val random get() = Random(seed.value)

    override fun int(range: IntRange): Int = random.nextInt(range)

    override fun bool(): Boolean = random.nextBoolean()
}

@JvmInline
internal value class PredeterminedValue(val value: Any) : ValueProducer {
    init {
        when (value) {
            is Int,
            is Boolean,
                -> Unit

            else -> throw IllegalArgumentException("Unsupported predetermined value type: ${value::class.simpleName}")
        }
    }

    override fun int(range: IntRange): Int = value as Int

    override fun bool(): Boolean = value as Boolean
}

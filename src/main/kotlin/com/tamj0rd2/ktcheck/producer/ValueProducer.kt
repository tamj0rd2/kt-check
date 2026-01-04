package com.tamj0rd2.ktcheck.producer

import kotlin.random.Random
import kotlin.random.nextInt

internal sealed interface ValueProducer {
    fun int(range: IntRange): Int
    fun bool(): Boolean
    fun char(chars: Set<Char>): Char
}

@JvmInline
internal value class RandomValueProducer(val seed: Seed) : ValueProducer {
    private val random get() = Random(seed.value)

    override fun int(range: IntRange): Int = random.nextInt(range)

    override fun bool(): Boolean = random.nextBoolean()

    override fun char(chars: Set<Char>): Char = chars.random(random)
}

internal sealed interface Primitive {
    val value: Any?

    @JvmInline
    value class Int(override val value: kotlin.Int) : Primitive

    @JvmInline
    value class Bool(override val value: Boolean) : Primitive

    @JvmInline
    value class Char(override val value: kotlin.Char) : Primitive
}

@JvmInline
internal value class PredeterminedValue(private val primitive: Primitive) : ValueProducer {
    override fun int(range: IntRange): Int = (primitive as Primitive.Int).value

    override fun bool(): Boolean = (primitive as Primitive.Bool).value

    override fun char(chars: Set<Char>): Char = (primitive as Primitive.Char).value
}

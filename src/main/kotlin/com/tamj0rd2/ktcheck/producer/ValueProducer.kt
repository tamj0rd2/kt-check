package com.tamj0rd2.ktcheck.producer

import kotlin.random.Random
import kotlin.random.nextInt

internal sealed interface ValueProducer {
    fun int(range: IntRange): Int
    fun bool(): Boolean
}

internal data class RandomValueProducer(val seed: Long) : ValueProducer {
    private val random get() = Random(seed)

    override fun int(range: IntRange): Int = random.nextInt(range)

    override fun bool(): Boolean = random.nextBoolean()
}

internal sealed interface Choice {
    val value: Any?

    data class Int(override val value: kotlin.Int) : Choice
    data class Bool(override val value: Boolean) : Choice
}

internal data class PredeterminedValue(private val choice: Choice) : ValueProducer {
    override fun int(range: IntRange): Int = when {
        choice !is Choice.Int -> throw InvalidReplay("Expected IntChoice but got ${choice::class.simpleName}")
        choice.value !in range -> throw InvalidReplay("IntChoice value ${choice.value} not in range $range")
        else -> choice.value
    }

    override fun bool(): Boolean = when (choice) {
        !is Choice.Bool -> throw InvalidReplay("Expected BooleanChoice but got ${choice::class.simpleName}")
        else -> choice.value
    }
}

internal class InvalidReplay(message: String) : IllegalStateException(message)

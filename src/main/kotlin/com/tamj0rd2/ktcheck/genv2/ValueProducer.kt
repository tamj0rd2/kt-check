package com.tamj0rd2.ktcheck.genv2

import kotlin.random.Random
import kotlin.random.nextInt

internal sealed interface ValueProducer {
    fun int(range: IntRange): Int
    fun bool(): Boolean
}

data class RandomValueProducer(val seed: Long) : ValueProducer {
    private val random get() = Random(seed)

    override fun int(range: IntRange): Int = random.nextInt(range)

    override fun bool(): Boolean = random.nextBoolean()
}

data class PredeterminedValue(private val choice: Choice) : ValueProducer {
    sealed interface Choice {
        val value: Any?

        data class IntChoice(override val value: Int) : Choice
        data class BooleanChoice(override val value: Boolean) : Choice
    }

    override fun int(range: IntRange): Int = when {
        choice !is Choice.IntChoice -> throw InvalidReplay("Expected IntChoice but got ${choice::class.simpleName}")
        choice.value !in range -> throw InvalidReplay("IntChoice value ${choice.value} not in range $range")
        else -> choice.value
    }

    override fun bool(): Boolean = when (choice) {
        !is Choice.BooleanChoice -> throw InvalidReplay("Expected BooleanChoice but got ${choice::class.simpleName}")
        else -> choice.value
    }
}

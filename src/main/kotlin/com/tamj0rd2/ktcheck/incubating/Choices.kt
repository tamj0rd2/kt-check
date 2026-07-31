package com.tamj0rd2.ktcheck.incubating

import com.tamj0rd2.ktcheck.Gen
import com.tamj0rd2.ktcheck.core.shrinkers.IntShrinker
import com.tamj0rd2.ktcheck.stats.Percentage
import com.tamj0rd2.ktcheck.stats.Percentage.Companion.asPercentage
import com.tamj0rd2.ktcheck.stats.Percentage.Companion.percent
import kotlin.random.Random

internal sealed interface Choice<T> {
    val value: T

    fun shrinks(): Sequence<Choice<T>>

    data class Integer(
        override val value: Int,
        val constraints: IntegerConstraints,
    ) : Choice<Int> {
        override fun shrinks(): Sequence<Choice<Int>> =
            IntShrinker.shrink(value, constraints.range, constraints.shrinkTarget).map { copy(value = it) }
    }
}

internal sealed class Choices {
    protected abstract val choices: List<Choice<*>>

    internal fun shrinks(): Sequence<PredeterminedChoices> = choices.asSequence().flatMapIndexed { index, choice ->
        choice.shrinks().map { shrunkChoice ->
            PredeterminedChoices(
                choices.toMutableList().apply { set(index, shrunkChoice) }.toList()
            )
        }
    }

    internal abstract fun int(
        constraints: IntegerConstraints,
    ): Int

    fun <T> Gen<T>.bind(): T {
        check(this is GenV2) { "${this@bind::class.java} incompatible with ${this@Choices::class.java}" }
        return generate(this@Choices)
    }
}

// todo: I could inject the edge case chance here. but maybe edge case chance should be per gen? I can't make
//  my mind up about it? One reason not to have it here is in case someone wants to disable edge cases for a specific
//  generator. That said, edge cases could still potentially be generated randomly... better for the user to filter
//  them out explicitly.
internal class RandomChoices(
    private val random: Random,
    private val chanceToGenerateEdgeCase: Percentage = 10.percent,
) : Choices() {
    private val _choices = mutableListOf<Choice<*>>()

    override val choices get() = _choices.toList()

    override fun int(constraints: IntegerConstraints): Int {
        val range = constraints.range
        val int = when (shouldGenerateAnEdgeCase()) {
            true -> setOf(range.first, range.first + 1, -1, 0, 1, range.last - 1, range.last)
                .filter { it in range }
                .random(random)

            false -> range.random(random)
        }
        _choices.add(Choice.Integer(value = int, constraints = constraints))
        return int
    }

    private fun shouldGenerateAnEdgeCase(): Boolean {
        return random.nextDouble().asPercentage <= chanceToGenerateEdgeCase
    }
}

internal class PredeterminedChoices(private val derivedFrom: List<Choice<*>>) : Choices() {
    private var choiceMarker = 0

    override val choices: List<Choice<*>> get() = derivedFrom.take(choiceMarker)

    override fun int(constraints: IntegerConstraints): Int {
        val choice = derivedFrom.getOrNull(choiceMarker)
        if (choice !is Choice.Integer) {
            TODO("expected an integer choice but got $choice")
        }

        if (constraints != choice.constraints) {
            TODO("Constraint mismatch. Got $constraints, want ${choice.constraints}")
        }
        choiceMarker += 1
        return choice.value
    }
}

package com.tamj0rd2.ktcheck.gen

import kotlin.math.abs
import kotlin.random.Random

sealed class ChoiceSequence {
    protected abstract val history: List<Int>

    abstract fun randomInt(range: IntRange): Int

    override fun toString(): String {
        return history.toString()
    }

    companion object {
        fun ChoiceSequence.shrink(): Sequence<ChoiceSequence> {
            return sequence {
                history.forEachIndexed { index, int ->
                    for (smallerInt in shrinkInt(int)) {
                        val newHistory = history.toMutableList().also { it[index] = smallerInt }
                        yield(ReadOnlyChoiceSequence(newHistory))
                    }
                }
            }
        }

        private fun shrinkInt(value: Int) = sequence {
            if (value != 0) yield(0)
            var current = abs(value) / 2
            while (current != 0) {
                yield(abs(value) - current)
                current /= 2
            }
        }
    }
}

class WritableChoiceSequence(private val random: Random) : ChoiceSequence() {
    private val _history: MutableList<Int> = mutableListOf()
    override val history
        get() = _history.toList()

    override fun randomInt(range: IntRange): Int = range.random(random).also { _history.add(it) }
}

class ReadOnlyChoiceSequence(private val inheritedHistory: List<Int>) : ChoiceSequence() {
    private var marker = 0
    override val history
        get() = inheritedHistory.take(marker)

    override fun randomInt(range: IntRange): Int {
        if (marker > inheritedHistory.lastIndex) {
            throw InvalidReplay("No more choices left in ReadOnlyChoiceSequence")
        }

        val choice = inheritedHistory[marker]
        if (choice !in range) {
            throw InvalidReplay("Choice $choice is out of range $range")
        }

        return choice.also { marker += 1 }
    }
}

internal class InvalidReplay(message: String) : IllegalStateException(message)

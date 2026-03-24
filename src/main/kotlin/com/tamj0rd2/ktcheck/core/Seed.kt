package com.tamj0rd2.ktcheck.core

import kotlin.random.Random

@JvmInline
// todo: make constructor private and see what breaks/flakes
value class Seed internal constructor(val value: Long) {
    fun next(offset: Int): Seed {
        if (value == 0L && offset == 0) {
            throw IllegalArgumentException("$this cannot produce a new seed using offset 0")
        }

        return Seed(value * SPLIT_MIX_64_MULTIPLIER + offset)
    }

    companion object {
        private const val SPLIT_MIX_64_MULTIPLIER = 6364136223846793005L

        internal fun random(): Seed = Seed(Random.nextLong())

        internal fun sequence(seed: Seed = random()): Sequence<Seed> = generateSequence(seed) {
            it.next(if (it.value == 0L) 1 else 0)
        }
    }
}

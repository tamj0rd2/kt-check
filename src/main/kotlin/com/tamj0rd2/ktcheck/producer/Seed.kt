package com.tamj0rd2.ktcheck.producer

@JvmInline
value class Seed internal constructor(val value: Long) {
    fun next(offset: Int): Seed = Seed(value * SPLIT_MIX_64_MULTIPLIER + offset)

    companion object {
        private const val SPLIT_MIX_64_MULTIPLIER = 6364136223846793005L
    }
}

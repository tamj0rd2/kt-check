package com.tamj0rd2.ktcheck.gen

private const val SPLIT_MIX_64_MULTIPLIER = 6364136223846793005L

// todo: introduce a type to represent the Seed
internal fun deriveSeed(parentSeed: Long, offset: Int): Long =
    parentSeed * SPLIT_MIX_64_MULTIPLIER + offset

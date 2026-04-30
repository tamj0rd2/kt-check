package com.tamj0rd2.ktcheck

val IntRange.Companion.full get() = Int.MIN_VALUE..Int.MAX_VALUE
val IntRange.Companion.positive get() = 1..Int.MAX_VALUE
val IntRange.Companion.negative get() = Int.MIN_VALUE..-1
val IntRange.Companion.nonNegative get() = 0..Int.MAX_VALUE
val IntRange.Companion.nonPositive get() = Int.MIN_VALUE..0

internal fun CharRange.toIntRange() = first.code..last.code

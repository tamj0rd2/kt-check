package com.tamj0rd2.ktcheck

import com.tamj0rd2.ktcheck.CodePoint.Companion.toCodePoint
import kotlin.streams.toList

@JvmInline
value class CodePoint private constructor(val value: Int) : Comparable<CodePoint> {
    init {
        require(Character.isValidCodePoint(value)) { "$value is not a valid code point" }
    }

    fun asString(): String = Character.toChars(value).joinToString("")

    override fun compareTo(other: CodePoint): Int = value.compareTo(other.value)
    operator fun rangeTo(other: CodePoint) = CodePointRange(this, other)
    operator fun rangeUntil(other: CodePoint) = CodePointRange(this, other - 1)

    operator fun plus(amount: Int) = CodePoint(value + amount)
    operator fun minus(amount: Int) = CodePoint(value - amount)

    companion object {
        val min = CodePoint(Character.MIN_CODE_POINT)
        val max = CodePoint(Character.MAX_CODE_POINT)

        /**
         * @throws IllegalArgumentException if the value is not a valid Unicode codepoint
         */
        fun Int.toCodePoint() = CodePoint(this)

        fun Char.toCodePoint() = code.toCodePoint()

        /**
         * @throws NoSuchElementException if there are no codepoints present
         * @throws IllegalArgumentException if there is more than 1 codepoint present
         */
        fun String.toCodePoint() = codePoints().toList().single().toCodePoint()
    }
}

data class CodePointRange(
    override val start: CodePoint,
    override val endInclusive: CodePoint,
) : ClosedRange<CodePoint> {
    fun toIntRange() = start.value..endInclusive.value

    companion object {
        val full = CodePoint.min..CodePoint.max

        fun IntRange.toCodePointRange() = first.toCodePoint()..last.toCodePoint()
    }
}

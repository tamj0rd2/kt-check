package com.tamj0rd2.ktcheck.gen

private data class ShortGenerator(
    private val range: IntRange,
) : Gen<Short>() {
    init {
        require(range.first in Short.MIN_VALUE..Short.MAX_VALUE) {
            "Range start ${range.first} must be within Short range $range"
        }
        require(range.last in Short.MIN_VALUE..Short.MAX_VALUE) {
            "Range end ${range.last} must be within Short range $range"
        }
    }

    override fun GenContext.generate(): GenResult<Short> {
        val value = tree.producer.short(range)
        return GenResult(
            value = value,
            shrinks = shrink(value.toInt(), range).map { tree.withValue(it.toShort()) }
        )
    }
}

fun Gen.Companion.short(range: IntRange = Short.MIN_VALUE..Short.MAX_VALUE): Gen<Short> = ShortGenerator(range)


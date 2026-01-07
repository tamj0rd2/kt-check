package com.tamj0rd2.ktcheck.gen

private data class ByteGenerator(
    private val range: IntRange,
) : Gen<Byte>() {
    init {
        require(range.first in Byte.MIN_VALUE..Byte.MAX_VALUE) {
            "Range start ${range.first} must be within Byte range $range"
        }
        require(range.last in Byte.MIN_VALUE..Byte.MAX_VALUE) {
            "Range end ${range.last} must be within Byte range $range"
        }
    }

    override fun GenContext.generate(): GenResult<Byte> {
        val value = tree.producer.byte(range)
        return GenResult(
            value = value,
            shrinks = shrink(value.toInt(), range).map { tree.withValue(it.toByte()) }
        )
    }
}

fun Gen.Companion.byte(range: IntRange = Byte.MIN_VALUE..Byte.MAX_VALUE): Gen<Byte> = ByteGenerator(range)


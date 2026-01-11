package com.tamj0rd2.ktcheck.v2

internal object BooleanGeneratorV2 : GenV2<Boolean>() {
    override fun GenContextV2.generate(): GenResultV2<Boolean> {
        val value = producer.bool()
        return GenResultV2(
            value = value,
            shrinks = if (value) {
                sequenceOf(GenResultV2(false, emptySequence()))
            } else {
                emptySequence()
            },
        )
    }
}

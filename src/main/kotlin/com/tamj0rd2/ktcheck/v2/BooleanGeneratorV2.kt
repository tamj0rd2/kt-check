package com.tamj0rd2.ktcheck.v2

class BooleanGeneratorV2 private constructor() : GenV2<Boolean>() {

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

    companion object {
        fun GenV2.Companion.bool() = BooleanGeneratorV2()
    }
}

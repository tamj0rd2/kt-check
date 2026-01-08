package com.tamj0rd2.ktcheck.v2

class BooleanGenerator private constructor() : Gen<Boolean>() {

    override fun GenContext.generate(): GenResult<Boolean> {
        val value = producer.bool()
        return GenResult(
            value = value,
            shrinks = if (value) {
                sequenceOf(GenResult(false, emptySequence()))
            } else {
                emptySequence()
            },
        )
    }

    companion object {
        fun Gen.Companion.bool() = BooleanGenerator()
    }
}

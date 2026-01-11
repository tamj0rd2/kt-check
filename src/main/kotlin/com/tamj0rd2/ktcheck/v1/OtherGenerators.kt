package com.tamj0rd2.ktcheck.v1

internal data class ConstantGenerator<T>(private val value: T) : GenV1<T>() {
    override fun GenContext.generate(): GenResult<T> = GenResult(value, emptySequence())
}

package com.tamj0rd2.ktcheck.gen

internal data class ConstantGenerator<T>(private val value: T) : Gen<T>() {
    override fun GenContext.generate(): GenResult<T> = GenResult(value, emptySequence())
}

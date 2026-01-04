package com.tamj0rd2.ktcheck.gen

import com.tamj0rd2.ktcheck.producer.ProducerTree

private class ConstantGenerator<T>(private val value: T) : Gen<T>() {
    override fun generate(tree: ProducerTree): GenResult<T> = GenResult(value, emptySequence())
}

fun <T> Gen.Companion.constant(value: T): Gen<T> = ConstantGenerator(value)

fun Gen.Companion.char(
    chars: Iterable<Char> = Char.MIN_VALUE..Char.MAX_VALUE,
): Gen<Char> = Gen.oneOfValues(chars.distinct().sorted())

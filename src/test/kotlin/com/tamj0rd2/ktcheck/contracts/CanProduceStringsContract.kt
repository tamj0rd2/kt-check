package com.tamj0rd2.ktcheck.contracts

import com.tamj0rd2.ktcheck.Gen
import kotlin.streams.toList

internal interface CanProduceStringsContract : BehavesLikeAListGeneratorContract {
    fun newStringLikeGenerator(sizeRange: IntRange): Gen<String>

    override fun newListLikeGen(sizeRange: IntRange): Gen<List<Any?>> {
        return newStringLikeGenerator(sizeRange).map { it.codePoints().toList() }
    }
}

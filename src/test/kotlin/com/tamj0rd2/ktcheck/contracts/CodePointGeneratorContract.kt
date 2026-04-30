package com.tamj0rd2.ktcheck.contracts


import com.tamj0rd2.ktcheck.Gen
import com.tamj0rd2.ktcheck.string

// todo: this should have all the same properties as Int, as it's just int with a map applied...
internal interface CodePointGeneratorContract : BaseContract, CanProduceStringsContract {
    override val exampleGen get() = codePoint()

    override fun newStringLikeGenerator(sizeRange: IntRange): Gen<String> = codePoint().string(sizeRange)

    // todo:add some more sanity checks here
}

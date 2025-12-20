package com.tamj0rd2.ktcheck.genv2

import com.tamj0rd2.ktcheck.genv2.Gen.Companion.sample
import org.junit.jupiter.api.Test

class ListGeneratorTest {
    @Test
    fun `can generate a long list without stack overflow`() {
        Gen.constant(1).list(10_000).sample()
    }
}

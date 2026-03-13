package com.tamj0rd2.ktcheck.current

internal class EdgeCasesDisabledGen<T>(
    private val delegate: Generator<T>,
) : Generator<T> by delegate {
    override fun edgeCases(root: ProviderTree): List<GeneratedValue<T>> {
        return emptyList()
    }
}

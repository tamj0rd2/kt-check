package com.tamj0rd2.ktcheck

interface CombinerContext {
    fun <T> Gen<T>.bind(): T
}

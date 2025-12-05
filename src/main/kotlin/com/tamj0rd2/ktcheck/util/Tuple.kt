@file:Suppress("unused")

package com.tamj0rd2.ktcheck.util

fun <A, B> tuple(val1: A, val2: B) = Tuple2(val1, val2)

fun <A, B, C> tuple(val1: A, val2: B, val3: C) = Tuple3(val1, val2, val3)

fun <A, B, C, D> tuple(val1: A, val2: B, val3: C, val4: D) = Tuple4(val1, val2, val3, val4)

fun <A, B, C, D, E> tuple(val1: A, val2: B, val3: C, val4: D, val5: E) = Tuple5(val1, val2, val3, val4, val5)

fun <A, B, C, D, E, F> tuple(val1: A, val2: B, val3: C, val4: D, val5: E, val6: F) = Tuple6(val1, val2, val3, val4, val5, val6)

fun <A, B, C, D, E, F, G> tuple(val1: A, val2: B, val3: C, val4: D, val5: E, val6: F, val7: G) =
    Tuple7(val1, val2, val3, val4, val5, val6, val7)

fun <A, B, C, D, E, F, G, H> tuple(val1: A, val2: B, val3: C, val4: D, val5: E, val6: F, val7: G, val8: H) =
    Tuple8(val1, val2, val3, val4, val5, val6, val7, val8)

fun <A, B, C, D, E, F, G, H, I> tuple(val1: A, val2: B, val3: C, val4: D, val5: E, val6: F, val7: G, val8: H, val9: I) =
    Tuple9(val1, val2, val3, val4, val5, val6, val7, val8, val9)

fun <A, B, C, D, E, F, G, H, I, J> tuple(val1: A, val2: B, val3: C, val4: D, val5: E, val6: F, val7: G, val8: H, val9: I, val10: J) =
    Tuple10(val1, val2, val3, val4, val5, val6, val7, val8, val9, val10)

sealed interface Tuple {
    val values: List<Any?>
}

data class Tuple2<A, B> internal constructor(val val1: A, val val2: B) : Tuple {
    override val values = listOf(val1, val2)

    fun <Value> plus(value: Value) = Tuple3(val1, val2, value)
}

data class Tuple3<A, B, C> internal constructor(val val1: A, val val2: B, val val3: C) : Tuple {
    override val values = listOf(val1, val2, val3)

    fun <Value> plus(value: Value) = Tuple4(val1, val2, val3, value)
}

data class Tuple4<A, B, C, D> internal constructor(val val1: A, val val2: B, val val3: C, val val4: D) : Tuple {
    override val values = listOf(val1, val2, val3, val4)

    fun <Value> plus(value: Value) = Tuple5(val1, val2, val3, val4, value)
}

data class Tuple5<A, B, C, D, E> internal constructor(val val1: A, val val2: B, val val3: C, val val4: D, val val5: E) : Tuple {
    override val values = listOf(val1, val2, val3, val4, val5)

    fun <Value> plus(value: Value) = Tuple6(val1, val2, val3, val4, val5, value)
}

data class Tuple6<A, B, C, D, E, F> internal constructor(val val1: A, val val2: B, val val3: C, val val4: D, val val5: E, val val6: F) :
    Tuple {
    override val values = listOf(val1, val2, val3, val4, val5, val6)

    fun <Value> plus(value: Value) = Tuple7(val1, val2, val3, val4, val5, val6, value)
}

data class Tuple7<A, B, C, D, E, F, G>
internal constructor(val val1: A, val val2: B, val val3: C, val val4: D, val val5: E, val val6: F, val val7: G) : Tuple {
    override val values = listOf(val1, val2, val3, val4, val5, val6, val7)

    fun <Value> plus(value: Value) = Tuple8(val1, val2, val3, val4, val5, val6, val7, value)
}

data class Tuple8<A, B, C, D, E, F, G, H>
internal constructor(val val1: A, val val2: B, val val3: C, val val4: D, val val5: E, val val6: F, val val7: G, val val8: H) : Tuple {
    override val values = listOf(val1, val2, val3, val4, val5, val6, val7, val8)

    fun <Value> plus(value: Value) = Tuple9(val1, val2, val3, val4, val5, val6, val7, val8, value)
}

data class Tuple9<A, B, C, D, E, F, G, H, I>
internal constructor(val val1: A, val val2: B, val val3: C, val val4: D, val val5: E, val val6: F, val val7: G, val val8: H, val val9: I) :
    Tuple {
    override val values = listOf(val1, val2, val3, val4, val5, val6, val7, val8, val9)

    fun <Value> plus(value: Value) = Tuple10(val1, val2, val3, val4, val5, val6, val7, val8, val9, value)
}

data class Tuple10<A, B, C, D, E, F, G, H, I, J>
internal constructor(
    val val1: A,
    val val2: B,
    val val3: C,
    val val4: D,
    val val5: E,
    val val6: F,
    val val7: G,
    val val8: H,
    val val9: I,
    val val10: J,
) : Tuple {
    override val values = listOf(val1, val2, val3, val4, val5, val6, val7, val8, val9, val10)
}

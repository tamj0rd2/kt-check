@file:Suppress("unused")

package com.tamj0rd2.ktcheck.genv2

import com.tamj0rd2.ktcheck.util.Tuple2
import com.tamj0rd2.ktcheck.util.Tuple3
import com.tamj0rd2.ktcheck.util.Tuple4
import com.tamj0rd2.ktcheck.util.Tuple5
import com.tamj0rd2.ktcheck.util.Tuple6
import com.tamj0rd2.ktcheck.util.Tuple7
import com.tamj0rd2.ktcheck.util.Tuple8
import com.tamj0rd2.ktcheck.util.Tuple9
import com.tamj0rd2.ktcheck.util.tuple

@JvmName("zip2")
infix operator fun <T1, T2> Gen<T1>.plus(
    nextGen: Gen<T2>,
) = combineWith(nextGen) { first, second -> tuple(first, second) }

@JvmName("zip3")
infix operator fun <T1, T2, T3> Gen<Tuple2<T1, T2>>.plus(
    nextGen: Gen<T3>,
) = combineWith(nextGen) { tuple, nextValue -> tuple + nextValue }

@JvmName("zip4")
infix operator fun <T1, T2, T3, T4> Gen<Tuple3<T1, T2, T3>>.plus(
    nextGen: Gen<T4>,
) = combineWith(nextGen) { tuple, nextValue -> tuple + nextValue }

@JvmName("zip5")
infix operator fun <T1, T2, T3, T4, T5> Gen<Tuple4<T1, T2, T3, T4>>.plus(
    nextGen: Gen<T5>,
) = combineWith(nextGen) { tuple, nextValue -> tuple + nextValue }

@JvmName("zip6")
infix operator fun <T1, T2, T3, T4, T5, T6> Gen<Tuple5<T1, T2, T3, T4, T5>>.plus(
    nextGen: Gen<T6>,
) = combineWith(nextGen) { tuple, nextValue -> tuple + nextValue }

@JvmName("zip7")
infix operator fun <T1, T2, T3, T4, T5, T6, T7> Gen<Tuple6<T1, T2, T3, T4, T5, T6>>.plus(
    nextGen: Gen<T6>,
) = combineWith(nextGen) { tuple, nextValue -> tuple + nextValue }

@JvmName("zip8")
infix operator fun <T1, T2, T3, T4, T5, T6, T7, T8> Gen<Tuple7<T1, T2, T3, T4, T5, T6, T7>>.plus(
    nextGen: Gen<T8>,
) = combineWith(nextGen) { tuple, nextValue -> tuple + nextValue }

@JvmName("zip9")
infix operator fun <T1, T2, T3, T4, T5, T6, T7, T8, T9> Gen<Tuple8<T1, T2, T3, T4, T5, T6, T7, T8>>.plus(
    nextGen: Gen<T9>,
) = combineWith(nextGen) { tuple, nextValue -> tuple + nextValue }

@JvmName("zip10")
infix operator fun <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> Gen<Tuple9<T1, T2, T3, T4, T5, T6, T7, T8, T9>>.plus(
    nextGen: Gen<T10>,
) = combineWith(nextGen) { tuple, nextValue -> tuple + nextValue }

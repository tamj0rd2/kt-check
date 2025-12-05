@file:Suppress("unused")

package com.tamj0rd2.ktcheck.gen

import com.tamj0rd2.ktcheck.util.Tuple2
import com.tamj0rd2.ktcheck.util.Tuple3
import com.tamj0rd2.ktcheck.util.Tuple4
import com.tamj0rd2.ktcheck.util.Tuple5
import com.tamj0rd2.ktcheck.util.Tuple6
import com.tamj0rd2.ktcheck.util.Tuple7
import com.tamj0rd2.ktcheck.util.Tuple8
import com.tamj0rd2.ktcheck.util.Tuple9

@JvmName("zipTuple2")
fun <A, B, C> Gen.Companion.zip(gen: Gen<Tuple2<A, B>>, other: Gen<C>) = Gen { cs -> gen.generate(cs).plus(other.generate(cs)) }

@JvmName("zipTuple3")
fun <A, B, C, D> Gen.Companion.zip(gen: Gen<Tuple3<A, B, C>>, other: Gen<D>) = Gen { cs -> gen.generate(cs).plus(other.generate(cs)) }

@JvmName("zipTuple4")
fun <A, B, C, D, E> Gen.Companion.zip(gen: Gen<Tuple4<A, B, C, D>>, other: Gen<E>) = Gen { cs -> gen.generate(cs).plus(other.generate(cs)) }

@JvmName("zipTuple5")
fun <A, B, C, D, E, F> Gen.Companion.zip(gen: Gen<Tuple5<A, B, C, D, E>>, other: Gen<F>) = Gen { cs -> gen.generate(cs).plus(other.generate(cs)) }

@JvmName("zipTuple6")
fun <A, B, C, D, E, F, G> Gen.Companion.zip(gen: Gen<Tuple6<A, B, C, D, E, F>>, other: Gen<G>) = Gen { cs -> gen.generate(cs).plus(other.generate(cs)) }

@JvmName("zipTuple7")
fun <A, B, C, D, E, F, G, H> Gen.Companion.zip(gen: Gen<Tuple7<A, B, C, D, E, F, G>>, other: Gen<H>) = Gen { cs -> gen.generate(cs).plus(other.generate(cs)) }

@JvmName("zipTuple8")
fun <A, B, C, D, E, F, G, H, I> Gen.Companion.zip(gen: Gen<Tuple8<A, B, C, D, E, F, G, H>>, other: Gen<I>) = Gen { cs -> gen.generate(cs).plus(other.generate(cs)) }

@JvmName("zipTuple9")
fun <A, B, C, D, E, F, G, H, I, J> Gen.Companion.zip(gen: Gen<Tuple9<A, B, C, D, E, F, G, H, I>>, other: Gen<J>) = Gen { cs -> gen.generate(cs).plus(other.generate(cs)) }

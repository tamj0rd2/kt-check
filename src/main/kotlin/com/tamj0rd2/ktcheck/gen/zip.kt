@file:Suppress("unused")

package com.tamj0rd2.ktcheck.gen

import com.tamj0rd2.ktcheck.util.tuple

fun <T1, T2> Gen.Companion.zip(gen1: Gen<T1>, gen2: Gen<T2>) = Gen { cs -> tuple(gen1.generate(cs), gen2.generate(cs)) }

fun <T1, T2, T3> Gen.Companion.zip(gen1: Gen<T1>, gen2: Gen<T2>, gen3: Gen<T3>) = Gen { cs ->
    tuple(gen1.generate(cs), gen2.generate(cs), gen3.generate(cs))
}

fun <T1, T2, T3, T4> Gen.Companion.zip(gen1: Gen<T1>, gen2: Gen<T2>, gen3: Gen<T3>, gen4: Gen<T4>) = Gen { cs ->
    tuple(gen1.generate(cs), gen2.generate(cs), gen3.generate(cs), gen4.generate(cs))
}

fun <T1, T2, T3, T4, T5> Gen.Companion.zip(gen1: Gen<T1>, gen2: Gen<T2>, gen3: Gen<T3>, gen4: Gen<T4>, gen5: Gen<T5>) = Gen { cs ->
    tuple(gen1.generate(cs), gen2.generate(cs), gen3.generate(cs), gen4.generate(cs), gen5.generate(cs))
}

fun <T1, T2, T3, T4, T5, T6> Gen.Companion.zip(gen1: Gen<T1>, gen2: Gen<T2>, gen3: Gen<T3>, gen4: Gen<T4>, gen5: Gen<T5>, gen6: Gen<T6>) =
    Gen { cs ->
        tuple(gen1.generate(cs), gen2.generate(cs), gen3.generate(cs), gen4.generate(cs), gen5.generate(cs), gen6.generate(cs))
    }

fun <T1, T2, T3, T4, T5, T6, T7> Gen.Companion.zip(
    gen1: Gen<T1>,
    gen2: Gen<T2>,
    gen3: Gen<T3>,
    gen4: Gen<T4>,
    gen5: Gen<T5>,
    gen6: Gen<T6>,
    gen7: Gen<T7>,
) = Gen { cs ->
    tuple(
        gen1.generate(cs),
        gen2.generate(cs),
        gen3.generate(cs),
        gen4.generate(cs),
        gen5.generate(cs),
        gen6.generate(cs),
        gen7.generate(cs),
    )
}

fun <T1, T2, T3, T4, T5, T6, T7, T8> Gen.Companion.zip(
    gen1: Gen<T1>,
    gen2: Gen<T2>,
    gen3: Gen<T3>,
    gen4: Gen<T4>,
    gen5: Gen<T5>,
    gen6: Gen<T6>,
    gen7: Gen<T7>,
    gen8: Gen<T8>,
) = Gen { cs ->
    tuple(
        gen1.generate(cs),
        gen2.generate(cs),
        gen3.generate(cs),
        gen4.generate(cs),
        gen5.generate(cs),
        gen6.generate(cs),
        gen7.generate(cs),
        gen8.generate(cs),
    )
}

fun <T1, T2, T3, T4, T5, T6, T7, T8, T9> Gen.Companion.zip(
    gen1: Gen<T1>,
    gen2: Gen<T2>,
    gen3: Gen<T3>,
    gen4: Gen<T4>,
    gen5: Gen<T5>,
    gen6: Gen<T6>,
    gen7: Gen<T7>,
    gen8: Gen<T8>,
    gen9: Gen<T9>,
) = Gen { cs ->
    tuple(
        gen1.generate(cs),
        gen2.generate(cs),
        gen3.generate(cs),
        gen4.generate(cs),
        gen5.generate(cs),
        gen6.generate(cs),
        gen7.generate(cs),
        gen8.generate(cs),
        gen9.generate(cs),
    )
}

fun <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> Gen.Companion.zip(
    gen1: Gen<T1>,
    gen2: Gen<T2>,
    gen3: Gen<T3>,
    gen4: Gen<T4>,
    gen5: Gen<T5>,
    gen6: Gen<T6>,
    gen7: Gen<T7>,
    gen8: Gen<T8>,
    gen9: Gen<T9>,
    gen10: Gen<T10>,
) = Gen { cs ->
    tuple(
        gen1.generate(cs),
        gen2.generate(cs),
        gen3.generate(cs),
        gen4.generate(cs),
        gen5.generate(cs),
        gen6.generate(cs),
        gen7.generate(cs),
        gen8.generate(cs),
        gen9.generate(cs),
        gen10.generate(cs),
    )
}

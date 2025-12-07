@file:Suppress("unused")

package com.tamj0rd2.ktcheck.gen

import com.tamj0rd2.ktcheck.util.tuple

fun <T1, T2> Gen.Companion.zip(gen1: Gen<T1>, gen2: Gen<T2>) =
    gen { tuple(gen1.bind(), gen2.bind()) }

fun <T1, T2, T3> Gen.Companion.zip(gen1: Gen<T1>, gen2: Gen<T2>, gen3: Gen<T3>) =
    gen { tuple(gen1.bind(), gen2.bind(), gen3.bind()) }

fun <T1, T2, T3, T4> Gen.Companion.zip(gen1: Gen<T1>, gen2: Gen<T2>, gen3: Gen<T3>, gen4: Gen<T4>) =
    gen { tuple(gen1.bind(), gen2.bind(), gen3.bind(), gen4.bind()) }

fun <T1, T2, T3, T4, T5> Gen.Companion.zip(gen1: Gen<T1>, gen2: Gen<T2>, gen3: Gen<T3>, gen4: Gen<T4>, gen5: Gen<T5>) =
    gen { tuple(gen1.bind(), gen2.bind(), gen3.bind(), gen4.bind(), gen5.bind()) }

fun <T1, T2, T3, T4, T5, T6> Gen.Companion.zip(
    gen1: Gen<T1>,
    gen2: Gen<T2>,
    gen3: Gen<T3>,
    gen4: Gen<T4>,
    gen5: Gen<T5>,
    gen6: Gen<T6>,
) =
    gen { tuple(gen1.bind(), gen2.bind(), gen3.bind(), gen4.bind(), gen5.bind(), gen6.bind()) }

fun <T1, T2, T3, T4, T5, T6, T7> Gen.Companion.zip(
    gen1: Gen<T1>,
    gen2: Gen<T2>,
    gen3: Gen<T3>,
    gen4: Gen<T4>,
    gen5: Gen<T5>,
    gen6: Gen<T6>,
    gen7: Gen<T7>,
) = gen {
    tuple(
        gen1.bind(),
        gen2.bind(),
        gen3.bind(),
        gen4.bind(),
        gen5.bind(),
        gen6.bind(),
        gen7.bind(),
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
) = gen {
    tuple(
        gen1.bind(),
        gen2.bind(),
        gen3.bind(),
        gen4.bind(),
        gen5.bind(),
        gen6.bind(),
        gen7.bind(),
        gen8.bind(),
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
) = gen {
    tuple(
        gen1.bind(),
        gen2.bind(),
        gen3.bind(),
        gen4.bind(),
        gen5.bind(),
        gen6.bind(),
        gen7.bind(),
        gen8.bind(),
        gen9.bind(),
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
) = gen {
    tuple(
        gen1.bind(),
        gen2.bind(),
        gen3.bind(),
        gen4.bind(),
        gen5.bind(),
        gen6.bind(),
        gen7.bind(),
        gen8.bind(),
        gen9.bind(),
        gen10.bind(),
    )
}

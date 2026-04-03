package com.tamj0rd2.ktcheck.current

import com.tamj0rd2.ktcheck.Falsification
import com.tamj0rd2.ktcheck.Property
import com.tamj0rd2.ktcheck.PropertyFalsifiedException
import com.tamj0rd2.ktcheck.ShrinkingConstraint
import com.tamj0rd2.ktcheck.TestConfig
import com.tamj0rd2.ktcheck.core.Seed
import dev.forkhandles.result4k.onFailure
import dev.forkhandles.result4k.orThrow

internal fun <T> test(config: TestConfig, gen: Gen<T>, property: Property<T>) {
    TestRunner(config = config, gen = gen, property = property).run()
}

private class TestRunner<T>(
    private val config: TestConfig,
    private val gen: Gen<T>,
    private val property: Property<T>,
) {
    private val edgeCases = gen.edgeCases(ProviderTree.new(Seed(0)))

    fun run() {
        val startingIteration = (config.replayIteration ?: 1)
        repeat(config.effectiveIterations) {
            val iteration = startingIteration + it

            val result = runIteration(iteration)
            if (result !is TestIterationResult.DidFalsify<*>) return@repeat

            throw PropertyFalsifiedException(
                seed = config.seed.value,
                iteration = iteration,
                original = result.originalFalsification,
                shrunk = result.shrunkFalsification.takeIf { it.input != result.originalFalsification.input },
                shrinkSteps = result.shrinkSteps
            )
        }
    }

    private fun runIteration(iteration: Int): TestIterationResult {
        val input = if (iteration - 1 in edgeCases.indices) {
            edgeCases.elementAt(iteration - 1)
        } else {
            gen.generate(ProviderTree.new(config.seed.next(iteration))).orThrow()
        }

        val originalError = property.falsify(input.value) ?: return TestIterationResult.DidNotFalsify
        val originalFalsification = Falsification(input.value, originalError.error)

        val (shrunkFalsification, shrinkSteps) = config.shrinkingConstraintFactory.new().use {
            findSimplestFalsification(
                originalGeneratedValue = input,
                originalFalsification = originalFalsification,
                shrinkingConstraint = it,
            )
        }

        return TestIterationResult.DidFalsify(
            originalFalsification = originalFalsification,
            shrunkFalsification = shrunkFalsification,
            shrinkSteps = shrinkSteps
        )
    }

    private fun findSimplestFalsification(
        originalGeneratedValue: GeneratedValue<T>,
        originalFalsification: Falsification<T>,
        shrinkingConstraint: ShrinkingConstraint,
    ): Pair<Falsification<T>, Int> {
        shrinkingConstraint.onStart()

        // todo: these 2 values are entirely coupled. They should probably be a single thing.
        var simplestFalsification = originalFalsification
        var candidates = originalGeneratedValue.shrinks.iterator()

        val seenValues = mutableSetOf<T>()

        while (shrinkingConstraint.shouldKeepShrinking() && candidates.hasNext()) {
            val shrunkInput = gen.generate(candidates.next()).onFailure { continue }
            if (!seenValues.add(shrunkInput.value)) continue

            shrinkingConstraint.onStep()

            val testResult = property.falsify(shrunkInput.value) ?: continue

            simplestFalsification = Falsification(shrunkInput.value, testResult.error)
            candidates = shrunkInput.shrinks.iterator()
        }

        return simplestFalsification to seenValues.size
    }

    private sealed interface TestIterationResult {
        data class DidFalsify<T>(
            val originalFalsification: Falsification<T>,
            val shrunkFalsification: Falsification<T>,
            val shrinkSteps: Int,
        ) : TestIterationResult

        data object DidNotFalsify : TestIterationResult
    }
}

package com.tamj0rd2.ktcheck

import com.tamj0rd2.ktcheck.core.Seed
import java.io.OutputStream
import java.io.OutputStream.nullOutputStream
import java.io.PrintStream
import java.time.Instant
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

@RequiresOptIn(
    message = "Indicates that test configuration has been hardcoded for this test, which should only be done for local debugging purposes.",
    level = RequiresOptIn.Level.WARNING
)
annotation class HardcodedTestConfig

@ConsistentCopyVisibility
data class TestConfig private constructor(
    internal val iterations: Int,
    internal val seed: Seed,
    internal val replayIteration: Int?,
    internal val shrinkingConstraintFactory: ShrinkingConstraintFactory,
    internal val printShrinkSteps: Boolean,
    internal val reportingPrintStream: PrintStream,
) {
    internal val effectiveIterations = if (replayIteration != null) 1 else iterations

    constructor() : this(
        iterations = System.getProperty(SYSTEM_PROPERTY_TEST_ITERATIONS)?.toIntOrNull() ?: DEFAULT_ITERATIONS,
        seed = Seed(Random.nextLong()),
        replayIteration = null,
        shrinkingConstraintFactory = ShrinkingConstraint.byDuration(1.seconds),
        printShrinkSteps = false,
        reportingPrintStream = System.out,
    )

    fun withIterations(iterations: Int) = copy(iterations = iterations)

    fun withSeed(seed: Long) = copy(seed = Seed(seed))

    fun withShrinkingConstraint(constraint: ShrinkingConstraintFactory) =
        copy(shrinkingConstraintFactory = constraint)

    @Deprecated("I might move this functionality to the Reporter")
    fun printShrinkSteps() = copy(printShrinkSteps = true)

    @HardcodedTestConfig
    fun replay(seed: Long, iteration: Int) = copy(
        seed = Seed(seed),
        replayIteration = iteration
    )

    fun withoutReporting() = withReportingStream(nullOutputStream())

    fun withReportingStream(outputStream: OutputStream) = copy(reportingPrintStream = PrintStream(outputStream))

    companion object {
        internal const val SYSTEM_PROPERTY_TEST_ITERATIONS = "ktcheck.test.iterations"

        internal const val DEFAULT_ITERATIONS = 1000
    }
}

fun interface ShrinkingConstraintFactory {
    fun new(): ShrinkingConstraint
}

interface ShrinkingConstraint : AutoCloseable {
    fun onStart() {}
    fun onStep() {}
    override fun close() {}

    // todo: contract
    fun shouldStopShrinking(): Boolean
    fun shouldKeepShrinking(): Boolean = !shouldStopShrinking()

    companion object {
        fun infinite(): ShrinkingConstraintFactory = { Unconstrained }
        fun bySteps(maxSteps: Int): ShrinkingConstraintFactory = { ConstrainedBySteps(maxSteps) }
        fun byDuration(duration: Duration): ShrinkingConstraintFactory = { ConstrainedByDuration(duration) }
    }
}

private object Unconstrained : ShrinkingConstraint {
    override fun shouldStopShrinking(): Boolean = false
}

private class ConstrainedByDuration(private val duration: Duration) : ShrinkingConstraint {
    private var mustEndAfter: Instant? = null

    override fun onStart() {
        mustEndAfter = Instant.now().plus(duration.toJavaDuration())
    }

    override fun shouldStopShrinking(): Boolean {
        checkNotNull(mustEndAfter) { "Shrink session not started" }
        return Instant.now().isAfter(mustEndAfter)
    }
}

private class ConstrainedBySteps(private val maxSteps: Int) : ShrinkingConstraint {
    private var stepsTaken = 0

    override fun onStep() {
        stepsTaken += 1
    }

    override fun shouldStopShrinking(): Boolean {
        return stepsTaken >= maxSteps
    }
}

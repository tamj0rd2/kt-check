package com.tamj0rd2.ktcheck.stats

import com.tamj0rd2.ktcheck.stats.Percentage.Companion.percent
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.contains
import strikt.assertions.isEqualTo
import strikt.assertions.isLessThan

@Suppress("ClassName")
class CounterTest {

    @Nested
    inner class `collecting and checking unlabelled values` {
        @Test
        fun `can collect and verify percentage of single value`() {
            val counter = Counter()

            repeat(100) { counter.collect("value1") }

            counter.checkPercentages(mapOf("value1" to 100.percent))
        }

        @Test
        fun `can collect and verify percentage of multiple values`() {
            val counter = Counter()

            repeat(50) { counter.collect("value1") }
            repeat(30) { counter.collect("value2") }
            repeat(20) { counter.collect("value3") }

            counter.checkPercentages(
                mapOf(
                    "value1" to 50.percent,
                    "value2" to 30.percent,
                    "value3" to 20.percent,
                )
            )
        }

        @Test
        fun `checkPercentages accepts minimum percentages not exact matches`() {
            val counter = Counter()

            repeat(50) { counter.collect("value1") }
            repeat(50) { counter.collect("value2") }

            counter.checkPercentages(
                mapOf(
                    "value1" to 40.percent,
                    "value2" to 40.percent,
                )
            )
        }

        @Test
        fun `checkPercentages throws when percentage is below expected minimum`() {
            val counter = Counter()

            repeat(30) { counter.collect("value1") }
            repeat(70) { counter.collect("value2") }

            expectThrows<AssertionError> {
                counter.checkPercentages(mapOf("value1" to 50.percent))
            }.get { message }.isEqualTo(
                "expected the recorded percentage for 'value1' to be at least 50.0% but was 30.0%"
            )
        }

        @Test
        fun `checkPercentages throws when unlabelled value was never recorded`() {
            val counter = Counter()
            repeat(100) { counter.collect("value1") }

            expectThrows<AssertionError> {
                counter.checkPercentages(mapOf("nonexistent" to 1.percent))
            }.get { message }.isEqualTo("no recorded statistics for the value 'nonexistent'")
        }

        @Test
        fun `checkPercentages throws when labelled value was never recorded`() {
            val counter = LabelledCounter()
            repeat(100) { counter.collect("the-label", "value1") }

            expectThrows<AssertionError> {
                counter.checkPercentages("the-label", mapOf("nonexistent" to 1.percent))
            }.get { message }.isEqualTo("label 'the-label': no recorded statistics for the value 'nonexistent'")
        }

        @Test
        fun `can collect null values`() {
            val counter = Counter()

            repeat(50) { counter.collect(null) }
            repeat(50) { counter.collect("value1") }

            counter.checkPercentages(
                mapOf(
                    null to 50.percent,
                    "value1" to 50.percent
                )
            )
        }
    }

    @Nested
    inner class `collecting and checking labelled values` {
        @Test
        fun `can collect and verify percentage with labels`() {
            val counter = LabelledCounter()

            repeat(60) { counter.collect("label1", "value1") }
            repeat(40) { counter.collect("label1", "value2") }

            counter.checkPercentages("label1", mapOf("value1" to 60.percent, "value2" to 40.percent))
        }

        @Test
        fun `different labels maintain separate statistics`() {
            val counter = LabelledCounter()

            repeat(80) { counter.collect("label1", "value1") }
            repeat(20) { counter.collect("label1", "value2") }

            repeat(30) { counter.collect("label2", "value1") }
            repeat(70) { counter.collect("label2", "value2") }

            counter.checkPercentages("label1", mapOf("value1" to 80.percent, "value2" to 20.percent))
            counter.checkPercentages("label2", mapOf("value1" to 30.percent, "value2" to 70.percent))
        }

        @Test
        fun `checkPercentages without label throws when percentage is below minimum`() {
            val counter = Counter()

            repeat(25) { counter.collect("value1") }
            repeat(75) { counter.collect("value2") }

            expectThrows<AssertionError> {
                counter.checkPercentages(mapOf("value1" to 50.percent))
            }.get { message }.isEqualTo(
                "expected the recorded percentage for 'value1' to be at least 50.0% but was 25.0%"
            )
        }

        @Test
        fun `checkPercentages with label throws when percentage is below minimum`() {
            val counter = LabelledCounter()

            repeat(25) { counter.collect("myLabel", "value1") }
            repeat(75) { counter.collect("myLabel", "value2") }

            expectThrows<AssertionError> {
                counter.checkPercentages("myLabel", mapOf("value1" to 50.percent))
            }.get { message }.isEqualTo(
                "label 'myLabel': expected the recorded percentage for 'value1' to be at least 50.0% but was 25.0%"
            )
        }
    }

    @Nested
    inner class `toString output` {
        @Test
        fun `formats unlabelled statistics correctly`() {
            val counter = Counter()

            repeat(650) { counter.collect("value1") }
            repeat(300) { counter.collect("value2") }
            repeat(1) { counter.collect("value3") }
            repeat(49) { counter.collect("value4") }

            val output = counter.toString()

            expectThat(output).contains("Stats:")
            expectThat(output).contains("value1")
            expectThat(output).contains("value2")
            expectThat(output).contains("value3")
            expectThat(output).contains("65.0%")
            expectThat(output).contains("30.0%")
            expectThat(output).contains("4.9%")
            expectThat(output).contains("0.1%")
        }

        @Test
        fun `formats labelled statistics correctly`() {
            val counter = LabelledCounter()

            repeat(70) { counter.collect("myLabel", "value1") }
            repeat(30) { counter.collect("myLabel", "value2") }

            val output = counter.toString()

            expectThat(output).contains("Stats (myLabel):")
            expectThat(output).contains("70%")
            expectThat(output).contains("30%")
        }

        @Test
        fun `formats multiple labelled sections`() {
            val counter = LabelledCounter()

            repeat(50) { counter.collect("label1", "value1") }
            repeat(50) { counter.collect("label2", "value2") }

            val output = counter.toString()

            expectThat(output).contains("Stats (label1):")
            expectThat(output).contains("Stats (label2):")
        }

        @Test
        fun `sorts entries by count descending`() {
            val counter = Counter()

            repeat(10) { counter.collect("low") }
            repeat(50) { counter.collect("high") }
            repeat(30) { counter.collect("medium") }

            val output = counter.toString()
            val lowIndex = output.indexOf("low")
            val mediumIndex = output.indexOf("medium")
            val highIndex = output.indexOf("high")

            expectThat(highIndex).isLessThan(mediumIndex)
            expectThat(mediumIndex).isLessThan(lowIndex)
        }
    }
}

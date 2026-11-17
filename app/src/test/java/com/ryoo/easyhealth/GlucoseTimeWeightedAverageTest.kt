package com.ryoo.easyhealth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GlucoseTimeWeightedAverageTest {
    private val hour = 60L * 60L * 1000L
    private val day = 24L * hour
    private val base = 1_700_000_000_000L

    private fun reading(value: String, offset: Long, source: String = "Meter A", type: String = "혈당") =
        VitalRecord("id-$source-$offset-$value", "본인", type, value, base + offset, source)

    private fun estimate(
        records: List<VitalRecord>,
        maxGap: Long = 6 * hour,
        minMeasurements: Int = 2,
        minSpan: Long = 0L,
        minCovered: Long = 1L
    ) = calculateTimeWeightedGlucoseEstimate(
        records = records,
        nowMillis = base + 90 * day,
        maxGapMillis = maxGap,
        minimumMeasurements = minMeasurements,
        minimumSpanMillis = minSpan,
        minimumCoveredMillis = minCovered
    )

    @Test fun irregularIntervalsUseElapsedTimeWeights() {
        val result = estimate(listOf(reading("100", 0), reading("200", 2 * hour), reading("300", 5 * hour)))
        assertNotNull(result)
        assertEquals(160.0, result!!.averageMgDl, 0.0001)
        assertEquals(5 * hour, result.coveredMillis)
    }

    @Test fun sameValueReceivesWeightOnlyForItsOwnDuration() {
        val result = estimate(listOf(reading("100", 0), reading("200", hour), reading("100", 5 * hour)))
        assertNotNull(result)
        assertEquals(180.0, result!!.averageMgDl, 0.0001)
    }

    @Test fun longMeasurementGapIsExcluded() {
        val result = estimate(listOf(reading("100", 0), reading("300", 10 * hour), reading("200", 11 * hour)))
        assertNotNull(result)
        assertEquals(300.0, result!!.averageMgDl, 0.0001)
        assertEquals(hour, result.coveredMillis)
        assertEquals(1, result.intervalCount)
    }

    @Test fun simultaneousReadingsFromDifferentMetersShareOneTimePoint() {
        val result = estimate(listOf(
            reading("100", 0, "Meter A"), reading("200", 0, "Meter B"),
            reading("200", hour, "Meter A"), reading("300", 2 * hour, "Meter A")
        ))
        assertNotNull(result)
        assertEquals(175.0, result!!.averageMgDl, 0.0001)
        assertEquals(3, result.measurementCount)
    }

    @Test fun duplicatesAndOutOfOrderRecordsAreHandled() {
        val first = reading("100", 0)
        val result = estimate(listOf(
            reading("300", 2 * hour), reading("200", hour), first, first.copy(id = "duplicate-id")
        ))
        assertNotNull(result)
        assertEquals(150.0, result!!.averageMgDl, 0.0001)
        assertEquals(3, result.measurementCount)
    }

    @Test fun intervalsContinueAcrossMidnight() {
        val beforeMidnight = 23L * hour
        val result = estimate(listOf(
            reading("100", beforeMidnight),
            reading("200", beforeMidnight + 2 * hour)
        ))
        assertNotNull(result)
        assertEquals(100.0, result!!.averageMgDl, 0.0001)
        assertEquals(2 * hour, result.coveredMillis)
    }

    @Test fun insufficientCoverageDoesNotProduceEstimate() {
        val result = calculateTimeWeightedGlucoseEstimate(
            listOf(reading("100", 0), reading("120", hour)),
            nowMillis = base + 2 * hour
        )
        assertNull(result)
    }

    @Test fun eAgAndHbA1cConversionCanBeReversed() {
        val result = estimate(listOf(reading("154.2", 0), reading("154.2", hour)))
        assertNotNull(result)
        val eAgBack = result!!.estimatedA1cPercent * 28.7 - 46.7
        assertEquals(154.2, eAgBack, 0.0001)
        assertEquals(7.0, result.estimatedA1cPercent, 0.001)
    }

    @Test fun laboratoryHbA1cRecordIsNotChangedOrUsedAsGlucose() {
        val lab = reading("7.2", 0, type = "당화혈색소")
        val before = lab.copy()
        estimate(listOf(reading("100", 0), reading("120", hour)))
        assertEquals(before, lab)
        assertNull(estimate(listOf(lab)))
    }
}

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

    @Test fun reportWindowExcludesReadingsOutsideSelectedPeriod() {
        val result = calculateTimeWeightedGlucoseEstimate(
            records = listOf(
                reading("900", -hour),
                reading("100", 0),
                reading("200", hour),
                reading("800", 2 * hour)
            ),
            nowMillis = base + 3 * hour,
            windowStartMillis = base,
            windowEndMillis = base + hour,
            minimumMeasurements = 2,
            minimumSpanMillis = 0L,
            minimumCoveredMillis = 1L
        )
        assertNotNull(result)
        assertEquals(100.0, result!!.averageMgDl, 0.0001)
        assertEquals(2, result.measurementCount)
        assertEquals(hour, result.coveredMillis)
    }

    @Test fun explicitReportWindowCanIncludeHistoryOlderThanNinetyDays() {
        val oldOffset = -120L * day
        val result = calculateTimeWeightedGlucoseEstimate(
            records = listOf(reading("140", oldOffset), reading("160", oldOffset + hour)),
            nowMillis = base + 90 * day,
            windowStartMillis = base - 180 * day,
            windowEndMillis = base + 90 * day,
            minimumMeasurements = 2,
            minimumSpanMillis = 0L,
            minimumCoveredMillis = 1L
        )
        assertNotNull(result)
        assertEquals(140.0, result!!.averageMgDl, 0.0001)
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


class SlotBasedGlucoseEstimateTest {
    private val dayMillis = 86_400_000L

    private fun timestamp(dayStart: Long, hour: Int, minute: Int = 0): Long =
        dayStart + (hour * 60L + minute) * 60_000L

    private fun scheduleSlots(dayStart: Long): List<Long> = listOf(
        timestamp(dayStart, 7), timestamp(dayStart, 7, 45), timestamp(dayStart, 10),
        timestamp(dayStart, 11, 45), timestamp(dayStart, 14),
        timestamp(dayStart, 17, 45), timestamp(dayStart, 20), timestamp(dayStart, 22)
    )

    private fun endOfDay(start: Long): Long = start + dayMillis - 1

    private fun makeRecords(start: Long, omitted: Pair<Int, Int>? = null): List<VitalRecord> {
        val records = mutableListOf<VitalRecord>()
        for (day in 0 until 90) {
            val dayStart = start + day * dayMillis
            scheduleSlots(dayStart).forEachIndexed { slot, at ->
                if (omitted != day to slot) {
                    records.add(VitalRecord("r-$day-$slot", "본인", "혈당",
                        (100 + slot * 10).toString(), at, "Meter"))
                }
            }
        }
        return records
    }

    @Test fun createsExactly720TimestampedSlotsAndUsesSameSlotHistory() {
        val endDay = 1_750_000_000_000L
        val calendar = java.util.Calendar.getInstance().apply {
            timeInMillis = endDay
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
            add(java.util.Calendar.DAY_OF_YEAR, -89)
        }
        val start = calendar.timeInMillis
        val records = makeRecords(start, 45 to 4)
        val result = calculateSlotBasedGlucoseEstimate(records, endOfDay(start + 89 * dayMillis))
        org.junit.Assert.assertNotNull(result)
        org.junit.Assert.assertEquals(720, result!!.slots.size)
        org.junit.Assert.assertEquals(719, result.actualSlotCount)
        org.junit.Assert.assertEquals(1, result.estimatedSlotCount)
        org.junit.Assert.assertEquals(140.0, result.slots[45 * 8 + 4].valueMgDl, 0.001)
        org.junit.Assert.assertTrue(result.slots.all { it.timestamp > 0L })
    }

    @Test fun actualReadingTakesPrecedenceOverHistoricalSlotAverage() {
        val endDay = 1_750_000_000_000L
        val calendar = java.util.Calendar.getInstance().apply {
            timeInMillis = endDay
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
            add(java.util.Calendar.DAY_OF_YEAR, -89)
        }
        val start = calendar.timeInMillis
        val records = makeRecords(start).toMutableList()
        val at = scheduleSlots(start + 60 * dayMillis)[4]
        records.removeAll { it.id == "r-60-4" }
        records.add(VitalRecord("override", "본인", "혈당", "222", at, "Meter"))
        val result = calculateSlotBasedGlucoseEstimate(records, endOfDay(start + 89 * dayMillis))
        org.junit.Assert.assertNotNull(result)
        val selected = result!!.slots[60 * 8 + 4]
        org.junit.Assert.assertFalse(selected.estimated)
        org.junit.Assert.assertEquals(222.0, selected.valueMgDl, 0.001)
    }
}

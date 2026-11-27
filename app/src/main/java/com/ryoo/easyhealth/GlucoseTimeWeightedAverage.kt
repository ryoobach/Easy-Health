package com.ryoo.easyhealth

/**
 * Time-weighted estimate from finger-stick glucose records.
 *
 * Each timestamp's glucose value is assigned only to the interval until the next
 * timestamp. Intervals longer than six hours are excluded; no value is carried
 * forward beyond the last reading. The six-hour limit is a conservative app
 * data-quality rule, not a clinically validated HbA1c threshold.
 */
internal data class TimeWeightedGlucoseEstimate(
    val averageMgDl: Double,
    val estimatedA1cPercent: Double,
    val measurementCount: Int,
    val intervalCount: Int,
    val coveredMillis: Long,
    val firstTimestamp: Long,
    val lastTimestamp: Long
) {
    val coveredHours: Double
        get() = coveredMillis / 3_600_000.0
}

internal fun calculateTimeWeightedGlucoseEstimate(
    records: List<VitalRecord>,
    nowMillis: Long,
    lookbackDays: Int = 90,
    maxGapMillis: Long = 6L * 60L * 60L * 1000L,
    minimumMeasurements: Int = 14,
    minimumSpanMillis: Long = 7L * 24L * 60L * 60L * 1000L,
    minimumCoveredMillis: Long = 24L * 60L * 60L * 1000L,
    windowStartMillis: Long? = null,
    windowEndMillis: Long = nowMillis
): TimeWeightedGlucoseEstimate? {
    require(lookbackDays > 0)
    require(maxGapMillis > 0L)
    val windowStart = windowStartMillis ?: (nowMillis - lookbackDays.toLong() * 24L * 60L * 60L * 1000L)
    require(windowStart <= windowEndMillis)

    // A caller may provide an explicit report interval; otherwise use the rolling lookback.
    // Readings outside the selected interval must not contribute to its estimate.
    val uniqueReadings = records.asSequence()
        .filter { it.type == "혈당" && it.timestamp in windowStart..windowEndMillis }
        .mapNotNull { record ->
            val value = record.value.trim().toDoubleOrNull()
            if (value == null || !value.isFinite() || value <= 0.0) null
            else Triple(record.source.ifBlank { "기기 미지정" }, record.timestamp, value)
        }
        .distinct()
        .toList()

    // Multiple meters at the same timestamp become one time point; their values
    // are averaged so that a simultaneous check does not create extra elapsed time.
    val samples = uniqueReadings
        .groupBy { it.second }
        .toSortedMap()
        .map { (timestamp, readings) -> timestamp to readings.map { it.third }.average() }

    if (samples.size < minimumMeasurements) return null
    val firstTimestamp = samples.first().first
    val lastTimestamp = samples.last().first
    if (lastTimestamp - firstTimestamp < minimumSpanMillis) return null

    var weightedGlucoseMillis = 0.0
    var coveredMillis = 0L
    var intervalCount = 0
    for (index in 0 until samples.lastIndex) {
        val (startTime, glucose) = samples[index]
        val nextTime = samples[index + 1].first
        val interval = nextTime - startTime
        // Sorting handles out-of-order input; zero/negative and long intervals are ignored.
        if (interval in 1..maxGapMillis) {
            weightedGlucoseMillis += glucose * interval.toDouble()
            coveredMillis += interval
            intervalCount++
        }
    }

    if (intervalCount == 0 || coveredMillis < minimumCoveredMillis) return null

    val timeWeightedAverage = weightedGlucoseMillis / coveredMillis.toDouble()
    // ADAG eAG relationship rearranged to estimate HbA1c; this remains an estimate.
    val estimatedA1c = (timeWeightedAverage + 46.7) / 28.7
    return TimeWeightedGlucoseEstimate(
        averageMgDl = timeWeightedAverage,
        estimatedA1cPercent = estimatedA1c,
        measurementCount = samples.size,
        intervalCount = intervalCount,
        coveredMillis = coveredMillis,
        firstTimestamp = firstTimestamp,
        lastTimestamp = lastTimestamp
    )
}


/** The eight daily points used by the shared record/report HbA1c estimate. */
internal data class GlucoseDailySlot(
    val timestamp: Long,
    val valueMgDl: Double,
    val slotIndex: Int,
    val estimated: Boolean
)

internal data class GlucoseDailySchedule(
    val wakeTime: String = "07:00",
    val breakfastTime: String = "08:00",
    val lunchTime: String = "12:00",
    val dinnerTime: String = "18:00",
    val bedtime: String = "22:00"
)

internal data class SlotBasedGlucoseEstimate(
    val estimate: TimeWeightedGlucoseEstimate,
    val slots: List<GlucoseDailySlot>,
    val actualSlotCount: Int,
    val estimatedSlotCount: Int
)

private fun glucoseClockMinutes(value: String, fallback: Int): Int {
    val parts = value.split(":")
    val hour = parts.getOrNull(0)?.toIntOrNull()
    val minute = parts.getOrNull(1)?.toIntOrNull()
    return if (hour in 0..23 && minute in 0..59) hour!! * 60 + minute!! else fallback
}

/**
 * Creates exactly eight timestamped values for each of the 90 calendar dates
 * ending on endMillis. Actual readings are assigned to the nearest configured
 * slot (within 150 minutes); missing values use actual same-slot history only.
 * Estimated values are never fed back into the historical averages.
 */
internal fun calculateSlotBasedGlucoseEstimate(
    records: List<VitalRecord>,
    endMillis: Long,
    schedule: GlucoseDailySchedule = GlucoseDailySchedule()
): SlotBasedGlucoseEstimate? {
    val dayMillis = 24L * 60L * 60L * 1000L
    val hourMillis = 60L * 60L * 1000L
    val endCalendar = java.util.Calendar.getInstance().apply { timeInMillis = endMillis }
    endCalendar.set(java.util.Calendar.HOUR_OF_DAY, 23)
    endCalendar.set(java.util.Calendar.MINUTE, 59)
    endCalendar.set(java.util.Calendar.SECOND, 59)
    endCalendar.set(java.util.Calendar.MILLISECOND, 999)
    val windowEnd = endCalendar.timeInMillis
    val firstDay = java.util.Calendar.getInstance().apply {
        timeInMillis = windowEnd
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
        add(java.util.Calendar.DAY_OF_YEAR, -89)
    }
    // Keep the preceding 21 days as real-measurement history for the first
    // dates in the requested 90-day window.
    val dayStarts = (-21 until 90).map { offset ->
        java.util.Calendar.getInstance().apply {
            timeInMillis = firstDay.timeInMillis
            add(java.util.Calendar.DAY_OF_YEAR, offset)
        }.timeInMillis
    }
    val wake = glucoseClockMinutes(schedule.wakeTime, 7 * 60)
    val breakfast = glucoseClockMinutes(schedule.breakfastTime, 8 * 60)
    val lunch = glucoseClockMinutes(schedule.lunchTime, 12 * 60)
    val dinner = glucoseClockMinutes(schedule.dinnerTime, 18 * 60)
    val bed = glucoseClockMinutes(schedule.bedtime, 22 * 60)
    val slotMinutes = listOf(wake, (breakfast - 15 + 1440) % 1440, (breakfast + 120) % 1440,
        (lunch - 15 + 1440) % 1440, (lunch + 120) % 1440,
        (dinner - 15 + 1440) % 1440, (dinner + 120) % 1440, bed)

    fun slotTimestamp(dayStart: Long, minute: Int): Long =
        dayStart + minute.toLong() * 60_000L

    val actualRecords = records.asSequence()
        .filter { it.type == "혈당" }
        .mapNotNull { record ->
            val value = record.value.trim().toDoubleOrNull()
            if (value == null || !value.isFinite() || value <= 0.0 || record.timestamp > windowEnd) null
            else record to value
        }.toList()

    // Slot assignments contain real readings only. Multiple actual readings
    // assigned to one slot are averaged; an estimated value can never enter here.
    val actualByDay = mutableMapOf<Int, MutableMap<Int, MutableList<Double>>>()
    val actualAssignedIds = mutableSetOf<String>()
    actualRecords.forEach { (record, value) ->
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = record.timestamp }
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        val dayIndex = dayStarts.binarySearch(cal.timeInMillis)
        if (dayIndex !in 0..110) return@forEach
        val nearest = slotMinutes.indices.minByOrNull { index ->
            kotlin.math.abs(slotTimestamp(dayStarts[dayIndex], slotMinutes[index]) - record.timestamp)
        } ?: return@forEach
        val distance = kotlin.math.abs(slotTimestamp(dayStarts[dayIndex], slotMinutes[nearest]) - record.timestamp)
        if (distance <= 150L * 60_000L) {
            actualByDay.getOrPut(dayIndex) { mutableMapOf() }
                .getOrPut(nearest) { mutableListOf() }.add(value)
            actualAssignedIds.add(record.id)
        }
    }
    val actualValues = actualByDay.mapValues { (_, slots) -> slots.mapValues { (_, values) -> values.average() } }
    val allHistoricalActual = actualRecords.map { it.second }
    if (allHistoricalActual.isEmpty()) return null
    val globalFallback = allHistoricalActual.average()

    fun historicalSlotAverage(dayIndex: Int, slot: Int): Double? {
        for (span in listOf(7, 14, 21)) {
            val values = (maxOf(0, dayIndex - span) until dayIndex)
                .mapNotNull { actualValues[it]?.get(slot) }
            if (values.isNotEmpty()) return values.average()
        }
        return null
    }
    fun historicalMealDelta(dayIndex: Int, preSlot: Int, postSlot: Int): Double? {
        for (span in listOf(7, 14, 21)) {
            val deltas = (maxOf(0, dayIndex - span) until dayIndex).mapNotNull { index ->
                val pair = actualValues[index] ?: return@mapNotNull null
                val pre = pair[preSlot] ?: return@mapNotNull null
                val post = pair[postSlot] ?: return@mapNotNull null
                post - pre
            }
            if (deltas.isNotEmpty()) return deltas.average()
        }
        return null
    }

    val slots = mutableListOf<GlucoseDailySlot>()
    for (dayIndex in 21..110) {
        val dayStart = dayStarts[dayIndex]
        for (slot in 0 until 8) {
            val timestamp = slotTimestamp(dayStart, slotMinutes[slot])
            val actual = actualValues[dayIndex]?.get(slot)
            if (actual != null) {
                slots.add(GlucoseDailySlot(timestamp, actual, slot, false))
                continue
            }
            var estimate = historicalSlotAverage(dayIndex, slot)
            if (estimate == null && slot in listOf(1, 2, 3, 4, 5, 6)) {
                val preSlot = if (slot % 2 == 0) slot - 1 else slot
                val postSlot = preSlot + 1
                val delta = historicalMealDelta(dayIndex, preSlot, postSlot)
                val sameDayActual = actualValues[dayIndex]
                estimate = when {
                    slot == postSlot && sameDayActual?.get(preSlot) != null ->
                        sameDayActual.getValue(preSlot) + (delta ?: 0.0)
                    slot == preSlot && sameDayActual?.get(postSlot) != null ->
                        sameDayActual.getValue(postSlot) - (delta ?: 0.0)
                    else -> null
                }
            }
            // Bedtime: if no same-slot history exists, bridge actual dinner-post
            // and next-morning fasting values. Neither endpoint may be estimated.
            if (estimate == null && slot == 7) {
                val dinnerPost = actualValues[dayIndex]?.get(6)
                val nextFasting = actualValues[dayIndex + 1]?.get(0)
                if (dinnerPost != null && nextFasting != null) estimate = (dinnerPost + nextFasting) / 2.0
            }
            if (estimate == null) estimate = globalFallback
            slots.add(GlucoseDailySlot(timestamp, estimate, slot, true))
        }
    }
    if (slots.size != 720) return null
    val synthetic = slots.mapIndexed { index, slot ->
        VitalRecord("estimated-slot-$index", "__a1c__", "혈당",
            String.format(java.util.Locale.US, "%.4f", slot.valueMgDl),
            slot.timestamp, if (slot.estimated) "추정값" else "실측값")
    }
    val start = dayStarts[21]
    val estimate = calculateTimeWeightedGlucoseEstimate(
        records = synthetic,
        nowMillis = windowEnd,
        maxGapMillis = 12L * hourMillis,
        minimumMeasurements = 14,
        minimumSpanMillis = 7L * dayMillis,
        minimumCoveredMillis = 24L * hourMillis,
        windowStartMillis = start,
        windowEndMillis = windowEnd
    ) ?: return null
    return SlotBasedGlucoseEstimate(
        estimate = estimate.copy(measurementCount = slots.size),
        slots = slots,
        actualSlotCount = slots.count { !it.estimated },
        estimatedSlotCount = slots.count { it.estimated }
    )
}

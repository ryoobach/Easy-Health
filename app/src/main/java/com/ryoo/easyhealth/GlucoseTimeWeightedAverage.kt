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
    val lastTimestamp: Long,
    val actualSlotCount: Int = 0,
    val estimatedSlotCount: Int = 0,
    val representedSlotCount: Int = 0
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
 * slot (within 150 minutes); missing values use actual history only. The final
 * mean applies an exponential 30-day half-life to the eight slots per day.
 * Estimated values are never fed back into the historical averages.
 */
internal fun calculateSlotBasedGlucoseEstimate(
    records: List<VitalRecord>,
    endMillis: Long,
    schedule: GlucoseDailySchedule = GlucoseDailySchedule()
): SlotBasedGlucoseEstimate? {
    val dayMillis = 24L * 60L * 60L * 1000L
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
    val windowStart = firstDay.timeInMillis
    val historyStart = java.util.Calendar.getInstance().apply {
        timeInMillis = windowStart
        add(java.util.Calendar.DAY_OF_YEAR, -21)
    }.timeInMillis
    val dayStarts = (-21 until 90).map { offset ->
        java.util.Calendar.getInstance().apply {
            timeInMillis = windowStart
            add(java.util.Calendar.DAY_OF_YEAR, offset)
        }.timeInMillis
    }

    fun clockMinutes(value: String, fallback: Int): Int {
        val parts = value.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull()
        val minute = parts.getOrNull(1)?.toIntOrNull()
        return if (hour in 0..23 && minute in 0..59) hour!! * 60 + minute!! else fallback
    }
    val wake = clockMinutes(schedule.wakeTime, 7 * 60)
    val breakfast = clockMinutes(schedule.breakfastTime, 8 * 60)
    val lunch = clockMinutes(schedule.lunchTime, 12 * 60)
    val dinner = clockMinutes(schedule.dinnerTime, 18 * 60)
    val bedtime = clockMinutes(schedule.bedtime, 22 * 60)
    val slotMinutes = listOf(
        wake,
        (breakfast - 15 + 1440) % 1440, (breakfast + 120) % 1440,
        (lunch - 15 + 1440) % 1440, (lunch + 120) % 1440,
        (dinner - 15 + 1440) % 1440, (dinner + 120) % 1440,
        bedtime
    )
    fun slotTimestamp(dayStart: Long, slot: Int): Long =
        dayStart + slotMinutes[slot].toLong() * 60_000L

    // Keep only the preceding 21 days as historical evidence plus the requested
    // 90-day window. Never allow distant lifetime averages to fill a missing slot.
    val actualRecords = records.asSequence()
        .filter { it.type == "혈당" && it.timestamp in historyStart..windowEnd }
        .mapNotNull { record ->
            val value = record.value.trim().toDoubleOrNull()
            if (value == null || !value.isFinite() || value <= 0.0) null else record to value
        }
        .toList()
    val actualByDay = mutableMapOf<Int, MutableMap<Int, MutableList<Pair<String, Double>>>>()
    val assignedWindowReadingIds = mutableSetOf<String>()
    actualRecords.forEach { (record, value) ->
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = record.timestamp }
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        val dayIndex = dayStarts.binarySearch(cal.timeInMillis)
        if (dayIndex !in 0..110) return@forEach
        val nearest = slotMinutes.indices.minByOrNull { slot ->
            kotlin.math.abs(slotTimestamp(dayStarts[dayIndex], slot) - record.timestamp)
        } ?: return@forEach
        val distance = kotlin.math.abs(slotTimestamp(dayStarts[dayIndex], nearest) - record.timestamp)
        if (distance <= 150L * 60_000L) {
            actualByDay.getOrPut(dayIndex) { mutableMapOf() }
                .getOrPut(nearest) { mutableListOf() }.add(record.id to value)
            if (record.timestamp >= windowStart) assignedWindowReadingIds.add(record.id)
        }
    }
    val actualValues = actualByDay.mapValues { (_, slotMap) ->
        slotMap.mapValues { (_, readings) -> readings.map { it.second }.average() }
    }
    val actualWindowRecords = actualRecords.filter { it.first.timestamp >= windowStart }
        .filter { record -> assignedWindowReadingIds.contains(record.first.id) }
    val distinctActualTimes = actualWindowRecords.map { it.first.timestamp }.distinct().sorted()
    if (assignedWindowReadingIds.size < 14 || distinctActualTimes.size < 14) return null
    if (distinctActualTimes.last() - distinctActualTimes.first() < 7L * dayMillis) return null

    val actualSlotIndices = actualValues.filterKeys { it in 21..110 }
        .values.flatMap { it.keys }.toSet()
    // Require real observations in at least half of the daily slots before
    // presenting a whole-day estimate. Otherwise sampling bias is too severe.
    if (actualSlotIndices.size < 4) return null

    fun historicalSlotAverage(dayIndex: Int, slot: Int): Double? {
        for (span in listOf(7, 14, 21)) {
            val values = (maxOf(0, dayIndex - span) until dayIndex)
                .mapNotNull { actualValues[it]?.get(slot) }
            if (values.size >= 2) return values.average()
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
            // Require at least three actual paired days before applying a
            // personal meal-rise correction; one isolated pair is not enough.
            if (deltas.size >= 3) return deltas.average()
        }
        return null
    }

    val slots = mutableListOf<GlucoseDailySlot>()
    for (dayIndex in 21..110) {
        val dayStart = dayStarts[dayIndex]
        for (slot in 0 until 8) {
            val timestamp = slotTimestamp(dayStart, slot)
            val actual = actualValues[dayIndex]?.get(slot)
            if (actual != null) {
                slots.add(GlucoseDailySlot(timestamp, actual, slot, false))
                continue
            }

            // First choice: same-slot averages from at least two distinct days
            // in the preceding 1, 2, or 3 weeks, using actual readings only.
            var estimate = historicalSlotAverage(dayIndex, slot)

            // Second choice: use the person's measured pre/post-meal delta only
            // when three or more historical paired days support that delta.
            if (estimate == null && slot in 1..6) {
                val preSlot = if (slot % 2 == 0) slot - 1 else slot
                val postSlot = preSlot + 1
                val delta = historicalMealDelta(dayIndex, preSlot, postSlot)
                val sameDayActual = actualValues[dayIndex]
                estimate = when {
                    delta != null && slot == postSlot && sameDayActual?.get(preSlot) != null ->
                        sameDayActual.getValue(preSlot) + delta
                    delta != null && slot == preSlot && sameDayActual?.get(postSlot) != null ->
                        sameDayActual.getValue(postSlot) - delta
                    else -> null
                }
            }

            // Bedtime can be interpolated only between two actual anchors:
            // dinner-post on this day and fasting on the following morning.
            if (estimate == null && slot == 7) {
                val dinnerPost = actualValues[dayIndex]?.get(6)
                val nextFasting = actualValues[dayIndex + 1]?.get(0)
                if (dinnerPost != null && nextFasting != null) {
                    val dinnerTime = slotTimestamp(dayStart, 6)
                    val nextWakeTime = slotTimestamp(dayStart + dayMillis, 0)
                    val fraction = ((timestamp - dinnerTime).toDouble() /
                        (nextWakeTime - dinnerTime).toDouble()).coerceIn(0.0, 1.0)
                    estimate = dinnerPost + (nextFasting - dinnerPost) * fraction
                }
            }

            // No generic/global fallback: if personal evidence is insufficient,
            // leave this slot absent rather than inventing a representative value.
            if (estimate != null && estimate.isFinite() && estimate > 0.0) {
                slots.add(GlucoseDailySlot(timestamp, estimate, slot, true))
            }
        }
    }
    if (slots.isEmpty()) return null
    val representedSlots = slots.groupBy { it.slotIndex }
    if (representedSlots.keys.size < 4) return null

    // Stratify by time-of-day first: each represented daily slot contributes
    // equally, so a frequently measured fasting slot cannot dominate the day.
    // Within each slot, recent days receive exponentially greater weight
    // (30-day half-life) to reflect the stronger influence of recent glucose.
    val slotMeans = representedSlots.values.map { slotReadings ->
        val weighted = slotReadings.map { slot ->
            val ageDays = ((windowEnd - slot.timestamp).coerceAtLeast(0L)).toDouble() / dayMillis
            val weight = Math.pow(0.5, ageDays / 30.0)
            slot.valueMgDl to weight
        }
        val weightSum = weighted.sumOf { it.second }
        if (weightSum <= 0.0) return null
        weighted.sumOf { it.first * it.second } / weightSum
    }
    val stratifiedAverage = slotMeans.average()
    val actualSlotCount = slots.count { !it.estimated }
    val estimatedSlotCount = slots.count { it.estimated }
    val estimate = TimeWeightedGlucoseEstimate(
        averageMgDl = stratifiedAverage,
        estimatedA1cPercent = (stratifiedAverage + 46.7) / 28.7,
        measurementCount = assignedWindowReadingIds.size,
        intervalCount = slots.size,
        coveredMillis = 90L * dayMillis,
        firstTimestamp = distinctActualTimes.first(),
        lastTimestamp = distinctActualTimes.last(),
        actualSlotCount = actualSlotCount,
        estimatedSlotCount = estimatedSlotCount,
        representedSlotCount = representedSlots.keys.size
    )
    return SlotBasedGlucoseEstimate(
        estimate = estimate,
        slots = slots,
        actualSlotCount = actualSlotCount,
        estimatedSlotCount = estimatedSlotCount
    )
}

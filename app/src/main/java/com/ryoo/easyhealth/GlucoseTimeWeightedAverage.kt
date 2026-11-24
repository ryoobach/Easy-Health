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

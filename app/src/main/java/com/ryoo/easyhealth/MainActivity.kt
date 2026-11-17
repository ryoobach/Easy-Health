package com.ryoo.easyhealth

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import android.content.Intent
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material3.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

private val Green = Color(0xFF16834A)
private val PaleGreen = Color(0xFFEAF5ED)
private val Ink = Color(0xFF183326)

private val vitalTypes = listOf("혈당", "혈압", "체중", "체온", "체지방률", "골격근량", "BMI", "호흡수", "산소포화도", "심전도", "케톤", "식사", "운동", "수면", "특이사항")
private val units = mapOf("혈당" to "mg/dL", "케톤" to "mmol/L", "혈압" to "mmHg", "체온" to "℃", "호흡수" to "회/분", "산소포화도" to "%", "체중" to "kg", "체지방률" to "%", "골격근량" to "kg", "BMI" to "", "심전도" to "", "식사" to "", "운동" to "", "수면" to "", "특이사항" to "")
private val devices = listOf(
    "Contour Plus ONE" to listOf("혈당"),
    "CareSens Dual" to listOf("혈당", "케톤"),
    "혈압계" to listOf("혈압"),
    "체중계" to listOf("체중", "체지방률", "골격근량", "BMI"),
    "체온계" to listOf("체온"),
    "생활 기록" to listOf("식사", "운동", "수면", "특이사항"),
    "산소포화도 측정기" to listOf("산소포화도", "맥박"),
    "심전도 측정기" to listOf("심전도", "맥박")
)

data class VitalRecord(
    val id: String,
    val person: String,
    val type: String,
    val value: String,
    val timestamp: Long,
    val source: String,
    val note: String = "",
    val externalId: String = ""
)



private data class PersonProfile(val name: String, val sex: String = "", val age: Int? = null, val heightCm: Double? = null, val wakeTime: String = "", val breakfastTime: String = "", val lunchTime: String = "", val dinnerTime: String = "", val bedtime: String = "")

private fun encodePeopleProfiles(profiles: Map<String, PersonProfile>): String {
    val array = JSONArray()
    profiles.values.forEach { p ->
        array.put(JSONObject().put("name", p.name).put("sex", p.sex)
            .put("age", p.age ?: JSONObject.NULL).put("heightCm", p.heightCm ?: JSONObject.NULL)
            .put("wakeTime", p.wakeTime).put("breakfastTime", p.breakfastTime).put("lunchTime", p.lunchTime)
            .put("dinnerTime", p.dinnerTime).put("bedtime", p.bedtime))
    }
    return array.toString()
}

private data class RiskInfo(val label: String, val color: Color)

private data class MealPeriod(val label: String, val color: Color)

private fun mealPeriodFor(timestamp: Long, profile: PersonProfile): MealPeriod? {
    val calendar = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
    val minute = calendar.get(java.util.Calendar.HOUR_OF_DAY) * 60 + calendar.get(java.util.Calendar.MINUTE)
    fun parse(value: String): Int? {
        val parts = value.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val min = parts.getOrNull(1)?.toIntOrNull() ?: return null
        return if (hour in 0..23 && min in 0..59) hour * 60 + min else null
    }
    fun circularDistance(a: Int, b: Int): Int {
        val d = kotlin.math.abs(a - b)
        return minOf(d, 1440 - d)
    }
    val wake = parse(profile.wakeTime)
    val breakfast = parse(profile.breakfastTime)
    val lunch = parse(profile.lunchTime)
    val dinner = parse(profile.dinnerTime)
    val bed = parse(profile.bedtime)
    val meals = listOf(
        Triple("아침", breakfast, Pair(Color(0xFF90CAF9), Color(0xFF0D47A1))),
        Triple("점심", lunch, Pair(Color(0xFF80CBC4), Color(0xFF00695C))),
        Triple("저녁", dinner, Pair(Color(0xFFFFCC80), Color(0xFFE65100)))
    ).filter { it.second != null }

    // 기상 후 아침 식사 전은 별도의 공복 구간으로 유지합니다.
    if (wake != null && breakfast != null) {
        val isFasting = if (wake <= breakfast) minute >= wake && minute < breakfast
            else minute >= wake || minute < breakfast
        if (isFasting) return MealPeriod("공복", Color(0xFF3949AB))
    }

    // 취침 기준시간 전후 1시간은 모두 취침전으로 표시합니다.
    if (bed != null && circularDistance(minute, bed) <= 60)
        return MealPeriod("취침전", Color(0xFF8E44AD))

    // 가장 가까운 식사 기준시간을 찾아 기준시간 이전은 식전, 이후는 식후로 표시합니다.
    val nearestMeal = meals.minByOrNull { circularDistance(minute, it.second!!) }
    if (nearestMeal != null) {
        val mealTime = nearestMeal.second!!
        val isBefore = ((mealTime - minute + 1440) % 1440) < 720
        val label = if (isBefore) "${nearestMeal.first}식전" else "${nearestMeal.first}식후"
        return MealPeriod(label, if (isBefore) nearestMeal.third.first else nearestMeal.third.second)
    }
    return null
}
private data class GlucoseMeasurementGroup(val source: String, val timestamp: Long, val average: Double, val values: List<Double>)

// 같은 기기에서 60초 이내 연속 측정한 혈당은 하나의 측정 묶음으로 계산합니다.
private fun glucoseMeasurementGroups(records: List<VitalRecord>, maxGapMillis: Long = 60_000L): List<GlucoseMeasurementGroup> {
    val sorted = records.filter { it.type == "혈당" && it.value.toDoubleOrNull() != null }
        .sortedWith(compareBy<VitalRecord> { it.timestamp }.thenBy { it.source })
    val result = mutableListOf<GlucoseMeasurementGroup>()
    var currentSource: String? = null
    var current = mutableListOf<Pair<Long, Double>>()
    fun flush() {
        if (current.isNotEmpty()) {
            val values = current.map { it.second }
            result.add(GlucoseMeasurementGroup(currentSource.orEmpty(), current.first().first, values.average(), values))
        }
        current = mutableListOf()
    }
    sorted.forEach { record ->
        val value = record.value.toDoubleOrNull() ?: return@forEach
        val source = record.source.ifBlank { "기기 미지정" }
        val previousTime = current.lastOrNull()?.first
        if (current.isNotEmpty() && (source != currentSource || record.timestamp - (previousTime ?: record.timestamp) > maxGapMillis)) flush()
        if (current.isEmpty()) currentSource = source
        current.add(record.timestamp to value)
    }
    flush()
    return result.sortedBy { it.timestamp }
}

private fun normalRangeForGraph(type: String): Pair<Double, Double>? = when (type) {
    "혈당" -> 70.0 to 130.0
    "혈압" -> 90.0 to 120.0
    "체온" -> 35.5 to 37.5
    "산소포화도" -> 95.0 to 100.0
    "호흡수" -> 12.0 to 20.0
    "맥박" -> 60.0 to 100.0
    "BMI" -> 18.5 to 25.0
    else -> null
}

private fun riskFor(type: String, value: String, profile: PersonProfile? = null): RiskInfo {
    val red = Color(0xFFD32F2F)
    val orange = Color(0xFFEF6C00)
    val yellow = Color(0xFFF9A825)
    val green = Color(0xFF16834A)
    val blue = Color(0xFF3976B8)
    val v = value.trim().toDoubleOrNull()
    return when (type) {
        "당화혈색소 추정치" -> when { v == null -> RiskInfo("판정 불가", Color.Gray); v < 5.7 -> RiskInfo("참고 범위", green); v < 6.5 -> RiskInfo("주의 범위", yellow); else -> RiskInfo("높음", red) }
        "혈당" -> when {
            v == null -> RiskInfo("판정 불가", Color.Gray)
            v < 54 -> RiskInfo("심각한 저혈당", Color(0xFF8E0000))
            v < 70 -> RiskInfo("저혈당", orange)
            v <= 130 -> RiskInfo("정상 참고 범위", green)
            v <= 180 -> RiskInfo("약간 높음", yellow)
            else -> RiskInfo("매우 높음", red)
        }
        "혈압" -> {
            val p = value.split("/").mapNotNull { it.toDoubleOrNull() }
            if (p.size < 2) RiskInfo("판정 불가", Color.Gray)
            else when {
                p[0] < 90 || p[1] < 60 -> RiskInfo("낮음", orange)
                p[0] >= 140 || p[1] >= 90 -> RiskInfo("높음", red)
                p[0] >= 130 || p[1] >= 80 -> RiskInfo("주의", orange)
                p[0] >= 120 -> RiskInfo("상승", yellow)
                else -> RiskInfo("참고 범위", green)
            }
        }
        "체온" -> when { v == null -> RiskInfo("판정 불가", Color.Gray); v < 35.5 -> RiskInfo("낮은 체온", orange); v < 37.5 -> RiskInfo("참고 범위", green); v < 38.0 -> RiskInfo("미열 가능", yellow); else -> RiskInfo("발열 가능", red) }
        "산소포화도" -> when { v == null -> RiskInfo("판정 불가", Color.Gray); v <= 92 -> RiskInfo("낮음", red); v <= 94 -> RiskInfo("주의", yellow); else -> RiskInfo("참고 범위", green) }
        "케톤" -> when { v == null -> RiskInfo("판정 불가", Color.Gray); v < 0.6 -> RiskInfo("참고 범위", green); v <= 1.5 -> RiskInfo("주의", yellow); v <= 3.0 -> RiskInfo("높음", orange); else -> RiskInfo("매우 높음", red) }
        "호흡수" -> when { v == null -> RiskInfo("판정 불가", Color.Gray); v < 10 || v > 24 -> RiskInfo("주의", red); v < 12 || v > 20 -> RiskInfo("경계", yellow); else -> RiskInfo("참고 범위", green) }
        "맥박" -> when { v == null -> RiskInfo("판정 불가", Color.Gray); v < 50 || v > 120 -> RiskInfo("주의", red); v < 60 || v > 100 -> RiskInfo("경계", yellow); else -> RiskInfo("참고 범위", green) }
        "BMI" -> when { v == null -> RiskInfo("판정 불가", Color.Gray); v < 18.5 -> RiskInfo("저체중 범주", yellow); v < 25 -> RiskInfo("참고 범위", green); v < 30 -> RiskInfo("과체중 범주", yellow); else -> RiskInfo("비만 범주", red) }
        "체중" -> {
            val h = profile?.heightCm?.div(100.0)
            val bmi = if (v != null && h != null && h > 0) v / (h * h) else null
            when { bmi == null -> RiskInfo("추세 참고", blue); bmi < 18.5 -> RiskInfo("BMI 낮음", yellow); bmi < 25 -> RiskInfo("BMI 참고 범위", green); bmi < 30 -> RiskInfo("BMI 높음", yellow); else -> RiskInfo("BMI 매우 높음", red) }
        }
        "체지방률", "골격근량" -> RiskInfo("개인별 기준 참고", blue)
        "심전도" -> RiskInfo("측정 결과 참고", blue)
        "식사", "운동", "수면", "특이사항" -> RiskInfo("생활 기록", blue)
        else -> RiskInfo("참고 정보", blue)
    }
}

data class HospitalAppointment(val id: Int, val hospital: String, val date: String, val time: String, val memo: String, val status: String = "예정")

private fun saveAppointments(context: Context, appointments: List<HospitalAppointment>) {
    val array = JSONArray()
    appointments.forEach { a -> array.put(JSONObject().put("id", a.id).put("hospital", a.hospital).put("date", a.date).put("time", a.time).put("memo", a.memo).put("status", a.status)) }
    context.getSharedPreferences("easy_health", Context.MODE_PRIVATE).edit().putString("appointments", array.toString()).apply()
}
private fun scheduleAppointmentReminder(context: Context, a: HospitalAppointment) {
    val fmt = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA).apply { isLenient = false }
    val at = fmt.parse("\${a.date} \${a.time}") ?: return
    val intent = android.content.Intent(context, AppointmentReminderReceiver::class.java).apply {
        putExtra("appointment_id", a.id); putExtra("hospital", a.hospital); putExtra("memo", a.memo)
    }
    val pi = android.app.PendingIntent.getBroadcast(context, a.id, intent,
        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
    val alarm = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
    alarm.cancel(pi)
    if (at.time > System.currentTimeMillis()) alarm.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, at.time, pi)
}
private fun cancelAppointmentReminder(context: Context, id: Int) {
    val intent = android.content.Intent(context, AppointmentReminderReceiver::class.java)
    val pi = android.app.PendingIntent.getBroadcast(context, id, intent,
        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
    (context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager).cancel(pi)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { EasyHealthApp(this) }
    }
}

private fun encodeRecords(records: List<VitalRecord>): String {
    val array = JSONArray()
    records.forEach { r ->
        array.put(JSONObject().put("id", r.id).put("person", r.person).put("type", r.type)
            .put("value", r.value).put("timestamp", r.timestamp).put("source", r.source)
            .put("note", r.note).put("externalId", r.externalId))
    }
    return array.toString()
}

private fun persistRecords(context: Context, records: List<VitalRecord>) {
    context.getSharedPreferences("easy_health", Context.MODE_PRIVATE)
        .edit().putString("vital_records", encodeRecords(records)).apply()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun EasyHealthApp(context: Context) {
    val prefs = remember { context.getSharedPreferences("easy_health", Context.MODE_PRIVATE) }
    val records = remember {
        mutableStateListOf<VitalRecord>().apply {
            val raw = prefs.getString("vital_records", null)
            if (raw != null) {
                try {
                    val a = JSONArray(raw)
                    for (i in 0 until a.length()) {
                        val o = a.getJSONObject(i)
                        add(VitalRecord(o.optString("id", UUID.randomUUID().toString()), o.optString("person", "본인"),
                            o.optString("type", "혈당"), o.optString("value", ""), o.optLong("timestamp"),
                            o.optString("source", "직접 입력"), o.optString("note", ""), o.optString("externalId", "")))
                    }
                } catch (_: Exception) { }
            } else {
                try {
                    val old = JSONArray(prefs.getString("glucose_entries", "[]") ?: "[]")
                    for (i in 0 until old.length()) {
                        val o = old.getJSONObject(i)
                        add(VitalRecord(UUID.randomUUID().toString(), "본인", "혈당", o.optInt("value").toString(),
                            o.optLong("timestamp"), o.optString("source", "직접 입력"), o.optString("category", "")))
                    }
                    if (isNotEmpty()) persistRecords(context, this)
                } catch (_: Exception) { }
            }
        }
    }
    val people = remember {
        mutableStateListOf<String>().apply {
            val a = JSONArray(prefs.getString("people", "[\"본인\"]") ?: "[\"본인\"]")
            for (i in 0 until a.length()) add(a.optString(i))
            if (isEmpty()) add("본인")
        }
    }
    val personProfiles = remember {
        mutableStateMapOf<String, PersonProfile>().apply {
            try {
                val saved = JSONArray(prefs.getString("person_profiles", "[]") ?: "[]")
                for (i in 0 until saved.length()) {
                    val o = saved.getJSONObject(i)
                    val name = o.optString("name")
                    if (name.isNotBlank()) put(name, PersonProfile(name, o.optString("sex"),
                        if (o.isNull("age")) null else o.optInt("age"),
                        if (o.isNull("heightCm")) null else o.optDouble("heightCm"),
                        o.optString("wakeTime", ""), o.optString("breakfastTime", ""), o.optString("lunchTime", ""),
                        o.optString("dinnerTime", ""), o.optString("bedtime", "")))
                }
            } catch (_: Exception) { }
            people.forEach { if (!containsKey(it)) put(it, PersonProfile(it)) }
        }
    }
    fun saveProfiles() { prefs.edit().putString("person_profiles", encodePeopleProfiles(personProfiles)).apply() }
    var selectedPerson by remember { mutableStateOf(prefs.getString("selected_person", "본인") ?: "본인") }
    if (selectedPerson !in people) selectedPerson = people.first()
    val enabledDevices = remember {
        mutableStateListOf<String>().apply {
            val a = JSONArray(prefs.getString("enabled_devices", "[\"Contour Plus ONE\",\"CareSens Dual\",\"생활 기록\"]") ?: "[]")
            for (i in 0 until a.length()) add(a.optString(i))
            if (!prefs.getBoolean("lifestyle_default_migrated", false)) {
                if (!contains("생활 기록")) add("생활 기록")
                prefs.edit().putString("enabled_devices", JSONArray(this).toString()).putBoolean("lifestyle_default_migrated", true).apply()
            }
        }
    }
    var tab by remember { mutableStateOf("기록") }
    val appointments = remember {
        mutableStateListOf<HospitalAppointment>().apply {
            try {
                val a = JSONArray(prefs.getString("appointments", "[]") ?: "[]")
                for (i in 0 until a.length()) { val o = a.getJSONObject(i); add(HospitalAppointment(o.optInt("id"), o.optString("hospital"), o.optString("date"), o.optString("time"), o.optString("memo"), o.optString("status", "예정"))) }
            } catch (_: Exception) { }
        }
    }
    var showAppointmentDialog by remember { mutableStateOf(false) }
    var editingAppointment by remember { mutableStateOf<HospitalAppointment?>(null) }
    var appointmentSection by remember { mutableStateOf("예정 일정") }
    var selectedAppointmentDate by remember { mutableStateOf(SimpleDateFormat("yyyy.MM.dd", Locale.KOREA).format(Date())) }
    var appointmentInitialDate by remember { mutableStateOf<String?>(null) }
    var appointmentDeleteTarget by remember { mutableStateOf<HospitalAppointment?>(null) }
    var showRecordDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<VitalRecord?>(null) }
    var showPeopleDialog by remember { mutableStateOf(false) }
    val registeredDevices = remember { mutableStateListOf<String>().apply { val a = JSONArray(prefs.getString("registered_devices", "[]") ?: "[]"); for (i in 0 until a.length()) add(a.optString(i)) } }
    var showRegisterDeviceDialog by remember { mutableStateOf(false) }
    val registerSelections = remember { mutableStateListOf<String>() }
    var deviceDeleteTarget by remember { mutableStateOf<String?>(null) }
    var personEditing by remember { mutableStateOf<PersonProfile?>(null) }
    var personDeleteTarget by remember { mutableStateOf<String?>(null) }
    var showDeviceInfo by remember { mutableStateOf(false) }
    var showTransferDialog by remember { mutableStateOf(false) }
    var transferMode by remember { mutableStateOf("보내기") }
    var transferStatus by remember { mutableStateOf("전송 방식을 선택해 주세요.") }
    var transferPeers by remember { mutableStateOf<List<TransferPeer>>(emptyList()) }
    var transferTarget by remember { mutableStateOf<TransferPeer?>(null) }
    var transferOffer by remember { mutableStateOf<TransferOffer?>(null) }
    var transferProgress by remember { mutableStateOf(0f) }
    var transferPerson by remember { mutableStateOf(selectedPerson) }
    var transferFrom by remember { mutableStateOf(Calendar.getInstance().apply { add(Calendar.MONTH, -1) }.timeInMillis) }
    var transferTo by remember { mutableStateOf(System.currentTimeMillis()) }
    var showTransferPersonMenu by remember { mutableStateOf(false) }
    var showBluetoothSearch by remember { mutableStateOf(false) }
    var showTransferBluetoothSearch by remember { mutableStateOf(false) }
    var transferSearching by remember { mutableStateOf(false) }
    var transferServerActive by remember { mutableStateOf(false) }
    var transferStartPending by remember { mutableStateOf(false) }
    var discoverabilityRequestPending by remember { mutableStateOf(false) }
    var receiverServerStartRequested by remember { mutableStateOf(false) }
    var bluetoothStatus by remember { mutableStateOf("측정기를 페어링 모드로 바꾼 뒤 검색해 주세요.") }
    var activeBluetoothName by remember { mutableStateOf("블루투스 측정기") }
    var bluetoothCandidates by remember { mutableStateOf<List<MeterCandidate>>(emptyList()) }
    val bluetoothClient = remember { BluetoothGlucoseClient(context) }
    val recordTransfer = remember { BluetoothRecordTransfer(context) }
    val bluetoothListener = remember(selectedPerson) {
        object : BluetoothGlucoseClient.Listener {
            override fun onStatus(message: String) { bluetoothStatus = message }
            override fun onDevices(devices: List<MeterCandidate>) { bluetoothCandidates = devices }
            override fun onConnected(device: MeterCandidate) {
                activeBluetoothName = device.name
                val model = if (device.name.lowercase().contains("caresens")) "CareSens Dual" else "Contour Plus ONE"
                if (!registeredDevices.contains(model)) registeredDevices.add(model)
                prefs.edit().putString("registered_devices", JSONArray(registeredDevices).toString()).apply()
                val saved = JSONArray(prefs.getString("bluetooth_devices", "[]") ?: "[]")
                val updated = JSONArray()
                for (i in 0 until saved.length()) {
                    val item = saved.optJSONObject(i) ?: continue
                    if (item.optString("address") != device.address) updated.put(item)
                }
                updated.put(JSONObject().put("name", device.name).put("address", device.address).put("model", model))
                prefs.edit().putString("bluetooth_devices", updated.toString()).apply()
                showBluetoothSearch = false
                showRegisterDeviceDialog = false
                android.widget.Toast.makeText(context, "$model 연결 성공", android.widget.Toast.LENGTH_SHORT).show()
            }
            override fun onReadings(readings: List<MeterReading>) {
                var imported = 0
                readings.forEach { reading ->
                    val deviceName = activeBluetoothName
                    val externalId = "$deviceName:${reading.sequence}:${reading.timestamp}"
                    if (records.none { it.externalId == externalId }) {
                        records.add(VitalRecord(UUID.randomUUID().toString(), selectedPerson, reading.type,
                            reading.value, reading.timestamp, deviceName, "", externalId))
                        imported++
                    }
                }
                persistRecords(context, records)
                bluetoothStatus = "${imported}건의 측정 기록을 가져왔습니다."
                android.widget.Toast.makeText(context, bluetoothStatus, android.widget.Toast.LENGTH_LONG).show()
            }
            override fun onError(message: String) { bluetoothStatus = message }
        }
    }
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.all { it }) bluetoothClient.startScan(bluetoothListener)
        else bluetoothStatus = "주변 기기 검색 및 연결 권한을 허용해 주세요."
    }
    fun requestBluetoothScan() {
        val permissions = if (android.os.Build.VERSION.SDK_INT >= 31)
            arrayOf(android.Manifest.permission.BLUETOOTH_SCAN, android.Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
        val missing = permissions.filter { context.checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) bluetoothClient.startScan(bluetoothListener)
        else bluetoothPermissionLauncher.launch(missing.toTypedArray())
    }
    val transferListener = remember {
        object : BluetoothRecordTransfer.Listener {
            private fun onUi(action: () -> Unit) { android.os.Handler(android.os.Looper.getMainLooper()).post(action) }
            override fun onStatus(message: String) {
                onUi {
                    transferStatus = message
                    if (message.contains("검색이 끝났습니다") || message.contains("검색을 취소") ||
                        message.contains("검색을 시작하지 못") || message.contains("검색에 실패")) transferSearching = false
                    if (message.contains("수신 서버를 시작")) transferStartPending = true
                    if (message.contains("수신 대기 중")) {
                        transferStartPending = false
                        transferServerActive = true
                    }
                    if (message.contains("전송이 완료") || message.contains("가져오기 완료") ||
                        message.contains("실패") || message.contains("오류") ||
                        message.contains("수신 대기를 취소") || message.contains("허용이 취소")) {
                        transferServerActive = false
                        transferStartPending = false
                    }
                }
            }
            override fun onPeers(peers: List<TransferPeer>) { onUi { transferPeers = peers } }
            override fun onIncoming(offer: TransferOffer) { onUi { transferServerActive = false; transferOffer = offer } }
            override fun onProgress(done: Int, total: Int) {
                onUi {
                    transferProgress = if (total > 0) done.toFloat() / total else 0f
                    transferStatus = "전송 중 " + (transferProgress * 100).toInt() + "%"
                }
            }
            override fun onReceived(json: String, offer: TransferOffer) {
                onUi {
                    try {
                        val array = JSONArray(json)
                        var imported = 0
                        var duplicates = 0
                        if (!people.contains(offer.person)) {
                            people.add(offer.person)
                            personProfiles[offer.person] = PersonProfile(offer.person)
                            prefs.edit().putString("people", JSONArray(people).toString()).apply()
                            saveProfiles()
                        }
                        for (i in 0 until array.length()) {
                            val o = array.getJSONObject(i)
                            val person = o.optString("person", offer.person).ifBlank { offer.person }
                            val item = VitalRecord(o.optString("id", UUID.randomUUID().toString()), person,
                                o.optString("type", ""), o.optString("value", ""), o.optLong("timestamp"),
                                o.optString("source", "블루투스 전송"), o.optString("note", ""), o.optString("externalId", ""))
                            if (item.type.isNotBlank() && item.value.isNotBlank()) {
                                if (records.none { it.id == item.id || (it.person == item.person && it.type == item.type && it.timestamp == item.timestamp && it.value == item.value) }) {
                                    records.add(item); imported++
                                } else duplicates++
                            }
                        }
                        persistRecords(context, records)
                        transferStatus = "가져오기 완료: 새 기록 " + imported + "건 · 중복 제외 " + duplicates + "건"
                        transferProgress = 1f
                        transferServerActive = false
                        transferStartPending = false
                        discoverabilityRequestPending = false
                        transferOffer = null
                    } catch (e: Exception) {
                        transferStatus = "가져온 데이터 처리 실패: " + (e.localizedMessage ?: "형식 오류")
                    }
                }
            }
            override fun onError(message: String) {
                Log.e("EasyHealthTransfer", "Transfer error: $message")
                onUi {
                    transferStatus = "오류: $message"
                    transferSearching = false
                    transferServerActive = false
                    transferStartPending = false
                    discoverabilityRequestPending = false
                    receiverServerStartRequested = false
                }
            }
        }
    }
    fun startReceiverServerOnce(reason: String) {
        if (!discoverabilityRequestPending) {
            Log.w("EasyHealthTransfer", "Ignoring server start trigger outside a discoverability request; reason=$reason")
            return
        }
        if (receiverServerStartRequested) {
            Log.i("EasyHealthTransfer", "Duplicate server start trigger ignored; reason=$reason")
            return
        }
        val connectGranted = android.os.Build.VERSION.SDK_INT < 31 ||
            context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val advertiseGranted = android.os.Build.VERSION.SDK_INT < 31 ||
            context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_ADVERTISE) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val scanMode = try {
            (context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager).adapter?.scanMode
                ?: android.bluetooth.BluetoothAdapter.SCAN_MODE_NONE
        } catch (e: Exception) {
            Log.e("EasyHealthTransfer", "Unable to read Bluetooth scanMode at $reason", e)
            android.bluetooth.BluetoothAdapter.SCAN_MODE_NONE
        }
        Log.i("EasyHealthTransfer", "Receiver start trigger=$reason scanMode=$scanMode connectGranted=$connectGranted advertiseGranted=$advertiseGranted")
        if (!connectGranted || !advertiseGranted) {
            transferStatus = "기기 검색은 허용됐지만 블루투스 권한이 확인되지 않습니다. 앱 권한을 확인한 뒤 다시 시도해 주세요."
            transferStartPending = false
            discoverabilityRequestPending = false
            Log.e("EasyHealthTransfer", "Bluetooth permission check failed: connect=$connectGranted advertise=$advertiseGranted")
            return
        }
        receiverServerStartRequested = true
        transferStartPending = true
        transferStatus = "기기 검색 허용을 확인했습니다. RFCOMM 수신 서버를 시작하고 있습니다."
        Log.i("EasyHealthTransfer", "Starting RFCOMM server once; trigger=$reason")
        recordTransfer.startServer(transferListener)
    }

    val discoverabilityLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val scanMode = try {
            (context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager).adapter?.scanMode
                ?: android.bluetooth.BluetoothAdapter.SCAN_MODE_NONE
        } catch (e: Exception) {
            Log.e("EasyHealthTransfer", "Unable to read scanMode in ActivityResult callback", e)
            android.bluetooth.BluetoothAdapter.SCAN_MODE_NONE
        }
        Log.i("EasyHealthTransfer", "Discoverability ActivityResult callback entered; resultCode=${result.resultCode}, dataAction=${result.data?.action}, scanMode=$scanMode")
        transferServerActive = false
        if (result.resultCode > 0) {
            startReceiverServerOnce("activity-result")
        } else {
            transferStatus = "기기 검색 허용이 취소되었거나 Android에서 요청을 완료하지 못했습니다. 다시 시도해 주세요."
            transferStartPending = false
            discoverabilityRequestPending = false
            receiverServerStartRequested = false
            Log.w("EasyHealthTransfer", "Discoverability denied/cancelled; resultCode=${result.resultCode}, scanMode=$scanMode")
        }
    }

    val discoverabilityReceiver = remember {
        object : android.content.BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent?.action != android.bluetooth.BluetoothAdapter.ACTION_SCAN_MODE_CHANGED) return
                val announcedScanMode = intent.getIntExtra(
                    android.bluetooth.BluetoothAdapter.EXTRA_SCAN_MODE,
                    android.bluetooth.BluetoothAdapter.SCAN_MODE_NONE
                )
                val previousMode = intent.getIntExtra(
                    android.bluetooth.BluetoothAdapter.EXTRA_PREVIOUS_SCAN_MODE,
                    android.bluetooth.BluetoothAdapter.SCAN_MODE_NONE
                )
                val actualScanMode = try {
                    (context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager).adapter?.scanMode
                        ?: android.bluetooth.BluetoothAdapter.SCAN_MODE_NONE
                } catch (e: Exception) {
                    Log.e("EasyHealthTransfer", "Unable to verify adapter scanMode from broadcast", e)
                    android.bluetooth.BluetoothAdapter.SCAN_MODE_NONE
                }
                Log.i("EasyHealthTransfer", "ACTION_SCAN_MODE_CHANGED previous=$previousMode announced=$announcedScanMode actual=$actualScanMode pending=$discoverabilityRequestPending")
                // The receiver is exported so Android's privileged Bluetooth process can reach it.
                // Verify the adapter's real state as well, so a spoofed broadcast cannot start the server.
                if (announcedScanMode == android.bluetooth.BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE &&
                    actualScanMode == android.bluetooth.BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE &&
                    discoverabilityRequestPending) {
                    startReceiverServerOnce("scan-mode-broadcast")
                }
            }
        }
    }
    DisposableEffect(context, discoverabilityReceiver) {
        val filter = android.content.IntentFilter(android.bluetooth.BluetoothAdapter.ACTION_SCAN_MODE_CHANGED)
        try {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                // Bluetooth broadcasts can originate from a privileged Bluetooth app UID.
                context.registerReceiver(discoverabilityReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(discoverabilityReceiver, filter)
            }
            Log.i("EasyHealthTransfer", "Registered ACTION_SCAN_MODE_CHANGED receiver")
        } catch (e: Exception) {
            Log.e("EasyHealthTransfer", "Failed to register scan-mode receiver", e)
            transferStatus = "블루투스 상태 감시기를 등록하지 못했습니다: ${e.localizedMessage ?: e.javaClass.simpleName}"
        }
        onDispose {
            try { context.unregisterReceiver(discoverabilityReceiver) } catch (_: Exception) { }
            Log.i("EasyHealthTransfer", "Unregistered ACTION_SCAN_MODE_CHANGED receiver")
        }
    }
    fun startTransferAction() {
        if (transferMode == "보내기") {
            transferTarget = null
            transferPeers = emptyList()
            transferSearching = true
            showTransferBluetoothSearch = true
            transferStatus = "주변 블루투스 기기를 검색하고 있습니다."
            recordTransfer.startDiscovery(transferListener)
        } else {
            // Do not mark the server active until the RFCOMM socket is actually listening.
            transferServerActive = false
            transferStartPending = false
            discoverabilityRequestPending = true
            receiverServerStartRequested = false
            transferStatus = "Android 기기 검색 허용을 기다리고 있습니다."
            Log.i("EasyHealthTransfer", "Launching ACTION_REQUEST_DISCOVERABLE; scanMode will be checked by result and broadcast")
            val request = Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                .putExtra(android.bluetooth.BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
            discoverabilityLauncher.launch(request)
        }
    }
    val transferPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.all { it }) startTransferAction()
        else {
            transferSearching = false
            transferServerActive = false
            transferStartPending = false
            discoverabilityRequestPending = false
            receiverServerStartRequested = false
            transferStatus = "블루투스 작업에 필요한 권한이 거부되었습니다. 앱 권한 설정에서 허용해 주세요."
        }
    }
    fun requestTransferPermissions() {
        // Open the search surface before requesting permissions. If Android denies a permission
        // or the tablet's permission flow differs, the user still sees the actionable search/error UI.
        if (transferMode == "보내기") {
            transferTarget = null
            transferPeers = emptyList()
            transferSearching = true
            showTransferBluetoothSearch = true
            transferStatus = "블루투스 권한을 확인하고 주변 기기를 검색합니다."
        }
        val required = if (android.os.Build.VERSION.SDK_INT >= 31) {
            if (transferMode == "보내기")
                arrayOf(android.Manifest.permission.BLUETOOTH_SCAN, android.Manifest.permission.BLUETOOTH_CONNECT)
            else arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT, android.Manifest.permission.BLUETOOTH_ADVERTISE)
        } else {
            if (transferMode == "보내기") arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION) else emptyArray()
        }
        val missing = required.filter { context.checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) startTransferAction()
        else transferPermissionLauncher.launch(missing.toTypedArray())
    }
    DisposableEffect(Unit) { onDispose { bluetoothClient.close(); recordTransfer.close() } }
    var showAppInfo by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var ownerNameInput by remember { mutableStateOf(prefs.getString("owner_name", "") ?: "") }
    var showOwnerSetup by remember { mutableStateOf(ownerNameInput.isBlank()) }
    var deleteTarget by remember { mutableStateOf<VitalRecord?>(null) }
    var reportPeriod by remember { mutableStateOf((prefs.getString("last_report_period", "1개월") ?: "1개월").let { when (it) { "월간" -> "1개월"; "분기별" -> "3개월"; else -> it } }) }
    var selectedReportVital by remember { mutableStateOf(prefs.getString("last_report_vital", "혈당") ?: "혈당") }
    var deviceHelpTarget by remember { mutableStateOf<String?>(null) }
    val dateFormat = remember { SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA) }
    val personRecords = records.filter { it.person == selectedPerson }.sortedByDescending { it.timestamp }
    val selectedProfile = personProfiles[selectedPerson] ?: PersonProfile(selectedPerson)
    val activeTypes = devices.filter { enabledDevices.contains(it.first) && registeredDevices.contains(it.first) }.flatMap { it.second }.distinct()
    // 리포트는 초기 화면 표시 설정과 분리하고, 등록된 기기의 측정 항목을 자동 포함합니다.
    val reportEnabledTypes = devices.filter { registeredDevices.contains(it.first) }.flatMap { it.second }.distinct()
    val reportRecords = personRecords.filter { reportEnabledTypes.contains(it.type) }
    val availableReportTypes = listOf("혈당", "혈압", "체온", "체중", "체지방률", "골격근량", "BMI", "호흡수", "산소포화도", "심전도", "케톤", "식사", "운동", "수면", "특이사항", "맥박").filter { reportEnabledTypes.contains(it) }
    if (availableReportTypes.isNotEmpty() && selectedReportVital !in availableReportTypes) selectedReportVital = availableReportTypes.first()
    val now = System.currentTimeMillis()
    val reportStart = Calendar.getInstance().apply {
        timeInMillis = now
        when (reportPeriod) {
            "1주일" -> add(Calendar.DAY_OF_YEAR, -7)
            "1개월" -> add(Calendar.MONTH, -1)
            "3개월" -> add(Calendar.MONTH, -3)
        }
    }.timeInMillis
    val periodRecords = if (reportPeriod == "전체") reportRecords else reportRecords.filter { it.timestamp in reportStart..now }
    // 초기 화면과 건강 리포트는 동일한 최근 90일 시간 가중 추정 결과를 사용합니다.
    val timeWeightedA1cEstimate = calculateTimeWeightedGlucoseEstimate(personRecords, now)

    MaterialTheme(colorScheme = lightColorScheme(primary = Green, secondary = Green, background = Color(0xFFF4F8F5), surface = Color.White)) {
        Scaffold(
            modifier = Modifier.pointerInput(tab) {
                var swipeDistance = 0f
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val tabs = listOf("기록", "리포트", "병원 일정", "기기 설정")
                        val index = tabs.indexOf(tab)
                        if (swipeDistance < -90f && index < tabs.lastIndex) tab = tabs[index + 1]
                        if (swipeDistance > 90f && index > 0) tab = tabs[index - 1]
                        swipeDistance = 0f
                    },
                    onHorizontalDrag = { _, amount -> swipeDistance += amount }
                )
            },
            containerColor = Color(0xFFF4F8F5),
            topBar = {
                TopAppBar(title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_launcher), contentDescription = "Easy Health 앱 아이콘", modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Easy Health", fontWeight = FontWeight.Bold, color = Ink, fontSize = 14.sp, maxLines = 1, softWrap = false)
                    }
                }, actions = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                        TextButton(onClick = { showPeopleDialog = true }, modifier = Modifier.widthIn(max = 66.dp), contentPadding = PaddingValues(horizontal = 1.dp)) {
                            Text(selectedPerson + " ▾", color = Green, fontSize = 10.sp, maxLines = 1, softWrap = false)
                        }
                        IconButton(onClick = { personEditing = PersonProfile("") }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Add, contentDescription = "사용자 추가", tint = Green, modifier = Modifier.size(18.dp)) }
                        IconButton(onClick = { (context as? Activity)?.moveTaskToBack(true) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Home, contentDescription = "홈", tint = Green, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { showHelpDialog = true }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Help, contentDescription = "도움말", tint = Green, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { showAppInfo = true }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Info, contentDescription = "앱정보", tint = Green, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { (context as? Activity)?.finishAndRemoveTask() }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.ExitToApp, contentDescription = "앱종료", tint = Green, modifier = Modifier.size(18.dp))
                        }
                    }
                }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFF4F8F5)))
            },
            bottomBar = {
                NavigationBar(containerColor = Color.White) {
                    listOf("기록", "리포트", "병원 일정", "기기 설정").forEach { name ->
                        NavigationBarItem(selected = tab == name, onClick = { tab = name },
                            icon = {
                                when (name) {
                                    "기록" -> Icon(Icons.Default.MonitorHeart, contentDescription = "기록")
                                    "리포트" -> Icon(Icons.Default.Assessment, contentDescription = "리포트")
                                    "병원 일정" -> Box(Modifier.size(24.dp)) {
                                        Icon(Icons.Default.CalendarMonth, contentDescription = "병원 일정", modifier = Modifier.align(Alignment.Center))
                                        Icon(Icons.Default.MedicalServices, contentDescription = null, modifier = Modifier.align(Alignment.BottomEnd).size(11.dp))
                                    }
                                    else -> Box(Modifier.size(24.dp)) {
                                        Icon(Icons.Default.Settings, contentDescription = "기기 설정", modifier = Modifier.align(Alignment.Center))
                                        Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.align(Alignment.BottomEnd).size(11.dp))
                                    }
                                }
                            },
                            label = { Text(name) })
                    }
                }
            }
        ) { padding ->
            when (tab) {
                "기록" -> Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                val glucoseRecord = personRecords.firstOrNull { it.type == "혈당" }
                                if (activeTypes.contains("혈당")) {
                                    Card(Modifier.width(148.dp), colors = CardDefaults.cardColors(containerColor = PaleGreen), shape = RoundedCornerShape(16.dp)) {
                                        Column(Modifier.padding(horizontal = 11.dp, vertical = 3.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                                Text("혈당", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                                Spacer(Modifier.weight(1f))
                                                Text(glucoseRecord?.let { dateFormat.format(Date(it.timestamp)) } ?: "기록 없음", color = Color.Gray, fontSize = 8.sp, maxLines = 1)
                                            }
                                            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text(glucoseRecord?.value ?: "--", color = glucoseRecord?.let { riskFor(it.type, it.value, selectedProfile).color } ?: Color.Gray, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                                                Text("mg/dL", color = Green, fontSize = 9.sp, modifier = Modifier.padding(bottom = 4.dp))
                                            }
                                            glucoseRecord?.let { Text(riskFor(it.type, it.value, selectedProfile).label, color = riskFor(it.type, it.value, selectedProfile).color, fontSize = 9.sp, fontWeight = FontWeight.SemiBold) }
                                            HorizontalDivider(color = Color(0xFFD5E8D9), thickness = 1.dp, modifier = Modifier.padding(vertical = 2.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text("당화혈색소 추정", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                                                Text(timeWeightedA1cEstimate?.let { String.format(Locale.KOREA, "%.1f%%", it.estimatedA1cPercent) } ?: "--", color = timeWeightedA1cEstimate?.let { riskFor("당화혈색소 추정치", it.estimatedA1cPercent.toString(), selectedProfile).color } ?: Color.Gray, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                            }
                                            Text(
                                                timeWeightedA1cEstimate?.let {
                                                    "측정 ${it.measurementCount}회 · 반영 ${String.format(Locale.KOREA, "%.1f", it.coveredHours)}시간 · 기간 ${SimpleDateFormat("MM.dd", Locale.KOREA).format(Date(it.firstTimestamp))}~${SimpleDateFormat("MM.dd", Locale.KOREA).format(Date(it.lastTimestamp))}"
                                                } ?: "기록량 부족 또는 긴 공백으로 추정 불가",
                                                color = Color.Gray, fontSize = 7.sp, lineHeight = 8.sp
                                            )
                                            Text("최근 90일 시간 가중 추정 · 실제 검사값 아님", color = Color.Gray, fontSize = 7.sp, lineHeight = 8.sp)
                                        }
                                    }
                                }
                                val bp = personRecords.firstOrNull { it.type == "혈압" }
                                if (activeTypes.contains("혈압") || activeTypes.any { it in listOf("수축기 혈압", "이완기 혈압", "맥박") }) {
                                    val bpParts = bp?.value?.split("/")
                                    val systolic = bpParts?.getOrNull(0) ?: personRecords.firstOrNull { it.type == "수축기 혈압" }?.value
                                    val diastolic = bpParts?.getOrNull(1) ?: personRecords.firstOrNull { it.type == "이완기 혈압" }?.value
                                    val pulse = bpParts?.getOrNull(2) ?: personRecords.firstOrNull { it.type == "맥박" }?.value
                                    Card(Modifier.width(158.dp), colors = CardDefaults.cardColors(containerColor = PaleGreen), shape = RoundedCornerShape(16.dp)) {
                                        Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                            Text("혈압 · 맥박", color = Ink, fontWeight = FontWeight.SemiBold)
                                            Text(if (systolic != null && diastolic != null) "$systolic/$diastolic" else "--/--", color = bp?.let { riskFor("혈압", it.value, selectedProfile).color } ?: Color.Gray, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                                            bp?.let { Text(riskFor("혈압", it.value, selectedProfile).label, color = riskFor("혈압", it.value, selectedProfile).color, fontSize = 10.sp) }
                                            Text("mmHg", color = Green, fontSize = 11.sp)
                                            Text("맥박 ${pulse ?: "--"}회/분", color = pulse?.let { riskFor("맥박", it, selectedProfile).color } ?: Color.Gray, fontSize = 12.sp)
                                            pulse?.let { Text(riskFor("맥박", it, selectedProfile).label, color = riskFor("맥박", it, selectedProfile).color, fontSize = 10.sp) }
                                        }
                                    }
                                }
                                if (activeTypes.contains("체온")) {
                                    val latest = personRecords.firstOrNull { it.type == "체온" }
                                    Card(Modifier.width(148.dp), colors = CardDefaults.cardColors(containerColor = PaleGreen), shape = RoundedCornerShape(16.dp)) {
                                        Column(Modifier.padding(horizontal = 14.dp, vertical = 5.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text("체온", color = Ink, fontWeight = FontWeight.SemiBold)
                                            Text(latest?.value ?: "--", color = latest?.let { riskFor(it.type, it.value, selectedProfile).color } ?: Color.Gray, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                                            latest?.let { Text(riskFor(it.type, it.value, selectedProfile).label, color = riskFor(it.type, it.value, selectedProfile).color, fontSize = 10.sp) }
                                            Text("℃", color = Green, fontSize = 11.sp)
                                            Text(latest?.let { dateFormat.format(Date(it.timestamp)) } ?: "기록 없음", color = Color.Gray, fontSize = 10.sp)
                                        }
                                    }
                                }
                                val scaleTypes = listOf("체중", "체지방률", "골격근량", "BMI")
                                if (scaleTypes.any { activeTypes.contains(it) }) {
                                    Card(Modifier.width(178.dp), colors = CardDefaults.cardColors(containerColor = PaleGreen), shape = RoundedCornerShape(16.dp)) {
                                        Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                            Text("체중계 측정값", color = Ink, fontWeight = FontWeight.SemiBold)
                                            scaleTypes.filter { activeTypes.contains(it) }.forEach { scaleType ->
                                                val latest = personRecords.firstOrNull { it.type == scaleType }
                                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                                    Text(if (scaleType == "체중") "몸무게" else scaleType, color = Ink, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                                    Text((latest?.value ?: "--") + (units[scaleType]?.let { " $it" } ?: ""), color = latest?.let { riskFor(it.type, it.value, selectedProfile).color } ?: Color.Gray, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                                val orderedTypes = listOf("호흡수", "산소포화도", "심전도", "케톤") + activeTypes.filter { it !in listOf("혈당", "케톤", "혈압", "수축기 혈압", "이완기 혈압", "맥박", "체온", "체중", "체지방률", "골격근량", "BMI", "식사", "운동", "수면", "특이사항") }
                                orderedTypes.distinct().filter { activeTypes.contains(it) }.forEach { vital ->
                                    val latest = personRecords.firstOrNull { it.type == vital }
                                    Card(Modifier.width(148.dp), colors = CardDefaults.cardColors(containerColor = PaleGreen), shape = RoundedCornerShape(16.dp)) {
                                        Column(Modifier.padding(horizontal = 14.dp, vertical = 5.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(vital, color = Ink, fontWeight = FontWeight.SemiBold)
                                            Text(latest?.value ?: "--", color = latest?.let { riskFor(it.type, it.value, selectedProfile).color } ?: Color.Gray, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                                            latest?.let { Text(riskFor(it.type, it.value, selectedProfile).label, color = riskFor(it.type, it.value, selectedProfile).color, fontSize = 10.sp) }
                                            Text(units[vital] ?: "", color = Green, fontSize = 11.sp)
                                            Text(latest?.let { dateFormat.format(Date(it.timestamp)) } ?: "기록 없음", color = Color.Gray, fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                            if (activeTypes.isEmpty()) Text("기기 설정에서 표시할 바이탈 항목을 선택해 주세요.", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("통합 건강 기록", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
                        Spacer(Modifier.weight(1f))
                        Text("${personRecords.size}건", color = Color.Gray, fontSize = 12.sp)
                    }
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("측정 기록 추가", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
                            Button(onClick = { editing = null; showRecordDialog = true }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("측정 기록 추가")
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { showDeviceInfo = true }, modifier = Modifier.weight(1f).heightIn(min = 54.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
                            Icon(Icons.Default.Bluetooth, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(3.dp)); Text("블루투스 자동 가져오기", fontSize = 9.sp, lineHeight = 11.sp)
                        }
                        OutlinedButton(onClick = { transferMode = "보내기"; showTransferDialog = true; transferStatus = "전송할 사용자와 기간을 선택해 주세요." }, modifier = Modifier.weight(1f).heightIn(min = 54.dp), contentPadding = PaddingValues(horizontal = 4.dp)) { Text("데이터 보내기", fontSize = 9.sp) }
                        OutlinedButton(onClick = { transferMode = "가져오기"; showTransferDialog = true; transferStatus = "가져올 준비를 해 주세요." }, modifier = Modifier.weight(1f).heightIn(min = 54.dp), contentPadding = PaddingValues(horizontal = 4.dp)) { Text("데이터 가져오기", fontSize = 9.sp) }
                    }
                    if (personRecords.isEmpty()) {
                        Text("이 대상자의 측정 기록이 없습니다.", color = Color.Gray, modifier = Modifier.padding(12.dp))
                    } else {
                        personRecords.forEach { record ->
                            Card(modifier = Modifier.fillMaxWidth().clickable { editing = record; showRecordDialog = true },
                                shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                        Text(record.type, color = Ink, fontWeight = FontWeight.SemiBold)
                                        Text(dateFormat.format(Date(record.timestamp)), color = Color.Gray, fontSize = 10.sp, lineHeight = 12.sp)
                                        Text(record.source + if (record.note.isNotBlank()) " · " + record.note else "", color = Color.Gray, fontSize = 10.sp, lineHeight = 12.sp)
                                    }
                                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(0.dp)) {
                                        val risk = riskFor(record.type, record.value, selectedProfile)
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            if (record.type == "혈당") mealPeriodFor(record.timestamp, selectedProfile)?.let { period ->
                                                Text(period.label, color = period.color, fontSize = 9.sp, fontWeight = FontWeight.Bold, lineHeight = 11.sp)
                                            }
                                            Text(record.value + (units[record.type]?.let { if (it.isNotBlank()) " $it" else "" } ?: ""),
                                                color = risk.color, fontSize = 18.sp, fontWeight = FontWeight.Bold, lineHeight = 20.sp)
                                        }
                                        Text(risk.label, color = risk.color, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, lineHeight = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                    Card(colors = CardDefaults.cardColors(containerColor = PaleGreen)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("함께 만드는 Easy Health", color = Green, fontWeight = FontWeight.Bold)
                            Text("개발: Ryoo Jaesang & 최피디", color = Ink, fontSize = 12.sp)
                        }
                    }
                }
                "병원 일정" -> Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("병원 방문 일정", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text("진료 날짜와 시간을 등록하면 해당 시각에 휴대폰 알림을 보내드립니다.", color = Color.Gray, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("예정 일정", "지난 일정").forEach { section ->
                            FilterChip(selected = appointmentSection == section, onClick = { appointmentSection = section }, label = { Text(section) })
                        }
                    }
                    val nowForAppointments = System.currentTimeMillis()
                    val appointmentDateFormat = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA)
                    val visibleDates = appointments.filter { a ->
                        val at = try { appointmentDateFormat.parse("${a.date} ${a.time}")?.time ?: 0L } catch (_: Exception) { 0L }
                        if (appointmentSection == "예정 일정") at >= nowForAppointments && a.status == "예정" else at < nowForAppointments || a.status != "예정"
                    }.map { it.date }.toSet()
                    HospitalCalendar(selectedDate = selectedAppointmentDate, markedDates = visibleDates, onDateSelected = { selectedAppointmentDate = it })
                    Button(onClick = {
                        appointmentInitialDate = selectedAppointmentDate
                        editingAppointment = null
                        showAppointmentDialog = true
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text(if (appointmentSection == "예정 일정") "선택한 날짜에 예약 등록" else "선택한 날짜에 진료 메모 추가")
                    }
                    val visibleAppointments = appointments.filter { a ->
                        val whenAt = try { appointmentDateFormat.parse("${a.date} ${a.time}")?.time ?: 0L } catch (_: Exception) { 0L }
                        if (appointmentSection == "예정 일정") whenAt >= nowForAppointments && a.status == "예정" else whenAt < nowForAppointments || a.status != "예정"
                    }.sortedBy { it.date + " " + it.time }
                    if (visibleAppointments.isEmpty()) Text(if (appointmentSection == "예정 일정") "예정된 병원 일정이 없습니다." else "지난 병원 일정이 없습니다.", color = Color.Gray)
                    else visibleAppointments.forEach { a ->
                        Card(modifier = Modifier.fillMaxWidth().combinedClickable(
                            onClick = { editingAppointment = a; showAppointmentDialog = true },
                            onLongClick = { appointmentDeleteTarget = a }
                        ), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(a.hospital, color = Ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                    Text(a.status, color = if (a.status == "예정") Green else Color.Gray, fontSize = 12.sp)
                                }
                                Text("${a.date} ${a.time}", color = Green, fontWeight = FontWeight.Bold)
                                if (a.memo.isNotBlank()) Text(a.memo, color = Color.Gray, fontSize = 12.sp)
                            }
                        }
                    }
                    Text("짧게 누르면 수정, 길게 누르면 삭제 확인창이 열립니다.", color = Color.Gray, fontSize = 11.sp)

                }
                "리포트" -> Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("건강 리포트", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text("대상자: ${selectedProfile.name} · ${selectedProfile.sex.ifBlank { "성별 미입력" }} · ${selectedProfile.age?.let { "${it}세" } ?: "나이 미입력"} · ${selectedProfile.heightCm?.let { "${it}cm" } ?: "키 미입력"}", color = Color.Gray, fontSize = 11.sp)
                    val reportVitalTypes = listOf("혈당", "혈압", "체온", "체중", "체지방률", "골격근량", "BMI", "호흡수", "산소포화도", "심전도", "케톤", "식사", "운동", "수면", "특이사항", "맥박").filter { reportEnabledTypes.contains(it) }
                    if (reportVitalTypes.isNotEmpty()) {
                    }
                    ScrollableTabRow(selectedTabIndex = listOf("1주일", "1개월", "3개월", "전체").indexOf(reportPeriod).coerceAtLeast(0), containerColor = Color.Transparent, edgePadding = 0.dp) {
                        listOf("1주일", "1개월", "3개월", "전체").forEach { label ->
                            Tab(selected = reportPeriod == label, onClick = {
                                reportPeriod = label
                                prefs.edit().putString("last_report_period", label).apply()
                            }, text = { Text(label) })
                        }
                    }
                    Text(when (reportPeriod) {
                        "1주일" -> "현재부터 1주일 전까지"
                        "1개월" -> "현재부터 1개월 전까지"
                        "3개월" -> "현재부터 3개월 전까지"
                        else -> "전체 기록 기준"
                    }, color = Color.Gray, fontSize = 12.sp)
                    if (reportEnabledTypes.contains("혈당")) {
                        Card(colors = CardDefaults.cardColors(containerColor = PaleGreen), shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("당화혈색소 앱 추정치 · ${reportPeriod}", color = Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(
                                    reportA1cEstimate?.let { String.format(Locale.KOREA, "%.1f%%", it.estimatedA1cPercent) } ?: "측정 데이터 부족",
                                    color = reportA1cEstimate?.let { riskFor("당화혈색소 추정치", it.estimatedA1cPercent.toString(), selectedProfile).color } ?: Color.Gray,
                                    fontSize = 24.sp, fontWeight = FontWeight.Bold
                                )
                                Text(
                                    reportA1cEstimate?.let {
                                        "시간 가중 평균혈당 ${String.format(Locale.KOREA, "%.1f", it.averageMgDl)} mg/dL · 측정 ${it.measurementCount}회 · 유효 구간 ${String.format(Locale.KOREA, "%.1f", it.coveredHours)}시간 · 기간 ${SimpleDateFormat("MM.dd", Locale.KOREA).format(Date(it.firstTimestamp))}~${SimpleDateFormat("MM.dd", Locale.KOREA).format(Date(it.lastTimestamp))}"
                                    } ?: "선택한 기간의 측정 횟수, 측정 기간 또는 유효 시간 구간이 부족합니다.",
                                    color = Color.Gray, fontSize = 11.sp
                                )
                                Text("실제 검사실 당화혈색소가 아닌 참고용 추정치입니다.", color = Color.Gray, fontSize = 10.sp)
                            }
                        }
                    }
                    if (reportVitalTypes.isEmpty()) {
                        Text("등록된 기기의 측정 기록이 쌓이면 여기에 표시됩니다.", color = Color.Gray)
                    } else {
                        // 바이탈별로 그래프와 요약만 표시합니다. 상세 측정 목록은 첫 화면과 중복되므로 제외합니다.
                        reportVitalTypes.forEach { vital ->
                            val vitalRows = periodRecords.filter { it.type == vital }.sortedBy { it.timestamp }
                            val graphRows = vitalRows.mapNotNull { row ->
                                val number = if (vital == "혈압") row.value.substringBefore("/").toDoubleOrNull() else row.value.toDoubleOrNull()
                                number?.let { row to it }
                            }
                            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
                                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(vital + " 측정 추이 · 총 " + vitalRows.size + "회", color = Ink, fontWeight = FontWeight.SemiBold)
                                    if (graphRows.isEmpty()) {
                                        Text("그래프로 표시할 숫자 측정값이 없습니다.", color = Color.Gray, fontSize = 12.sp)
                                    } else {
                                        Text("Y축 · 측정값 (" + (units[vital]?.ifBlank { "단위 없음" } ?: "측정값") + ")     X축 · 측정 날짜", color = Color.Gray, fontSize = 10.sp)
                                        val graphCanvasWidth = when (reportPeriod) {
                                            "전체" -> maxOf(360.dp, (graphRows.size * 2.5f).dp)
                                            "3개월" -> maxOf(360.dp, (graphRows.size * 5).dp)
                                            "1개월" -> maxOf(420.dp, (graphRows.size * 8).dp)
                                            else -> 360.dp
                                        }
                                        Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                                        Canvas(Modifier.width(graphCanvasWidth).height(170.dp)) {
                                            val values = graphRows.map { it.second }
                                            val rawMin = values.minOrNull() ?: 0.0
                                            val rawMax = values.maxOrNull() ?: 1.0
                                            val spread = (rawMax - rawMin).takeIf { it > 0.0 } ?: (kotlin.math.abs(rawMax) * 0.2).coerceAtLeast(1.0)
                                            val minV = if (vital == "혈당") 50.0 else rawMin - spread * 0.12
                                            val maxV = if (vital == "혈당") 300.0 else rawMax + spread * 0.12
                                            val left = 42.dp.toPx()
                                            val right = size.width - 8.dp.toPx()
                                            val top = 8.dp.toPx()
                                            val bottom = size.height - 27.dp.toPx()
                                            val graphStart = if (reportPeriod == "전체") graphRows.first().first.timestamp else reportStart
                                            val graphEnd = now.coerceAtLeast(graphStart + 1L)
                                            fun xFor(index: Int): Float {
                                                val timestamp = graphRows[index].first.timestamp
                                                val fraction = ((timestamp - graphStart).toDouble() / (graphEnd - graphStart).toDouble()).toFloat().coerceIn(0f, 1f)
                                                return left + fraction * (right - left)
                                            }
                                            fun yFor(value: Double) = bottom - ((value - minV) / (maxV - minV) * (bottom - top)).toFloat()
                                                                                        normalRangeForGraph(vital)?.let { range ->
                                                val upper = yFor(range.second.coerceIn(minV, maxV))
                                                val lower = yFor(range.first.coerceIn(minV, maxV))
                                                if (upper < lower) drawRect(Color(0xFF16834A).copy(alpha = 0.075f), Offset(left, upper), androidx.compose.ui.geometry.Size(right - left, lower - upper))
                                            }
                                            drawLine(Color.DarkGray, Offset(left, top), Offset(left, bottom), strokeWidth = 1.5.dp.toPx())
                                            drawLine(Color.DarkGray, Offset(left, bottom), Offset(right, bottom), strokeWidth = 1.5.dp.toPx())
                                            (0..4).forEach { tickIndex ->
                                                val tick = minV + (maxV - minV) * tickIndex / 4.0
                                                val y = yFor(tick)
                                                drawLine(Color.LightGray.copy(alpha = 0.7f), Offset(left, y), Offset(right, y), strokeWidth = 1.dp.toPx())
                                                drawContext.canvas.nativeCanvas.drawText(String.format(Locale.KOREA, "%.1f", tick), 0f, y + 4.dp.toPx(), android.graphics.Paint().apply { color = android.graphics.Color.DKGRAY; textSize = 9.dp.toPx() })
                                            }
                                            val points = graphRows.mapIndexed { index, pair -> Offset(xFor(index), yFor(pair.second.coerceIn(minV, maxV))) }
                                            for (i in 0 until points.lastIndex) drawLine(Color.Gray.copy(alpha = 0.65f), points[i], points[i + 1], strokeWidth = 1.5.dp.toPx())
                                            points.forEachIndexed { index, point -> drawCircle(riskFor(vital, graphRows[index].first.value, selectedProfile).color, radius = if (reportPeriod == "전체" || reportPeriod == "3개월") 2.3.dp.toPx() else 3.dp.toPx(), center = point) }
                                            val datePaint = android.graphics.Paint().apply { color = android.graphics.Color.DKGRAY; textSize = 9.dp.toPx(); textAlign = android.graphics.Paint.Align.CENTER }
                                            listOf(0, graphRows.size / 2, graphRows.lastIndex).distinct().forEach { index ->
                                                val date = SimpleDateFormat("MM.dd", Locale.KOREA).format(Date(graphRows[index].first.timestamp))
                                                drawContext.canvas.nativeCanvas.drawText(date, xFor(index), size.height - 5.dp.toPx(), datePaint)
                                            }
                                        }
                                        }
                                    }
                                }
                            }
                            val numericValues = if (vital == "혈당") glucoseMeasurementGroups(vitalRows).map { it.average } else vitalRows.mapNotNull { it.value.toDoubleOrNull() }
                            val summaryCount = if (vital == "혈당") glucoseMeasurementGroups(vitalRows).size else vitalRows.size
                            Card(colors = CardDefaults.cardColors(containerColor = PaleGreen), shape = RoundedCornerShape(18.dp)) {
                                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text("${vital} 요약", color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                    if (vitalRows.isEmpty()) {
                                        Text("선택한 기간에 기록이 없습니다.", color = Color.Gray)
                                    } else if (vital == "혈압") {
                                        val bp = vitalRows.mapNotNull { row ->
                                            val parts = row.value.split("/")
                                            if (parts.size >= 3) parts.take(3).mapNotNull { it.toDoubleOrNull() }.takeIf { it.size == 3 } else null
                                        }
                                        if (bp.isNotEmpty()) {
                                            Text("평균 ${bp.map { it[0] }.average().toInt()}/${bp.map { it[1] }.average().toInt()} mmHg", color = Green, fontWeight = FontWeight.Bold, fontSize = 23.sp)
                                            Text("평균 맥박 ${bp.map { it[2] }.average().toInt()}회/분 · 총 ${vitalRows.size}회", color = Ink, fontSize = 12.sp)
                                        } else Text("총 ${vitalRows.size}회 측정", color = Green, fontWeight = FontWeight.Bold)
                                    } else if (vital == "혈당" && numericValues.isNotEmpty()) {
                                        val overallAverage = numericValues.average()
                                        Text("전체 평균 ${String.format(Locale.KOREA, "%.1f", overallAverage)} mg/dL", color = riskFor(vital, overallAverage.toString(), selectedProfile).color, fontWeight = FontWeight.Bold, fontSize = 23.sp)
                                        Text("기간별 평균 · 같은 시간대의 연속 측정은 한 번으로 묶어 계산", color = Ink, fontSize = 11.sp)
                                        val glucoseChunkRanges = mutableListOf<Pair<Long, Long>>()
                                        if (reportPeriod == "1개월") {
                                            for (index in 0 until 4) {
                                                val end = now - index * 7L * 86400000L
                                                glucoseChunkRanges.add((end - 7L * 86400000L) to end)
                                            }
                                        } else if (reportPeriod == "3개월") {
                                            val endCalendar = Calendar.getInstance().apply { timeInMillis = now }
                                            for (index in 0 until 3) {
                                                val end = endCalendar.timeInMillis
                                                val startCalendar = (endCalendar.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
                                                glucoseChunkRanges.add(startCalendar.timeInMillis to end)
                                                endCalendar.add(Calendar.MONTH, -1)
                                            }
                                        } else if (reportPeriod == "전체") {
                                            val firstTimestamp = vitalRows.minOf { it.timestamp }
                                            val endCalendar = Calendar.getInstance().apply { timeInMillis = now }
                                            while (true) {
                                                val end = endCalendar.timeInMillis
                                                val startCalendar = (endCalendar.clone() as Calendar).apply { add(Calendar.MONTH, -3) }
                                                val start = startCalendar.timeInMillis
                                                glucoseChunkRanges.add(start to end)
                                                if (start <= firstTimestamp) break
                                                endCalendar.add(Calendar.MONTH, -3)
                                            }
                                        }
                                        if (glucoseChunkRanges.isNotEmpty()) {
                                            glucoseChunkRanges.asReversed().forEach { (rangeStart, rangeEnd) ->
                                                val chunkGroups = glucoseMeasurementGroups(vitalRows.filter { it.timestamp >= rangeStart && it.timestamp < rangeEnd })
                                                val chunkValues = chunkGroups.map { it.average }
                                                val startLabel = SimpleDateFormat("MM.dd", Locale.KOREA).format(Date(rangeStart))
                                                val endLabel = SimpleDateFormat("MM.dd", Locale.KOREA).format(Date(rangeEnd))
                                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                    Text("$startLabel ~ $endLabel", color = Ink, fontSize = 12.sp)
                                                    Text(if (chunkValues.isNotEmpty()) "평균 ${String.format(Locale.KOREA, "%.1f", chunkValues.average())} mg/dL · ${chunkGroups.size}회" else "기록 없음", color = if (chunkValues.isNotEmpty()) Green else Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                                }
                                            }
                                        }
                                        Text("최고 ${numericValues.maxOrNull()?.toInt()} · 최저 ${numericValues.minOrNull()?.toInt()} · 총 ${summaryCount}회", color = Ink, fontSize = 12.sp)
                                    } else if (numericValues.isNotEmpty()) {
                                        Text("평균 ${String.format(Locale.KOREA, "%.1f", numericValues.average())} ${units[vital] ?: ""}", color = riskFor(vital, numericValues.average().toString(), selectedProfile).color, fontWeight = FontWeight.Bold, fontSize = 23.sp)
                                        Text("평균 기준: ${riskFor(vital, numericValues.average().toString(), selectedProfile).label}", color = riskFor(vital, numericValues.average().toString(), selectedProfile).color, fontSize = 11.sp)
                                        Text("최고 ${numericValues.maxOrNull()} · 최저 ${numericValues.minOrNull()} · 총 ${summaryCount}회", color = Ink, fontSize = 12.sp)
                                    } else {
                                        Text("총 ${vitalRows.size}건", color = Green, fontWeight = FontWeight.Bold, fontSize = 23.sp)
                                    }
                                    vitalRows.lastOrNull()?.let { Text("최근 측정: ${dateFormat.format(Date(it.timestamp))}", color = Color.Gray, fontSize = 11.sp) }
                                }
                            }
                        }
                    }
                    
                }
                else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("기기 설정", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Button(onClick = { showRegisterDeviceDialog = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("기기 등록")
                    }
                    if (registeredDevices.isEmpty()) {
                        Text("등록된 기기가 없습니다. 기기 등록 버튼을 눌러 추가해 주세요.", color = Color.Gray, modifier = Modifier.padding(vertical = 18.dp))
                    } else {
                        devices.filter { it.first in registeredDevices }.forEach { device ->
                            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.combinedClickable(onClick = {
                                    if (enabledDevices.contains(device.first)) enabledDevices.remove(device.first) else enabledDevices.add(device.first)
                                    prefs.edit().putString("enabled_devices", JSONArray(enabledDevices).toString()).apply()
                                }, onLongClick = { deviceDeleteTarget = device.first })) {
                                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = enabledDevices.contains(device.first), onCheckedChange = { checked ->
                                        if (checked && !enabledDevices.contains(device.first)) enabledDevices.add(device.first)
                                        if (!checked) enabledDevices.remove(device.first)
                                        prefs.edit().putString("enabled_devices", JSONArray(enabledDevices).toString()).apply()
                                    })
                                    Column {
                                        Text(device.first, color = Ink, fontWeight = FontWeight.SemiBold)
                                        Text(device.second.joinToString(" · "), color = Color.Gray, fontSize = 12.sp)
                                        TextButton(onClick = { deviceHelpTarget = device.first }, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)) {
                                            Text("연결 방법 보기", color = Green, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                        Text("짧게 누르면 초기 화면에 표시할 항목을 바꾸고, 길게 누르면 등록 삭제 확인창이 열립니다.", color = Color.Gray, fontSize = 11.sp)
                    }
                    Text("일반 기기는 측정기를 페어링 모드로 설정한 뒤 휴대폰 블루투스에서 검색·등록해 주세요. Contour Plus ONE과 CareSens Dual은 각 기기별 연결 방법을 확인할 수 있습니다.", color = Color.Gray, fontSize = 12.sp)
                }
            }
        }
    }


    if (deviceHelpTarget != null) {
        val helpName = deviceHelpTarget!!
        val helpText = when (helpName) {
            "Contour Plus ONE" -> "1. 측정기 전원을 끕니다.\n2. OK 버튼을 길게 눌러 블루투스 표시가 깜박이도록 합니다.\n3. Easy Health에서 기기 등록 → 블루투스 기기 찾기를 누릅니다.\n4. 검색된 Contour Plus ONE을 선택해 연결합니다."
            "CareSens Dual" -> "1. 측정기에서 S 버튼을 약 3초 누릅니다.\n2. YES → BT → PAIR 순서로 선택합니다.\n3. Easy Health에서 기기 등록 → 블루투스 기기 찾기를 누릅니다.\n4. 검색된 CareSens 기기를 선택하고 측정기 화면의 PIN 안내에 따라 연결합니다."
            else -> "1. 측정기를 켜고 블루투스 페어링 모드로 설정합니다.\n2. 휴대폰 설정의 블루투스에서 기기를 검색해 등록합니다.\n3. Easy Health의 기기 등록에서 해당 측정 항목을 활성화합니다.\n4. 자동 가져오기는 기기별 데이터 전송 방식이 지원되는 경우에 사용할 수 있습니다."
        }
        AlertDialog(onDismissRequest = { deviceHelpTarget = null }, title = { Text("$helpName 연결 방법") },
            text = { Text(helpText, color = Ink, fontSize = 13.sp, lineHeight = 20.sp) },
            confirmButton = { TextButton(onClick = { deviceHelpTarget = null }) { Text("확인") } })
    }
    if (showRegisterDeviceDialog) {
        AlertDialog(onDismissRequest = { showRegisterDeviceDialog = false }, title = { Text("기기 등록") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Contour Plus ONE: 전원을 끄고 OK 버튼을 파란 불이 깜박일 때까지 길게 누르세요. CareSens Dual: S 버튼을 3초 누르고 YES → BT → PAIR를 선택하세요. 측정기 화면의 PIN이 휴대폰에 표시되면 확인해 주세요.", color = Color.Gray, fontSize = 12.sp)
                Button(onClick = { showBluetoothSearch = true; requestBluetoothScan() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Bluetooth, null); Spacer(Modifier.width(6.dp)); Text("블루투스 기기 찾기")
                }
            } },
            confirmButton = { TextButton(onClick = { showRegisterDeviceDialog = false }) { Text("닫기") } })
    }
    if (showBluetoothSearch) {
        AlertDialog(onDismissRequest = { bluetoothClient.stopScan(); showBluetoothSearch = false }, title = { Text("블루투스 기기 찾기") },
            text = {
                Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(bluetoothStatus, color = Color.Gray, fontSize = 12.sp)
                    Button(onClick = { bluetoothCandidates = emptyList(); requestBluetoothScan() }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Bluetooth, null); Spacer(Modifier.width(6.dp)); Text("다시 검색")
                    }
                    bluetoothCandidates.forEach { candidate ->
                        Card(Modifier.fillMaxWidth().clickable { bluetoothStatus = "${candidate.name}에 연결을 요청했습니다."; bluetoothClient.connect(candidate, bluetoothListener) }) {
                            Column(Modifier.padding(12.dp)) {
                                Text(candidate.name, color = Ink, fontWeight = FontWeight.SemiBold)
                                Text(candidate.address, color = Color.Gray, fontSize = 11.sp)
                                Text("눌러서 연결 및 등록", color = Green, fontSize = 11.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { bluetoothClient.stopScan(); showBluetoothSearch = false }) { Text("닫기") } })
    }
    deviceDeleteTarget?.let { target ->
        AlertDialog(onDismissRequest = { deviceDeleteTarget = null },
            title = { Text("기기 삭제") },
            text = { Text(target + " 기기를 삭제하시겠습니까?") },
            confirmButton = { TextButton(onClick = {
                registeredDevices.remove(target); enabledDevices.remove(target)
                prefs.edit().putString("registered_devices", JSONArray(registeredDevices).toString())
                    .putString("enabled_devices", JSONArray(enabledDevices).toString()).apply()
                deviceDeleteTarget = null
            }) { Text("삭제", color = Color.Red) } },
            dismissButton = { TextButton(onClick = { deviceDeleteTarget = null }) { Text("취소") } })
    }

    if (showOwnerSetup) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Easy Health 사용자 이름") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("휴대폰은 소유자 이름을 앱에 자동으로 알려주지 않습니다. 기록에 표시할 이름을 입력해 주세요.")
                    OutlinedTextField(
                        value = ownerNameInput,
                        onValueChange = { ownerNameInput = it },
                        label = { Text("내 이름") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = ownerNameInput.trim()
                    if (name.isNotBlank()) {
                        val oldName = prefs.getString("owner_name", null)
                        if (oldName == null && people.contains("본인")) {
                            val index = people.indexOf("본인")
                            if (index >= 0) people[index] = name
                            records.indices.forEach { i ->
                                if (records[i].person == "본인") records[i] = records[i].copy(person = name)
                            }
                            persistRecords(context, records)
                            prefs.edit().putString("people", JSONArray(people).toString()).apply()
                        }
                        prefs.edit().putString("owner_name", name).putString("selected_person", name).apply()
                        if (!people.contains(name)) people.add(name)
                        selectedPerson = name
                        showOwnerSetup = false
                    }
                }) { Text("저장") }
            }
        )
    }

    if (showAppInfo) {
        Dialog(onDismissRequest = { showAppInfo = false }) {
            Surface(shape = RoundedCornerShape(22.dp), color = Color.White) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 26.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Image(painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_launcher), contentDescription = "Easy Health 앱 아이콘", modifier = Modifier.size(76.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("Easy Health", color = Ink, fontWeight = FontWeight.Bold, fontSize = 25.sp)
                        Text("버전 ${BuildConfig.VERSION_NAME}", color = Color.Gray, fontSize = 12.sp)
                        Text("Ryoo Jaesang & 최피디", color = Ink, fontSize = 14.sp)
                    }
                }
            }
        }
    }
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text("Easy Health 사용 도움말", color = Ink, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("1. 사용자 정보와 측정 대상자", fontWeight = FontWeight.Bold, color = Ink)
                    Text("처음 실행할 때 이름, 성별, 나이, 키를 입력합니다. 가족 등 다른 사람의 기록은 상단의 대상자 이름을 눌러 추가하거나 전환할 수 있습니다.")
                    Text("2. 건강 기록 입력", fontWeight = FontWeight.Bold, color = Ink)
                    Text("기록 화면에서 혈당, 혈압, 체온, 체중과 기타 건강 정보를 입력하고 저장할 수 있습니다. 저장한 기록은 수정하거나 삭제할 수 있습니다.")
                    Text("3. 블루투스 측정기기", fontWeight = FontWeight.Bold, color = Ink)
                    Text("기기 설정에서 측정기기를 등록하고 연결 상태를 확인합니다. 블루투스 자동 가져오기를 누른 뒤 기기를 선택하면 측정값을 가져올 수 있습니다. 기기별 연결 방법도 확인할 수 있습니다.")
                    Text("4. 건강 리포트", fontWeight = FontWeight.Bold, color = Ink)
                    Text("리포트 화면에서 1주일, 1개월, 3개월 또는 전체 기간의 기록을 확인할 수 있습니다. 필요한 건강 항목을 선택해 평균, 최고·최저값과 측정 추이를 살펴보세요.")
                    Text("5. 병원 일정", fontWeight = FontWeight.Bold, color = Ink)
                    Text("병원 일정에서 방문 날짜와 시간을 등록하고 메모를 남길 수 있습니다.")
                    Text("참고: 화면의 색상과 기준은 일반적인 참고 정보이며 의료진의 진단을 대신하지 않습니다. 측정 조건과 개인별 목표에 따라 해석이 달라질 수 있습니다.", color = Color.Gray, fontSize = 12.sp)
                }
            },
            confirmButton = { TextButton(onClick = { showHelpDialog = false }) { Text("닫기") } }
        )
    }
    if (showAppointmentDialog) {
        AppointmentDialog(existing = editingAppointment, initialDate = appointmentInitialDate, initialStatus = if (appointmentSection == "지난 일정") "진료 완료" else "예정", onDismiss = { showAppointmentDialog = false; appointmentInitialDate = null },
            onSave = { hospital, date, time, memo, status ->
                val fmt = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA).apply { isLenient = false }
                val parsed = try { fmt.parse("$date $time") } catch (_: Exception) { null }
                if (hospital.isNotBlank() && parsed != null && (editingAppointment != null || appointmentSection == "지난 일정" || parsed.time > System.currentTimeMillis())) {
                    editingAppointment?.let { cancelAppointmentReminder(context, it.id); appointments.removeAll { old -> old.id == it.id } }
                    val saved = HospitalAppointment(editingAppointment?.id ?: (System.currentTimeMillis() % Int.MAX_VALUE).toInt(), hospital, date, time, memo, status)
                    appointments.add(saved); saveAppointments(context, appointments); scheduleAppointmentReminder(context, saved)
                    selectedAppointmentDate = date
                    appointmentInitialDate = null
                    if (android.os.Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                        (context as? Activity)?.requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 701)
                    showAppointmentDialog = false
                }
            },
            onDelete = { a -> appointmentDeleteTarget = a; showAppointmentDialog = false }
        )
    }
    appointmentDeleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { appointmentDeleteTarget = null },
            title = { Text("병원 일정 삭제") },
            text = { Text("${target.hospital} 일정(${target.date} ${target.time})을 삭제하시겠습니까? 삭제한 일정은 복구할 수 없습니다.") },
            confirmButton = {
                TextButton(onClick = {
                    cancelAppointmentReminder(context, target.id)
                    appointments.removeAll { it.id == target.id }
                    saveAppointments(context, appointments)
                    appointmentDeleteTarget = null
                }) { Text("삭제", color = Color.Red) }
            },
            dismissButton = { TextButton(onClick = { appointmentDeleteTarget = null }) { Text("취소") } }
        )
    }
    if (showRecordDialog) {
        RecordDialog(selectedPerson, editing, registeredDevices.toList(), onDismiss = { showRecordDialog = false }, onSave = { type, value, note, source, timestamp ->
            val candidate = VitalRecord(editing?.id ?: UUID.randomUUID().toString(), selectedPerson, type, value, timestamp, source, note, editing?.externalId ?: "")
            val duplicate = records.any { old ->
                old.id != candidate.id && old.person == candidate.person && old.type == candidate.type &&
                    old.timestamp == candidate.timestamp && old.value == candidate.value && old.source == candidate.source
            }
            if (!duplicate) {
                if (editing != null) {
                    val index = records.indexOfFirst { it.id == editing!!.id }
                    if (index >= 0) records[index] = candidate
                } else records.add(candidate)
                persistRecords(context, records)
            }
            showRecordDialog = false
        }, onDelete = { target -> deleteTarget = target; showRecordDialog = false })
    }
    if (deleteTarget != null) {
        AlertDialog(onDismissRequest = { deleteTarget = null }, title = { Text("기록 삭제") },
            text = { Text("선택한 측정 기록을 삭제하시겠습니까?") },
            confirmButton = { TextButton(onClick = {
                records.removeAll { it.id == deleteTarget!!.id }; persistRecords(context, records); deleteTarget = null
            }) { Text("삭제", color = Color.Red) } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("취소") } })
    }
    if (showPeopleDialog) {
        AlertDialog(onDismissRequest = { showPeopleDialog = false }, title = { Text("측정 대상자") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    people.forEach { person ->
                        val profile = personProfiles[person] ?: PersonProfile(person)
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF7FAF8)), shape = RoundedCornerShape(12.dp)) {
                            Column(Modifier.fillMaxWidth().padding(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = selectedPerson == person, onClick = {
                                        selectedPerson = person; prefs.edit().putString("selected_person", person).apply(); showPeopleDialog = false
                                    })
                                    Column(Modifier.weight(1f).clickable {
                                        selectedPerson = person; prefs.edit().putString("selected_person", person).apply(); showPeopleDialog = false
                                    }) {
                                        Text(person, color = Ink, fontWeight = FontWeight.SemiBold)
                                        Text(listOf(profile.sex, profile.age?.let { "${it}세" }, profile.heightCm?.let { "${it}cm" }).filterNotNull().filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "추가 정보 없음" }, color = Color.Gray, fontSize = 11.sp)
                                    }
                                    TextButton(onClick = { personEditing = profile; showPeopleDialog = false }) { Text("수정") }
                                    TextButton(onClick = { personDeleteTarget = person; showPeopleDialog = false }, enabled = people.size > 1) { Text("삭제", color = Color.Red) }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { personEditing = PersonProfile(""); showPeopleDialog = false }) { Text("대상자 추가") } },
            dismissButton = { TextButton(onClick = { showPeopleDialog = false }) { Text("닫기") } })
    }
    personEditing?.let { original ->
        PersonProfileDialog(existing = original, onDismiss = { personEditing = null }, onSave = { updated ->
            val oldName = original.name
            val newName = updated.name.trim()
            if (newName.isNotBlank() && (oldName.isBlank() || oldName == newName || !people.contains(newName))) {
                if (oldName.isNotBlank() && oldName != newName) {
                    val index = people.indexOf(oldName)
                    if (index >= 0) people[index] = newName
                    records.indices.filter { records[it].person == oldName }.forEach { i -> records[i] = records[i].copy(person = newName) }
                    if (selectedPerson == oldName) {
                        selectedPerson = newName
                        prefs.edit().putString("selected_person", newName).apply()
                    }
                    personProfiles.remove(oldName)
                } else if (oldName.isBlank()) {
                    people.add(newName)
                    selectedPerson = newName
                    prefs.edit().putString("selected_person", newName).apply()
                }
                personProfiles[newName] = updated.copy(name = newName)
                prefs.edit().putString("people", JSONArray(people).toString()).apply()
                saveProfiles()
                persistRecords(context, records)
            }
            personEditing = null
        })
    }
    personDeleteTarget?.let { target ->
        AlertDialog(onDismissRequest = { personDeleteTarget = null }, title = { Text("측정 대상자 삭제") },
            text = { Text("${target} 대상자와 해당 대상자의 측정 기록도 함께 삭제하시겠습니까?") },
            confirmButton = { TextButton(onClick = {
                records.removeAll { it.person == target }
                people.remove(target); personProfiles.remove(target)
                if (selectedPerson == target) {
                    selectedPerson = people.first()
                    prefs.edit().putString("selected_person", selectedPerson).apply()
                }
                prefs.edit().putString("people", JSONArray(people).toString()).apply()
                saveProfiles(); persistRecords(context, records)
                personDeleteTarget = null
            }) { Text("삭제", color = Color.Red) } },
            dismissButton = { TextButton(onClick = { personDeleteTarget = null }) { Text("취소") } })
    }
    if (showTransferDialog) {
        AlertDialog(
            onDismissRequest = {
                if (transferSearching) recordTransfer.cancelDiscovery()
                if (transferServerActive || transferMode == "가져오기") recordTransfer.stopServer()
                transferSearching = false
                transferServerActive = false
                transferStartPending = false
                discoverabilityRequestPending = false
                receiverServerStartRequested = false
                showTransferBluetoothSearch = false
                showTransferDialog = false
            },
            title = { Text(if (transferMode == "보내기") "데이터 내보내기" else "데이터 가져오기") },
            text = {
                Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(transferStatus, color = Green, fontSize = 12.sp)
                    if (transferMode == "보내기") {
                        Text("내보낼 사용자")
                        people.forEach { p ->
                            FilterChip(selected = transferPerson == p, onClick = { transferPerson = p }, label = { Text(p) })
                        }
                        Text("내보낼 날짜 기간")
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(onClick = {
                                val c = Calendar.getInstance().apply { timeInMillis = transferFrom }
                                DatePickerDialog(context, { _, y, m, d ->
                                    transferFrom = Calendar.getInstance().apply { set(y, m, d, 0, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
                                }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
                            }) { Text("시작 " + SimpleDateFormat("yyyy.MM.dd", Locale.KOREA).format(Date(transferFrom))) }
                            TextButton(onClick = {
                                val c = Calendar.getInstance().apply { timeInMillis = transferTo }
                                DatePickerDialog(context, { _, y, m, d ->
                                    transferTo = Calendar.getInstance().apply { set(y, m, d, 23, 59, 59); set(Calendar.MILLISECOND, 999) }.timeInMillis
                                }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
                            }) { Text("종료 " + SimpleDateFormat("yyyy.MM.dd", Locale.KOREA).format(Date(transferTo))) }
                        }
                        if (transferProgress > 0f) LinearProgressIndicator(progress = { transferProgress }, modifier = Modifier.fillMaxWidth())
                    } else {
                        Text(
                            if (transferServerActive && transferStatus.contains("수신 대기 중"))
                                "다른 기기에서 이 기기를 검색할 수 있습니다. 보내는 기기에서 내보내기를 진행해 주세요."
                            else if (transferStartPending)
                                "수신 서버를 준비하고 있습니다. 서버가 실제 대기 상태가 될 때까지 이 화면을 유지해 주세요."
                            else if (transferServerActive)
                                "가져오기 준비 중입니다. 이 화면을 유지해 주세요."
                            else "가져오기를 시작하면 다른 기기에서 이 기기를 검색할 수 있도록 허용한 뒤 자동으로 수신 대기합니다.",
                            color = Color.Gray, fontSize = 12.sp
                        )
                        if (transferProgress > 0f) LinearProgressIndicator(progress = { transferProgress }, modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                if (transferMode == "보내기") {
                    TextButton(onClick = {
                        val selected = records.filter { it.person == transferPerson && it.timestamp in transferFrom..transferTo }
                        when {
                            transferFrom > transferTo -> transferStatus = "날짜 기간을 다시 확인해 주세요."
                            selected.isEmpty() -> transferStatus = "선택한 기간에 내보낼 기록이 없습니다."
                            else -> requestTransferPermissions()
                        }
                    }) { Text("기기 검색") }
                } else {
                    TextButton(onClick = {
                        if (transferServerActive || transferStartPending) {
                            recordTransfer.stopServer()
                            transferServerActive = false
                            transferStartPending = false
                            discoverabilityRequestPending = false
                            receiverServerStartRequested = false
                        } else requestTransferPermissions()
                    }) { Text(if (transferServerActive || transferStartPending) "수신 대기 취소" else "가져오기 시작") }
                }
            },
            dismissButton = { TextButton(onClick = {
                if (transferSearching) recordTransfer.cancelDiscovery()
                if (transferServerActive || transferMode == "가져오기") recordTransfer.stopServer()
                transferSearching = false
                transferServerActive = false
                transferStartPending = false
                discoverabilityRequestPending = false
                receiverServerStartRequested = false
                showTransferDialog = false
            }) { Text("닫기") } }
        )
    }
    transferOffer?.let { offer ->
        // 이전 버전 호환 상태만 정리합니다. 새 수신 흐름은 별도 승인 없이 자동 수신합니다.
        LaunchedEffect(offer) { transferOffer = null }
    }
    if (showTransferBluetoothSearch) {
        AlertDialog(
            onDismissRequest = {
                if (transferSearching) recordTransfer.cancelDiscovery()
                transferSearching = false
                showTransferBluetoothSearch = false
            },
            title = { Text("블루투스 기기 검색") },
            text = {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(transferStatus, color = Green, fontSize = 12.sp)
                    if (transferSearching) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    if (transferPeers.isEmpty()) {
                        Text(
                            if (transferSearching) "주변 기기를 찾고 있습니다. 받는 기기에서 가져오기를 시작해 주세요."
                            else "검색된 기기가 없습니다. 받는 기기의 블루투스와 수신 대기 상태를 확인한 뒤 다시 검색해 주세요.",
                            color = Color.Gray, fontSize = 12.sp
                        )
                    } else {
                        transferPeers.forEach { peer ->
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    transferTarget = peer
                                    transferSearching = false
                                    recordTransfer.cancelDiscovery()
                                    showTransferBluetoothSearch = false
                                    val selected = records.filter { it.person == transferPerson && it.timestamp in transferFrom..transferTo }
                                    if (selected.isEmpty()) {
                                        transferStatus = "선택한 기간에 내보낼 기록이 없습니다."
                                    } else {
                                        transferProgress = 0f
                                        transferStatus = peer.name + " 기기에 연결하고 데이터를 전송합니다."
                                        recordTransfer.send(peer, transferPerson, transferFrom, transferTo, encodeRecords(selected), selected.size)
                                    }
                                }
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(peer.name, color = Ink, fontWeight = FontWeight.SemiBold)
                                    Text(peer.address, color = Color.Gray, fontSize = 11.sp)
                                    Text("선택하면 연결 후 자동 전송됩니다.", color = Green, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    transferPeers = emptyList()
                    transferSearching = true
                    transferStatus = "주변 블루투스 기기를 다시 검색하고 있습니다."
                    recordTransfer.startDiscovery(transferListener)
                }) { Text("다시 검색") }
            },
            dismissButton = {
                TextButton(onClick = {
                    if (transferSearching) recordTransfer.cancelDiscovery()
                    transferSearching = false
                    showTransferBluetoothSearch = false
                }) { Text("취소") }
            }
        )
    }
    if (showDeviceInfo) {
        val savedMeters = JSONArray(prefs.getString("bluetooth_devices", "[]") ?: "[]")
        AlertDialog(onDismissRequest = { showDeviceInfo = false }, title = { Text("블루투스 자동 가져오기") },
            text = {
                Column(Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(bluetoothStatus, color = Color.Gray, fontSize = 12.sp)
                    if (savedMeters.length() == 0) Text("먼저 기기 설정에서 측정기를 등록해 주세요.")
                    for (i in 0 until savedMeters.length()) {
                        val item = savedMeters.optJSONObject(i) ?: continue
                        Card(Modifier.fillMaxWidth().clickable {
                            bluetoothStatus = "${item.optString("model")} 연결 중입니다."
                            bluetoothClient.connectAddress(item.optString("name"), item.optString("address"), bluetoothListener)
                        }) {
                            Column(Modifier.padding(10.dp)) {
                                Text(item.optString("model"), color = Ink, fontWeight = FontWeight.SemiBold)
                                Text(item.optString("name"), color = Color.Gray, fontSize = 11.sp)
                                Text("눌러서 측정 기록 가져오기", color = Green, fontSize = 11.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDeviceInfo = false }) { Text("닫기") } })
    }
}


@Composable
private fun ProfileTimeRow(label: String, value: String, onChange: (String) -> Unit) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Ink, fontSize = 12.sp, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = {
            val parts = value.split(":")
            val hour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 7
            val minute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
            TimePickerDialog(context, { _, h, m -> onChange(String.format(Locale.KOREA, "%02d:%02d", h, m)) }, hour, minute, true).show()
        }) { Text(value.ifBlank { "시간 선택" }, fontSize = 12.sp) }
    }
}

@Composable
private fun PersonProfileDialog(existing: PersonProfile, onDismiss: () -> Unit, onSave: (PersonProfile) -> Unit) {
    var name by remember(existing) { mutableStateOf(existing.name) }
    var sex by remember(existing) { mutableStateOf(existing.sex) }
    var age by remember(existing) { mutableStateOf(existing.age?.toString() ?: "") }
    var height by remember(existing) { mutableStateOf(existing.heightCm?.toString() ?: "") }
    var wakeTime by remember(existing) { mutableStateOf(existing.wakeTime) }
    var breakfastTime by remember(existing) { mutableStateOf(existing.breakfastTime) }
    var lunchTime by remember(existing) { mutableStateOf(existing.lunchTime) }
    var dinnerTime by remember(existing) { mutableStateOf(existing.dinnerTime) }
    var bedtime by remember(existing) { mutableStateOf(existing.bedtime) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing.name.isBlank()) "측정 대상자 추가" else "측정 대상자 정보 수정") },
        text = {
            Column(Modifier.heightIn(max = 600.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("이름") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("성별", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("남성", "여성", "선택 안 함").forEach { option ->
                        FilterChip(selected = sex == option, onClick = { sex = if (option == "선택 안 함") "" else option }, label = { Text(option, fontSize = 12.sp) })
                    }
                }
                OutlinedTextField(value = age, onValueChange = { age = it.filter(Char::isDigit).take(3) }, label = { Text("나이 (세)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = height, onValueChange = { height = it.filter { ch -> ch.isDigit() || ch == '.' }.take(6) }, label = { Text("키 (cm)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("생활 시간", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                ProfileTimeRow("기상 시간", wakeTime) { wakeTime = it }
                ProfileTimeRow("아침 식사 시간", breakfastTime) { breakfastTime = it }
                ProfileTimeRow("점심 식사 시간", lunchTime) { lunchTime = it }
                ProfileTimeRow("저녁 식사 시간", dinnerTime) { dinnerTime = it }
                ProfileTimeRow("취침 시간", bedtime) { bedtime = it }
                Text("입력한 식사 기준시간보다 이르면 식전, 지나면 식후로 표시합니다. 기상 후 아침 식사 전 구간은 공복으로, 취침시간 전후 1시간은 취침전으로 표시합니다.", color = Color.Gray, fontSize = 10.sp)
                Text("키를 입력하면 체중 기록에서 BMI를 계산해 위험 참고 색상에 반영합니다. 성별과 나이는 개인별 리포트 해석에 활용합니다.", color = Color.Gray, fontSize = 11.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val a = age.toIntOrNull()
                val h = height.toDoubleOrNull()
                if (name.trim().isNotBlank() && (age.isBlank() || a in 0..120) && (height.isBlank() || (h != null && h in 30.0..250.0))) {
                    onSave(PersonProfile(name.trim(), sex, a, h, wakeTime, breakfastTime, lunchTime, dinnerTime, bedtime))
                }
            }) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

@Composable
private fun RecordDialog(
    person: String,
    existing: VitalRecord?,
    registeredDevices: List<String>,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, Long) -> Unit,
    onDelete: (VitalRecord) -> Unit
) {
    val context = LocalContext.current
    val availableDevices = registeredDevices.mapNotNull { name ->
        devices.firstOrNull { it.first == name }
    }
    var selectedDevice by remember(existing, registeredDevices) {
        mutableStateOf(
            availableDevices.firstOrNull { it.first == existing?.source }?.first
                ?: availableDevices.firstOrNull { device -> existing != null && existing.type in device.second }?.first
                ?: availableDevices.firstOrNull()?.first.orEmpty()
        )
    }
    val availableTypes = availableDevices.firstOrNull { it.first == selectedDevice }?.second ?: emptyList()
    var type by remember(existing, selectedDevice) {
        mutableStateOf(existing?.type?.takeIf { it in availableTypes } ?: availableTypes.firstOrNull().orEmpty())
    }
    var value by remember(existing) { mutableStateOf(existing?.value ?: "") }
    val existingBp = remember(existing) { existing?.value?.split("/") ?: emptyList() }
    var systolic by remember(existing) { mutableStateOf(existingBp.getOrNull(0) ?: "") }
    var diastolic by remember(existing) { mutableStateOf(existingBp.getOrNull(1) ?: "") }
    var pulse by remember(existing) { mutableStateOf(existingBp.getOrNull(2) ?: "") }
    var note by remember(existing) { mutableStateOf(existing?.note ?: "") }
    var measuredDate by remember(existing) { mutableStateOf(SimpleDateFormat("yyyy.MM.dd", Locale.KOREA).format(Date(existing?.timestamp ?: System.currentTimeMillis()))) }
    var measuredTime by remember(existing) { mutableStateOf(SimpleDateFormat("HH:mm", Locale.KOREA).format(Date(existing?.timestamp ?: System.currentTimeMillis()))) }
    fun showDatePicker() {
        val calendar = java.util.Calendar.getInstance()
        try { SimpleDateFormat("yyyy.MM.dd", Locale.KOREA).parse(measuredDate)?.let { calendar.time = it } } catch (_: Exception) { }
        DatePickerDialog(context, { _, year, month, day -> measuredDate = String.format(Locale.KOREA, "%04d.%02d.%02d", year, month + 1, day) }, calendar.get(java.util.Calendar.YEAR), calendar.get(java.util.Calendar.MONTH), calendar.get(java.util.Calendar.DAY_OF_MONTH)).show()
    }
    fun showTimePicker() {
        val parts = measuredTime.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 0
        val minute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
        TimePickerDialog(context, { _, h, m -> measuredTime = String.format(Locale.KOREA, "%02d:%02d", h, m) }, hour, minute, true).show()
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (existing == null) "측정 기록 추가" else "측정 기록 수정") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState())) {
                if (availableDevices.isEmpty()) {
                    Text("먼저 기기 설정에서 측정기를 등록해 주세요.", color = Color.Gray)
                } else {
                    Text("등록된 측정기", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    ScrollableTabRow(selectedTabIndex = availableDevices.indexOfFirst { it.first == selectedDevice }.coerceAtLeast(0), edgePadding = 0.dp) {
                        availableDevices.forEach { device ->
                            Tab(selected = selectedDevice == device.first, onClick = {
                                selectedDevice = device.first
                                type = device.second.firstOrNull().orEmpty()
                                value = ""
                                systolic = ""; diastolic = ""; pulse = ""
                            }, text = { Text(device.first, fontSize = 11.sp) })
                        }
                    }
                    Text("측정 항목", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        availableTypes.forEach { item ->
                            FilterChip(selected = type == item, onClick = { type = item; value = "" }, label = { Text(item, fontSize = 12.sp) })
                        }
                    }
                    if (type == "혈당") {
                        Text("당화혈색소 추정치는 최근 혈당 기록을 바탕으로 자동 계산됩니다.", color = Color.Gray, fontSize = 11.sp)
                    }
                    if (type == "혈압") {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(value = systolic, onValueChange = { systolic = it }, label = { Text("수축기 (mmHg)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = diastolic, onValueChange = { diastolic = it }, label = { Text("이완기 (mmHg)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f))
                        }
                        OutlinedTextField(value = pulse, onValueChange = { pulse = it }, label = { Text("맥박 (회/분)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                    } else {
                        OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text("측정값 ${units[type]?.let { "($it)" } ?: ""}") }, keyboardOptions = KeyboardOptions(keyboardType = if (type in listOf("식사", "운동", "수면", "특이사항", "심전도")) KeyboardType.Text else KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = measuredDate, onValueChange = { measuredDate = it }, label = { Text("측정 날짜") }, singleLine = true, modifier = Modifier.weight(1f))
                        IconButton(onClick = { showDatePicker() }) { Icon(Icons.Default.DateRange, contentDescription = "달력에서 날짜 선택", tint = Green) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = measuredTime, onValueChange = { measuredTime = it }, label = { Text("측정 시간") }, singleLine = true, modifier = Modifier.weight(1f))
                        IconButton(onClick = { showTimePicker() }) { Icon(Icons.Default.AccessTime, contentDescription = "시계에서 시간 선택", tint = Green) }
                    }
                    OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("메모 (선택)") }, modifier = Modifier.fillMaxWidth())
                    if (existing != null) TextButton(onClick = { onDelete(existing) }) { Text("이 기록 삭제", color = Color.Red) }
                }
            }
        },
        confirmButton = { TextButton(onClick = {
            val fmt = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA).apply { isLenient = false }
            val timestamp = try { fmt.parse("$measuredDate $measuredTime")?.time } catch (_: Exception) { null }
            val savedValue = if (type == "혈압") "${systolic.trim()}/${diastolic.trim()}/${pulse.trim()}" else value.trim()
            val validValue = if (type == "혈압") systolic.isNotBlank() && diastolic.isNotBlank() && pulse.isNotBlank() else value.trim().isNotEmpty()
            if (availableDevices.isNotEmpty() && type.isNotBlank() && validValue && timestamp != null) onSave(type, savedValue, note.trim(), selectedDevice, timestamp)
        }, enabled = availableDevices.isNotEmpty()) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } })
}

@Composable
private fun HospitalCalendar(selectedDate: String, markedDates: Set<String>, onDateSelected: (String) -> Unit) {
    val monthCalendar = remember(selectedDate) {
        java.util.Calendar.getInstance().apply {
            try { time = SimpleDateFormat("yyyy.MM.dd", Locale.KOREA).parse(selectedDate) ?: Date() } catch (_: Exception) { }
            set(java.util.Calendar.DAY_OF_MONTH, 1)
        }
    }
    var displayedMonth by remember(selectedDate) { mutableStateOf(monthCalendar.timeInMillis) }
    val month = java.util.Calendar.getInstance().apply { timeInMillis = displayedMonth }
    val year = month.get(java.util.Calendar.YEAR)
    val monthIndex = month.get(java.util.Calendar.MONTH)
    val firstDayOffset = (month.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7
    val daysInMonth = month.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    val c = java.util.Calendar.getInstance().apply { timeInMillis = displayedMonth; add(java.util.Calendar.MONTH, -1) }
                    displayedMonth = c.timeInMillis
                }) { Text("‹", fontSize = 24.sp, color = Green) }
                Spacer(Modifier.weight(1f))
                Text(String.format(Locale.KOREA, "%04d년 %02d월", year, monthIndex + 1), color = Ink, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    val c = java.util.Calendar.getInstance().apply { timeInMillis = displayedMonth; add(java.util.Calendar.MONTH, 1) }
                    displayedMonth = c.timeInMillis
                }) { Text("›", fontSize = 24.sp, color = Green) }
            }
            Row(Modifier.fillMaxWidth()) {
                listOf("월", "화", "수", "목", "금", "토", "일").forEach { day ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(day, color = if (day == "일") Color(0xFFD16B6B) else Color.Gray, fontSize = 11.sp)
                    }
                }
            }
            for (week in 0 until 6) {
                Row(Modifier.fillMaxWidth()) {
                    for (weekday in 0 until 7) {
                        val dayNumber = week * 7 + weekday - firstDayOffset + 1
                        if (dayNumber in 1..daysInMonth) {
                            val date = String.format(Locale.KOREA, "%04d.%02d.%02d", year, monthIndex + 1, dayNumber)
                            val marked = date in markedDates
                            val selected = date == selectedDate
                            Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp)
                                .background(when { selected -> Color(0xFFB7DCC2); marked -> Color(0xFFE5F2E8); else -> Color.Transparent }, RoundedCornerShape(9.dp))
                                .clickable { onDateSelected(date) }, contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                    Text(dayNumber.toString(), color = if (weekday == 6) Color(0xFFD16B6B) else Ink, fontSize = 12.sp, fontWeight = if (selected || marked) FontWeight.Bold else FontWeight.Normal)
                                    if (marked) Box(Modifier.size(4.dp).background(Green, RoundedCornerShape(50)))
                                }
                            }
                        } else Spacer(Modifier.weight(1f).aspectRatio(1f))
                    }
                }
            }
            Text("연한 초록색 날짜는 일정 또는 진료 기록이 있는 날입니다.", color = Color.Gray, fontSize = 10.sp)
        }
    }
}

@Composable
private fun AppointmentDialog(existing: HospitalAppointment?, initialDate: String? = null, initialStatus: String? = null, onDismiss: () -> Unit,
    onSave: (String, String, String, String, String) -> Unit, onDelete: (HospitalAppointment) -> Unit) {
    val context = LocalContext.current
    var hospital by remember(existing) { mutableStateOf(existing?.hospital ?: "") }
    var date by remember(existing, initialDate) { mutableStateOf(existing?.date ?: initialDate ?: SimpleDateFormat("yyyy.MM.dd", Locale.KOREA).format(Date(System.currentTimeMillis() + 86400000L))) }
    var time by remember(existing) { mutableStateOf(existing?.time ?: "09:00") }
    var memo by remember(existing) { mutableStateOf(existing?.memo ?: "") }
    var status by remember(existing, initialStatus) { mutableStateOf(existing?.status ?: initialStatus ?: "예정") }
    var statusMenu by remember { mutableStateOf(false) }

    fun showDatePicker() {
        val calendar = java.util.Calendar.getInstance()
        try {
            val parsed = SimpleDateFormat("yyyy.MM.dd", Locale.KOREA).parse(date)
            if (parsed != null) calendar.time = parsed
        } catch (_: Exception) { }
        DatePickerDialog(context, { _, year, month, day ->
            date = String.format(Locale.KOREA, "%04d.%02d.%02d", year, month + 1, day)
        }, calendar.get(java.util.Calendar.YEAR), calendar.get(java.util.Calendar.MONTH),
            calendar.get(java.util.Calendar.DAY_OF_MONTH)).show()
    }

    fun showTimePicker() {
        val parts = time.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 9
        val minute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
        TimePickerDialog(context, { _, selectedHour, selectedMinute ->
            time = String.format(Locale.KOREA, "%02d:%02d", selectedHour, selectedMinute)
        }, hour, minute, true).show()
    }

    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (existing == null) "병원 일정 등록" else "병원 일정 수정") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = hospital, onValueChange = { hospital = it }, label = { Text("병원명") }, singleLine = true)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("날짜 (YYYY.MM.DD)") }, singleLine = true, modifier = Modifier.weight(1f))
                IconButton(onClick = { showDatePicker() }) {
                    Icon(Icons.Default.DateRange, contentDescription = "달력에서 날짜 선택", tint = Green)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = time, onValueChange = { time = it }, label = { Text("시간 (HH:MM)") }, singleLine = true, modifier = Modifier.weight(1f))
                IconButton(onClick = { showTimePicker() }) {
                    Icon(Icons.Default.AccessTime, contentDescription = "시간 선택", tint = Green)
                }
            }
            OutlinedTextField(value = memo, onValueChange = { memo = it }, label = { Text("진료 내용 / 메모") }, singleLine = true)
            if (existing != null || initialStatus != null) {
                Box {
                    OutlinedButton(onClick = { statusMenu = true }, modifier = Modifier.fillMaxWidth()) { Text("진료 상태: $status") }
                    DropdownMenu(expanded = statusMenu, onDismissRequest = { statusMenu = false }) {
                        listOf("예정", "진료 완료", "미방문", "취소").forEach { option ->
                            DropdownMenuItem(text = { Text(option) }, onClick = { status = option; statusMenu = false })
                        }
                    }
                }
            }
            Text("달력과 시계 아이콘을 누르면 날짜와 시간을 선택할 수 있습니다.", color = Color.Gray, fontSize = 11.sp)
            Text("저장하면 지정한 날짜와 시간에 알림을 설정합니다.", color = Color.Gray, fontSize = 11.sp)
            if (existing != null) TextButton(onClick = { onDelete(existing) }) { Text("일정 삭제", color = Color.Red) }
        } },
        confirmButton = { TextButton(onClick = { if (hospital.isNotBlank()) onSave(hospital.trim(), date.trim(), time.trim(), memo.trim(), status) }) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } })
}

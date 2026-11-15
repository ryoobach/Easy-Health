package com.ryoo.easyhealth

import android.app.Activity
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material3.*
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private val Green = Color(0xFF16834A)
private val PaleGreen = Color(0xFFEAF5ED)
private val Ink = Color(0xFF183326)

private val vitalTypes = listOf("혈당", "수축기 혈압", "이완기 혈압", "맥박", "체온", "호흡수", "산소포화도", "체중", "체지방률", "골격근량", "BMI", "심전도")
private val units = mapOf("혈당" to "mg/dL", "수축기 혈압" to "mmHg", "이완기 혈압" to "mmHg", "맥박" to "bpm", "체온" to "℃", "호흡수" to "회/분", "산소포화도" to "%", "체중" to "kg", "체지방률" to "%", "골격근량" to "kg", "BMI" to "", "심전도" to "")
private val devices = listOf(
    "Contour Plus ONE" to listOf("혈당"),
    "CareSens Dual" to listOf("혈당"),
    "혈압계" to listOf("수축기 혈압", "이완기 혈압", "맥박"),
    "체중계" to listOf("체중", "체지방률", "골격근량", "BMI"),
    "체온계" to listOf("체온"),
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

@OptIn(ExperimentalMaterial3Api::class)
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
    var selectedPerson by remember { mutableStateOf(prefs.getString("selected_person", "본인") ?: "본인") }
    if (selectedPerson !in people) selectedPerson = people.first()
    val enabledDevices = remember {
        mutableStateListOf<String>().apply {
            val a = JSONArray(prefs.getString("enabled_devices", "[\"Contour Plus ONE\",\"CareSens Dual\"]") ?: "[]")
            for (i in 0 until a.length()) add(a.optString(i))
        }
    }
    var tab by remember { mutableStateOf("기록") }
    var showRecordDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<VitalRecord?>(null) }
    var showPeopleDialog by remember { mutableStateOf(false) }
    var newPerson by remember { mutableStateOf("") }
    var showDeviceInfo by remember { mutableStateOf(false) }
    var showAppInfo by remember { mutableStateOf(false) }
    var ownerNameInput by remember { mutableStateOf(prefs.getString("owner_name", "") ?: "") }
    var showOwnerSetup by remember { mutableStateOf(ownerNameInput.isBlank()) }
    var deleteTarget by remember { mutableStateOf<VitalRecord?>(null) }
    var reportPeriod by remember { mutableStateOf(prefs.getString("last_report_period", "주간") ?: "주간") }
    val dateFormat = remember { SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA) }
    val personRecords = records.filter { it.person == selectedPerson }.sortedByDescending { it.timestamp }
    val activeTypes = devices.filter { enabledDevices.contains(it.first) }.flatMap { it.second }.distinct()
    val reportRecords = personRecords.filter { activeTypes.contains(it.type) }
    val now = System.currentTimeMillis()
    val days = when (reportPeriod) { "월간" -> 30; "분기별" -> 90; "전체" -> 0; else -> 7 }
    val periodRecords = if (reportPeriod == "전체") reportRecords else reportRecords.filter { it.timestamp >= now - days * 86400000L }
    val glucoseValues = periodRecords.filter { it.type == "혈당" }.mapNotNull { it.value.toDoubleOrNull() }
    val averageGlucose = glucoseValues.takeIf { it.isNotEmpty() }?.average()
    val latestGlucose = personRecords.firstOrNull { it.type == "혈당" }

    MaterialTheme(colorScheme = lightColorScheme(primary = Green, secondary = Green, background = Color(0xFFF4F8F5), surface = Color.White)) {
        Scaffold(
            containerColor = Color(0xFFF4F8F5),
            topBar = {
                TopAppBar(title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Favorite, null, tint = Green)
                        Spacer(Modifier.width(8.dp))
                        Text("Easy Health", fontWeight = FontWeight.Bold, color = Ink)
                    }
                }, actions = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        TextButton(onClick = { showPeopleDialog = true }, contentPadding = PaddingValues(horizontal = 4.dp)) {
                            Text(selectedPerson + " ▾", color = Green, fontSize = 11.sp)
                        }
                        IconButton(onClick = { (context as? Activity)?.moveTaskToBack(true) }) {
                            Icon(Icons.Default.Home, contentDescription = "홈", tint = Green)
                        }
                        IconButton(onClick = { showAppInfo = true }) {
                            Icon(Icons.Default.Info, contentDescription = "앱정보", tint = Green)
                        }
                        IconButton(onClick = { (context as? Activity)?.finishAndRemoveTask() }) {
                            Icon(Icons.Default.ExitToApp, contentDescription = "앱종료", tint = Green)
                        }
                    }
                }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFF4F8F5)))
            },
            bottomBar = {
                NavigationBar(containerColor = Color.White) {
                    listOf("기록", "리포트", "기기 설정").forEach { name ->
                        NavigationBarItem(selected = tab == name, onClick = { tab = name },
                            icon = { Icon(if (name == "기록") Icons.Default.MonitorHeart else Icons.Default.Favorite, null) },
                            label = { Text(name) })
                    }
                }
            }
        ) { padding ->
            when (tab) {
                "기록" -> Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.fillMaxWidth().padding(20.dp)) {
                            Text("최근 혈당 · $selectedPerson", color = Ink, fontWeight = FontWeight.SemiBold)
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(latestGlucose?.value ?: "--", fontSize = 48.sp, color = Green, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(8.dp))
                                Text("mg/dL", color = Green, modifier = Modifier.padding(bottom = 8.dp))
                            }
                            Text(latestGlucose?.let { dateFormat.format(Date(it.timestamp)) } ?: "아직 혈당 기록이 없습니다.", color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("건강 측정값 입력", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
                            Text("새 기록을 추가하거나 기존 기록을 눌러 수정·삭제할 수 있습니다.", color = Color.Gray, fontSize = 12.sp)
                            Button(onClick = { editing = null; showRecordDialog = true }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("측정 기록 추가")
                            }
                            OutlinedButton(onClick = { showDeviceInfo = true }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Bluetooth, null); Spacer(Modifier.width(6.dp)); Text("블루투스 자동 가져오기")
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("통합 건강 기록", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
                        Spacer(Modifier.weight(1f))
                        Text("${personRecords.size}건", color = Color.Gray, fontSize = 12.sp)
                    }
                    if (personRecords.isEmpty()) {
                        Text("이 대상자의 측정 기록이 없습니다.", color = Color.Gray, modifier = Modifier.padding(12.dp))
                    } else {
                        personRecords.forEach { record ->
                            Card(modifier = Modifier.fillMaxWidth().clickable { editing = record; showRecordDialog = true },
                                shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(record.type, color = Ink, fontWeight = FontWeight.SemiBold)
                                        Text(dateFormat.format(Date(record.timestamp)), color = Color.Gray, fontSize = 11.sp)
                                        Text(record.source + if (record.note.isNotBlank()) " · " + record.note else "", color = Color.Gray, fontSize = 11.sp)
                                    }
                                    Text(record.value + (units[record.type]?.let { if (it.isNotBlank()) " $it" else "" } ?: ""),
                                        color = Green, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    Card(colors = CardDefaults.cardColors(containerColor = PaleGreen)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("함께 만드는 Easy Health", color = Green, fontWeight = FontWeight.Bold)
                            Text("개발: Ryoo Jaesang & 최피디\nAI 개발 협력: ChatGPT (OpenAI)", color = Ink, fontSize = 12.sp)
                        }
                    }
                }
                "리포트" -> Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("$selectedPerson 건강 리포트", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Ink)
                    ScrollableTabRow(selectedTabIndex = listOf("주간", "월간", "분기별", "전체").indexOf(reportPeriod).coerceAtLeast(0),
                        containerColor = Color.Transparent, edgePadding = 0.dp) {
                        listOf("주간", "월간", "분기별", "전체").forEach { label ->
                            Tab(selected = reportPeriod == label, onClick = {
                                reportPeriod = label
                                prefs.edit().putString("last_report_period", label).apply()
                            }, text = { Text(label) })
                        }
                    }
                    Text(if (reportPeriod == "전체") "전체 기록 기준" else "최근 ${days}일 기준", color = Color.Gray, fontSize = 12.sp)
                    if (activeTypes.isEmpty()) {
                        Text("기기 설정에서 리포트에 포함할 기기를 체크해 주세요.", color = Color.Gray)
                    } else {
                        activeTypes.forEach { type ->
                            val values = periodRecords.filter { it.type == type }.mapNotNull { it.value.toDoubleOrNull() }
                            val display = when {
                                values.isEmpty() -> "기록 없음"
                                type == "혈당" -> "평균 ${values.average().toInt()} ${units[type]}"
                                else -> "최근 ${values.first()} ${units[type] ?: ""}"
                            }
                            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
                                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(type, color = Ink, fontWeight = FontWeight.SemiBold)
                                    Text(display, color = if (values.isEmpty()) Color.Gray else Green, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                        Card(colors = CardDefaults.cardColors(containerColor = PaleGreen), shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.padding(18.dp)) {
                                Text("혈당 평균", color = Ink, fontWeight = FontWeight.SemiBold)
                                Text(averageGlucose?.let { "${it.toInt()} mg/dL" } ?: "--", color = Green, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                                Text("선택한 기기와 대상자의 기록만 계산합니다.", color = Color.Gray, fontSize = 11.sp)
                            }
                        }
                    }
                    OutlinedButton(onClick = { tab = "기기 설정" }, modifier = Modifier.fillMaxWidth()) { Text("리포트에 포함할 기기 설정") }
                }
                else -> Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("리포트 기기 선택", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text("체크한 기기에서 측정하는 항목만 리포트에 표시됩니다. 같은 항목을 측정하는 기기는 통합해서 분석합니다.", color = Color.Gray, fontSize = 13.sp)
                    devices.forEach { device ->
                        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp)) {
                            Row(Modifier.fillMaxWidth().clickable {
                                if (enabledDevices.contains(device.first)) enabledDevices.remove(device.first) else enabledDevices.add(device.first)
                                prefs.edit().putString("enabled_devices", JSONArray(enabledDevices).toString()).apply()
                            }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = enabledDevices.contains(device.first), onCheckedChange = { checked ->
                                    if (checked && !enabledDevices.contains(device.first)) enabledDevices.add(device.first)
                                    if (!checked) enabledDevices.remove(device.first)
                                    prefs.edit().putString("enabled_devices", JSONArray(enabledDevices).toString()).apply()
                                })
                                Column {
                                    Text(device.first, color = Ink, fontWeight = FontWeight.SemiBold)
                                    Text(device.second.joinToString(" · "), color = Color.Gray, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                    Text("현재는 리포트 표시 설정입니다. 실제 블루투스 연결과 자동 수집은 기기별 호환성 확인 후 추가됩니다.", color = Color.Gray, fontSize = 12.sp)
                    Button(onClick = { tab = "리포트" }, modifier = Modifier.fillMaxWidth()) { Text("설정 완료") }
                }
            }
        }
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
            Surface(
                modifier = Modifier.clickable { showAppInfo = false },
                shape = RoundedCornerShape(22.dp),
                color = Color.White
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Favorite, contentDescription = null, tint = Green, modifier = Modifier.size(42.dp))
                    Text("Easy Health ${BuildConfig.VERSION_NAME}", color = Ink, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    Text("Ryoo Jaesang & 최피디", color = Ink, fontSize = 14.sp)
                    Text("AI 개발 협력: ChatGPT (OpenAI)", color = Color.Gray, fontSize = 11.sp)
                    Text("화면을 터치하면 닫힙니다.", color = Color.Gray, fontSize = 10.sp)
                }
            }
        }
    }

    if (showRecordDialog) {
        RecordDialog(selectedPerson, editing, onDismiss = { showRecordDialog = false }, onSave = { type, value, note, source ->
            val timestamp = editing?.timestamp ?: System.currentTimeMillis()
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    people.forEach { person ->
                        Row(Modifier.fillMaxWidth().clickable {
                            selectedPerson = person
                            prefs.edit().putString("selected_person", person).apply()
                            showPeopleDialog = false
                        }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selectedPerson == person, onClick = {
                                selectedPerson = person; prefs.edit().putString("selected_person", person).apply(); showPeopleDialog = false
                            })
                            Text(person)
                        }
                    }
                    OutlinedTextField(value = newPerson, onValueChange = { newPerson = it }, label = { Text("새 대상자 이름") }, singleLine = true)
                }
            },
            confirmButton = { TextButton(onClick = {
                val name = newPerson.trim()
                if (name.isNotBlank() && !people.contains(name)) {
                    people.add(name); prefs.edit().putString("people", JSONArray(people).toString()).apply()
                    selectedPerson = name; prefs.edit().putString("selected_person", name).apply()
                }
                newPerson = ""; showPeopleDialog = false
            }) { Text("추가") } },
            dismissButton = { TextButton(onClick = { showPeopleDialog = false }) { Text("닫기") } })
    }
    if (showDeviceInfo) {
        AlertDialog(onDismissRequest = { showDeviceInfo = false }, title = { Text("블루투스 자동 가져오기") },
            text = { Text("현재 시험판에는 기기 선택과 통합 기록 구조가 준비되어 있습니다. Contour Plus ONE과 CareSens Dual을 포함한 실제 블루투스 연결·데이터 수신은 기기별 통신 방식 확인 후 추가해야 합니다. 아직 자동 수집은 실행되지 않습니다.") },
            confirmButton = { TextButton(onClick = { showDeviceInfo = false }) { Text("확인") } })
    }
}

@Composable
private fun RecordDialog(
    person: String,
    existing: VitalRecord?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit,
    onDelete: (VitalRecord) -> Unit
) {
    var type by remember(existing) { mutableStateOf(existing?.type ?: "혈당") }
    var value by remember(existing) { mutableStateOf(existing?.value ?: "") }
    var note by remember(existing) { mutableStateOf(existing?.note ?: "") }
    var source by remember(existing) { mutableStateOf(existing?.source ?: "직접 입력") }
    var menu by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (existing == null) "$person 측정 기록 추가" else "측정 기록 수정") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) { Text("측정 항목: $type") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        vitalTypes.forEach { item -> DropdownMenuItem(text = { Text(item) }, onClick = { type = item; menu = false }) }
                    }
                }
                OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text("측정값 ${units[type]?.let { "($it)" } ?: ""}") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("메모 (선택)") }, singleLine = true)
                Text("측정 대상: $person", color = Color.Gray, fontSize = 12.sp)
                Text("기록 시각: " + SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA).format(Date(existing?.timestamp ?: System.currentTimeMillis())),
                    color = Color.Gray, fontSize = 11.sp)
                if (existing != null) TextButton(onClick = { onDelete(existing) }) { Text("이 기록 삭제", color = Color.Red) }
            }
        },
        confirmButton = { TextButton(onClick = {
            if (value.trim().isNotEmpty()) onSave(type, value.trim(), note.trim(), source)
        }) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } })
}

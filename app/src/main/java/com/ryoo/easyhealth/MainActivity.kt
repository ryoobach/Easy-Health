package com.ryoo.easyhealth

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material3.*
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

private val Green = Color(0xFF16834A)
private val PaleGreen = Color(0xFFEAF5ED)
private val Ink = Color(0xFF183326)

data class GlucoseEntry(val timestamp: Long, val value: Int, val category: String, val source: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { EasyHealthApp(this) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EasyHealthApp(context: Context) {
    val prefs = remember { context.getSharedPreferences("easy_health", Context.MODE_PRIVATE) }
    val entries = remember {
        mutableStateListOf<GlucoseEntry>().apply {
            try {
                val array = JSONArray(prefs.getString("glucose_entries", "[]") ?: "[]")
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    add(GlucoseEntry(o.getLong("timestamp"), o.getInt("value"), o.optString("category", "기타"), o.optString("source", "직접 입력")))
                }
            } catch (_: Exception) { }
        }
    }
    var glucose by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("공복") }
    var reportPeriod by remember { mutableStateOf("주간") }
    var showInfo by remember { mutableStateOf(false) }
    val sorted = entries.sortedByDescending { it.timestamp }
    val latest = sorted.firstOrNull()
    val dateFormat = remember { SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA) }
    val dayFormat = remember { SimpleDateFormat("MM.dd", Locale.KOREA) }
    val now = System.currentTimeMillis()
    val periodDays = when (reportPeriod) { "월간" -> 30; "분기별" -> 90; else -> 7 }
    val periodEntries = entries.filter { it.timestamp >= now - periodDays * 24L * 60L * 60L * 1000L && it.timestamp <= now }
    val periodAverage = if (periodEntries.isNotEmpty()) periodEntries.map { it.value }.average() else null
    val dailyAverages = periodEntries.groupBy { dayFormat.format(Date(it.timestamp)) }
        .mapValues { (_, values) -> values.map { it.value }.average() }
        .toList().sortedBy { it.first }
    val last90Days = entries.filter { it.timestamp >= now - 90L * 24L * 60L * 60L * 1000L && it.timestamp <= now }
    val last90DayCount = last90Days.map { dayFormat.format(Date(it.timestamp)) }.distinct().size
    val estimatedA1c = if (last90Days.size >= 30 && last90DayCount >= 14) {
        ((last90Days.map { it.value }.average() + 46.7) / 28.7)
    } else null

    MaterialTheme(colorScheme = lightColorScheme(primary = Green, secondary = Green, background = Color(0xFFF4F8F5), surface = Color.White)) {
        Scaffold(containerColor = Color(0xFFF4F8F5), topBar = {
            TopAppBar(title = { Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Favorite, null, tint = Green)
                Spacer(Modifier.width(8.dp))
                Text("Easy Health", fontWeight = FontWeight.Bold, color = Ink)
            } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFF4F8F5)))
        }) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.fillMaxWidth().padding(20.dp)) {
                            Text("최근 혈당", color = Ink, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(latest?.value?.toString() ?: "--", fontSize = 48.sp, color = Green, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(8.dp))
                                Text("mg/dL", color = Green, modifier = Modifier.padding(bottom = 8.dp))
                            }
                            Text(if (latest == null) "아직 등록된 혈당 기록이 없습니다." else "측정 " + dateFormat.format(Date(latest.timestamp)), color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                }
                item {
                    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("건강 리포트", fontWeight = FontWeight.Bold, color = Ink, fontSize = 18.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                listOf("주간", "월간", "분기별").forEach { label ->
                                    FilterChip(
                                        selected = reportPeriod == label,
                                        onClick = { reportPeriod = label },
                                        label = { Text(label) }
                                    )
                                }
                            }
                            Text("최근 ${periodDays}일 기준", color = Color.Gray, fontSize = 12.sp)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text("평균 혈당", color = Color.Gray, fontSize = 12.sp)
                                    Text(periodAverage?.let { "${it.toInt()} mg/dL" } ?: "--", color = Green, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("측정 기록", color = Color.Gray, fontSize = 12.sp)
                                    Text("${periodEntries.size}건", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            if (dailyAverages.isNotEmpty()) {
                                Divider(color = PaleGreen)
                                Text(if (reportPeriod == "주간") "날짜별 평균" else "기간 내 날짜별 평균", color = Ink, fontWeight = FontWeight.SemiBold)
                                dailyAverages.takeLast(if (reportPeriod == "주간") 7 else 10).forEach { (day, average) ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(day, color = Color.Gray, fontSize = 12.sp, modifier = Modifier.width(48.dp))
                                        LinearProgressIndicator(
                                            progress = { (average / 300.0).toFloat().coerceIn(0f, 1f) },
                                            modifier = Modifier.weight(1f).height(7.dp),
                                            color = Green,
                                            trackColor = PaleGreen
                                        )
                                        Text("${average.toInt()}", color = Ink, fontSize = 12.sp, modifier = Modifier.width(42.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                                    }
                                }
                            } else {
                                Text("해당 기간에 등록된 혈당 기록이 없습니다.", color = Color.Gray, fontSize = 13.sp)
                            }
                            Divider(color = PaleGreen)
                            Text("예상 당화혈색소 (최근 90일)", color = Ink, fontWeight = FontWeight.SemiBold)
                            if (estimatedA1c != null) {
                                Text(String.format(Locale.KOREA, "%.1f%%", estimatedA1c), color = Green, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                                Text("혈당 기록의 평균으로 계산한 참고 추정치입니다. 실제 검사 결과와 다를 수 있으며 진단을 대신하지 않습니다.", color = Color.Gray, fontSize = 11.sp, lineHeight = 16.sp)
                            } else {
                                Text("추정하려면 최근 90일 동안 최소 30건, 14일 이상의 혈당 기록이 필요합니다.", color = Color.Gray, fontSize = 12.sp, lineHeight = 17.sp)
                            }
                        }
                    }
                }
                item {
                    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("혈당 직접 입력", fontWeight = FontWeight.Bold, color = Ink, fontSize = 18.sp)
                            OutlinedTextField(
                                value = glucose, onValueChange = { glucose = it.filter(Char::isDigit).take(4) },
                                label = { Text("혈당 수치 (mg/dL)") }, placeholder = { Text("예: 125") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true, modifier = Modifier.fillMaxWidth()
                            )
                            Text("측정 구분", color = Ink, fontWeight = FontWeight.Medium)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                                listOf("공복", "식전", "식후", "취침 전").forEach { item ->
                                    FilterChip(selected = category == item, onClick = { category = item }, label = { Text(item, fontSize = 11.sp) })
                                }
                            }
                            Button(onClick = {
                                val value = glucose.toIntOrNull()
                                if (value != null && value in 1..999) {
                                    entries.add(0, GlucoseEntry(System.currentTimeMillis(), value, category, "직접 입력"))
                                    val array = JSONArray()
                                    entries.forEach { e ->
                                        array.put(JSONObject().put("timestamp", e.timestamp).put("value", e.value).put("category", e.category).put("source", e.source))
                                    }
                                    prefs.edit().putString("glucose_entries", array.toString()).apply()
                                    glucose = ""
                                }
                            }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                                Icon(Icons.Default.Add, null)
                                Spacer(Modifier.width(6.dp))
                                Text("기록 저장")
                            }
                            OutlinedButton(onClick = { showInfo = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                                Icon(Icons.Default.Bluetooth, null)
                                Spacer(Modifier.width(8.dp))
                                Text("자동 가져오기")
                            }
                        }
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MonitorHeart, null, tint = Green)
                        Spacer(Modifier.width(8.dp))
                        Text("통합 혈당 기록표", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Ink)
                        Spacer(Modifier.weight(1f))
                        Text(entries.size.toString() + "건", color = Color.Gray, fontSize = 12.sp)
                    }
                }
                if (sorted.isEmpty()) {
                    item { Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Text("혈당을 입력하면 측정 시간순으로 여기에 표시됩니다.", modifier = Modifier.padding(18.dp), color = Color.Gray)
                    } }
                } else {
                    items(sorted) { entry ->
                        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(dateFormat.format(Date(entry.timestamp)), color = Ink, fontWeight = FontWeight.SemiBold)
                                    Text(entry.category + " · " + entry.source, color = Color.Gray, fontSize = 12.sp)
                                }
                                Text(entry.value.toString(), color = Green, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                Text(" mg/dL", color = Color.Gray, fontSize = 11.sp)
                            }
                        }
                    }
                }
                item {
                    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = PaleGreen)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("함께 만드는 Easy Health", color = Green, fontWeight = FontWeight.Bold)
                            Text("개발: Ryoo Jaesang & 최피디\nAI 개발 협력: ChatGPT (OpenAI)", color = Ink, fontSize = 12.sp, lineHeight = 19.sp)
                        }
                    }
                }
            }
        }
        if (showInfo) {
            AlertDialog(onDismissRequest = { showInfo = false }, title = { Text("자동 가져오기") },
                text = { Text("블루투스 혈당계 연결 기능은 다음 개발 단계에서 추가됩니다. 현재 버전에서는 혈당을 직접 입력하고 통합 기록표에 저장할 수 있습니다.") },
                confirmButton = { TextButton(onClick = { showInfo = false }) { Text("확인") } })
        }
    }
}

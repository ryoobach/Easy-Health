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
    var showInfo by remember { mutableStateOf(false) }
    val sorted = entries.sortedByDescending { it.timestamp }
    val latest = sorted.firstOrNull()
    val dateFormat = remember { SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA) }

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

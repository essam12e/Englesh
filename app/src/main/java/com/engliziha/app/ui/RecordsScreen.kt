package com.engliziha.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.engliziha.app.data.ProgressStore
import com.engliziha.app.notify.Reminders
import com.engliziha.app.data.Course
import com.engliziha.app.data.ProgressState
import com.engliziha.app.data.SkillScore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RecordsScreen(course: Course, state: ProgressState, store: ProgressStore, onBack: () -> Unit) {
    val context = LocalContext.current
    var reminders by remember { mutableStateOf(Reminders.enabled(context)) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            val ok = try {
                context.contentResolver.openOutputStream(uri)?.use { it.write(store.exportJson().toByteArray()) } != null
            } catch (e: Exception) { false }
            Toast.makeText(context, if (ok) "حُفظت النسخة الاحتياطية" else "تعذّر حفظ النسخة", Toast.LENGTH_SHORT).show()
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val ok = try {
                context.contentResolver.openInputStream(uri)?.use { store.importJson(it.readBytes().decodeToString()) } == true
            } catch (e: Exception) { false }
            Toast.makeText(context, if (ok) "تم استرجاع تقدمك" else "هذا الملف ليس نسخة احتياطية صالحة", Toast.LENGTH_SHORT).show()
        }
    }
    val extra = LocalExtra.current
    val attempts = state.attempts.asReversed()
    val passed = course.all.count { state.lessons[it.n]?.passed == true }
    val total = course.all.size
    val average = if (attempts.isEmpty()) 0 else attempts.sumOf { it.percent } / attempts.size
    fun sum(pick: (com.engliziha.app.data.Attempt) -> SkillScore) =
        SkillScore(attempts.sumOf { pick(it).correct }, attempts.sumOf { pick(it).total })
    val dateFormat = SimpleDateFormat("d MMM yyyy · HH:mm", Locale("ar"))

    Column(Modifier.fillMaxSize()) {
        TopBar("السجلات والتقدم", onBack)
        LazyColumn(
            Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        ProgressRing(if (total == 0) 0f else passed.toFloat() / total, MaterialTheme.colorScheme.primary, size = 104.dp, stroke = 10.dp) {
                            Text("${if (total == 0) 0 else passed * 100 / total}%", style = Numeric, fontSize = 24.sp)
                        }
                        Spacer(Modifier.width(20.dp))
                        Column {
                            Text("$passed من $total", style = Numeric, fontSize = 30.sp)
                            Text("درساً أنجزتها بنجاح", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            Text("${attempts.size} اختباراً · المتوسط $average%", style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                Text("تقدمك في كل مستوى", style = MaterialTheme.typography.titleLarge)
            }
            items(course.levels, key = { "lvl" + it.code }) { level ->
                val done = level.lessons.count { state.lessons[it.n]?.passed == true }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(level.code, Modifier.width(40.dp), style = Numeric, fontSize = 16.sp, color = extra.level(level.code))
                    ProgressLine(if (level.lessons.isEmpty()) 0f else done.toFloat() / level.lessons.size,
                        extra.level(level.code), Modifier.weight(1f), height = 10.dp)
                    Text("$done/${level.lessons.size}", Modifier.width(56.dp).padding(start = 10.dp), style = Numeric, fontSize = 14.sp)
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                Text("دقتك حسب المهارة", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(10.dp))
                SkillBars(sum { it.questions }, sum { it.writing }, sum { it.speaking })
            }
            item {
                Spacer(Modifier.height(8.dp))
                Text("الإعدادات", style = MaterialTheme.typography.titleLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("التذكير اليومي", style = MaterialTheme.typography.titleMedium)
                        Text("3 إشعارات: 8 صباحاً، 4 عصراً، 9 مساءً", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = reminders, onCheckedChange = { reminders = it; Reminders.setEnabled(context, it) })
                }
                Spacer(Modifier.height(8.dp))
                Text("النسخة الاحتياطية", style = MaterialTheme.typography.titleMedium)
                Text("احفظ تقدمك في ملف لتسترجعه إذا غيّرت الجوال أو حذفت التطبيق. الاسترجاع يستبدل التقدم الحالي.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
                    OutlinedButton(onClick = { export.launch("english-backup.json") }, modifier = Modifier.weight(1f)) { Text("حفظ نسخة") }
                    OutlinedButton(onClick = { import.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }, modifier = Modifier.weight(1f)) { Text("استرجاع نسخة") }
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                Text("سجل الاختبارات", style = MaterialTheme.typography.titleLarge)
            }
            if (attempts.isEmpty()) item {
                Text("لم تُجرِ أي اختبار بعد. شاهد درسك الأول ثم اختبر نفسك وسيظهر السجل هنا.",
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(attempts, key = { it.time }) { a ->
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(a.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${if (a.lesson.firstOrNull()?.isDigit() == true) "الدرس ${a.lesson} · " else ""}${a.level} · ${dateFormat.format(Date(a.time))}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${a.correct} من ${a.total} · ${if (a.passed) "ناجح" else "لم ينجح"}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("${a.percent}%", style = Numeric, fontSize = 22.sp,
                            color = if (a.passed) extra.success else MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

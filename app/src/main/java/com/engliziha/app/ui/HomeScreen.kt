package com.engliziha.app.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.engliziha.app.R
import com.engliziha.app.data.dueWords
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import com.engliziha.app.data.Course
import com.engliziha.app.data.Lesson
import com.engliziha.app.data.Level
import com.engliziha.app.data.ProgressState
import com.engliziha.app.data.currentLesson
import com.engliziha.app.data.isUnlocked

@Composable
fun HomeScreen(
    course: Course,
    state: ProgressState,
    onLevel: (String) -> Unit,
    onLesson: (String) -> Unit,
    onRecords: () -> Unit,
    onReview: () -> Unit,
    onDaily: () -> Unit,
    onNotebook: () -> Unit,
    onQuiz: (QuizMode) -> Unit,
    onSkipPlacement: () -> Unit,
) {
    // Ask once for permission to show the daily reminders (Android 13+).
    val appContext = LocalContext.current
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    val extra = LocalExtra.current
    val passed = course.all.count { state.lessons[it.n]?.passed == true }
    // Resume the lesson last opened if it is still unfinished, otherwise the next one in the path.
    val resume = state.last?.let(course::lesson)?.takeIf { state.lessons[it.n]?.passed != true }
        ?: course.currentLesson(state)

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.logo), contentDescription = null, modifier = Modifier.size(64.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("English", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                    Text("تعلّم .. وتطوّر .. وحقق هدفك", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onRecords) { Text("السجلات") }
            }
        }
        if (!state.placementDone && passed == 0) item {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text("🎯 هل تعرف بعض الإنجليزية؟", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("اختبار قصير من 20 سؤالاً يحدد مستواك ويفتح لك الدروس المناسبة بدل البدء من الصفر.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Button(onClick = { onQuiz(QuizMode.Placement) }) { Text("حدّد مستواي") }
                        TextButton(onClick = onSkipPlacement) { Text("أبدأ من الصفر") }
                    }
                }
            }
        }
        if (resume != null) item {
            ResumeCard(resume, state, passed, course.all.size) { onLesson(resume.n) }
            Spacer(Modifier.height(12.dp))
            DailyRow(state.streak(), course.dueWords(state).size, onReview)
            Spacer(Modifier.height(12.dp))
            val daily = state.dailyToday()
            Surface(
                onClick = onDaily, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("📅", fontSize = 28.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("جملة اليوم", style = MaterialTheme.typography.titleMedium)
                        Text("صباحاً وعصراً و9 مساءً — احفظها كتابةً ونطقاً", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(if (daily.tested) "✓" else "${daily.learned.size}/3", style = Numeric, fontSize = 18.sp,
                        color = if (daily.tested) LocalExtra.current.success else MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    onClick = { onQuiz(QuizMode.Mistakes) }, enabled = state.mistakes.isNotEmpty(),
                    shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.weight(1f),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("🛠 أخطائي", style = MaterialTheme.typography.titleMedium)
                        Text(if (state.mistakes.isEmpty()) "لا أخطاء محفوظة" else "${state.mistakes.size} سؤالاً للتصحيح",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Surface(
                    onClick = onNotebook, shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.weight(1f),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("⭐ دفتر كلماتي", style = MaterialTheme.typography.titleMedium)
                        Text("${state.starred.size} محفوظة", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("المستويات", style = MaterialTheme.typography.titleLarge)
        }
        items(course.levels, key = { it.code }) { level ->
            val context = LocalContext.current
            val open = level.lessons.firstOrNull()?.let { course.isUnlocked(it.n, state) } ?: false
            LevelRow(level, state, open, extra.level(level.code)) {
                if (open) onLevel(level.code)
                else Toast.makeText(context, "أكمل المستوى السابق أولاً لفتح هذا المستوى", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

/** Today's habit: the study streak and the words waiting for review. */
@Composable
private fun DailyRow(streak: Int, due: Int, onReview: () -> Unit) {
    val extra = LocalExtra.current
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = MaterialTheme.shapes.medium, color = extra.attentionContainer, modifier = Modifier.weight(1f)) {
            Column(Modifier.padding(14.dp)) {
                Text("🔥 $streak", style = Numeric, fontSize = 24.sp, color = extra.onAttentionContainer)
                Text(if (streak == 0) "ابدأ سلسلتك اليوم" else "يوماً متتالياً", style = MaterialTheme.typography.bodySmall,
                    color = extra.onAttentionContainer)
            }
        }
        Surface(
            onClick = onReview, enabled = due > 0,
            shape = MaterialTheme.shapes.medium, color = extra.successContainer, modifier = Modifier.weight(2f),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(if (due > 0) "🧠 مراجعة اليوم: $due كلمة" else "🧠 لا كلمات للمراجعة الآن",
                    style = MaterialTheme.typography.titleMedium)
                Text(if (due > 0) "راجعها حتى لا تنساها — تعمل بدون إنترنت" else "تظهر هنا كلمات الدروس التي نجحت فيها",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ResumeCard(lesson: Lesson, state: ProgressState, passed: Int, total: Int, onClick: () -> Unit) {
    val progress = state.lessons[lesson.n]
    val started = (progress?.pos ?: 0f) > 5f
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                if (started) "تابع من حيث توقفت" else "درسك التالي",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f),
            )
            Spacer(Modifier.height(6.dp))
            Text("الدرس ${lesson.label} · ${lesson.level}", style = MaterialTheme.typography.titleSmall)
            Text(lesson.title_ar, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onPrimary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.PlayArrow, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (started) "من الدقيقة ${formatTime(progress?.pos ?: 0f)}" else "ابدأ المشاهدة",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text("أنجزت $passed من $total درساً", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f))
                }
            }
            Spacer(Modifier.height(14.dp))
            ProgressLine(
                if (total == 0) 0f else passed.toFloat() / total,
                MaterialTheme.colorScheme.onPrimary, Modifier.fillMaxWidth(), height = 5.dp,
            )
        }
    }
}

@Composable
private fun LevelRow(level: Level, state: ProgressState, open: Boolean, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    val done = level.lessons.count { state.lessons[it.n]?.passed == true }
    val total = level.lessons.size
    val complete = total > 0 && done == total
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(topStart = 18.dp, bottomEnd = 18.dp, topEnd = 6.dp, bottomStart = 6.dp))
                    .background(if (open) color else MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    level.code, style = Numeric, fontSize = 20.sp,
                    color = if (open) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("المستوى ${level.name}", style = MaterialTheme.typography.titleMedium)
                Text(level.blurb, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressLine(if (total == 0) 0f else done.toFloat() / total, color, Modifier.weight(1f))
                    Spacer(Modifier.width(10.dp))
                    Text("$done / $total", style = Numeric, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(8.dp))
            when {
                !open -> Icon(Icons.Filled.Lock, "مقفل", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                complete -> Icon(Icons.Filled.Check, "مكتمل", tint = LocalExtra.current.success, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
fun LevelScreen(course: Course, code: String, state: ProgressState, onBack: () -> Unit, onLesson: (String) -> Unit, onExam: () -> Unit) {
    val level = course.level(code) ?: return
    val color = LocalExtra.current.level(code)
    val context = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        TopBar("${level.code} · المستوى ${level.name}", onBack)
        LazyColumn(
            Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 32.dp),
        ) {
            item {
                Text(level.blurb, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
            }
            items(level.lessons, key = { it.n }) { lesson ->
                val open = course.isUnlocked(lesson.n, state)
                val p = state.lessons[lesson.n]
                val isCurrent = open && p?.passed != true
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                        .background(if (isCurrent) MaterialTheme.colorScheme.surface else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable {
                            if (open) onLesson(lesson.n)
                            else Toast.makeText(context, "انجح في اختبار الدرس السابق لفتح هذا الدرس", Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(
                            when {
                                p?.passed == true -> color
                                isCurrent -> color.copy(alpha = 0.16f)
                                else -> MaterialTheme.colorScheme.surfaceContainer
                            }
                        ),
                        contentAlignment = Alignment.Center,
                    ) {
                        when {
                            p?.passed == true -> Icon(Icons.Filled.Check, "ناجح", tint = MaterialTheme.colorScheme.surface, modifier = Modifier.size(22.dp))
                            !open -> Icon(Icons.Filled.Lock, "مقفل", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                            else -> Text(lesson.n.filter { it.isDigit() }, style = Numeric, fontSize = 15.sp, color = color)
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            lesson.title_ar, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = if (open) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "الدرس ${lesson.label} · ${formatTime(lesson.sec.toFloat())} · ${typeName(lesson.type)}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (isCurrent && (p?.watched ?: 0f) > 0.02f) {
                            Spacer(Modifier.height(6.dp))
                            ProgressLine(p?.watched ?: 0f, color, Modifier.fillMaxWidth(0.6f), height = 4.dp)
                        }
                    }
                    if ((p?.best ?: -1) >= 0) {
                        Text("${p?.best}%", style = Numeric, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
                            color = if (p?.passed == true) LocalExtra.current.success else MaterialTheme.colorScheme.error)
                    }
                }
            }
            item {
                ExamCard(code, level.lessons.all { state.lessons[it.n]?.passed == true }, state.exams[code], color, onExam)
            }
        }
    }
}

/** The level's final exam; opens when every lesson in the level is passed. */
@Composable
private fun ExamCard(code: String, ready: Boolean, best: Int?, color: androidx.compose.ui.graphics.Color, onExam: () -> Unit) {
    Surface(
        onClick = onExam, enabled = ready, shape = MaterialTheme.shapes.large,
        color = if (ready) color.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (ready) "🏅" else "🔒", fontSize = 30.sp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("الاختبار الشامل للمستوى $code", style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        !ready -> "يُفتح بعد النجاح في كل دروس المستوى"
                        best != null -> "أفضل نتيجة $best% — اضغط لإعادة الاختبار"
                        else -> "20 سؤالاً من كل الدروس، وشهادة إنجاز عند النجاح"
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

fun typeName(type: String): String = when (type) {
    "grammar" -> "قواعد"
    "vocab" -> "مفردات"
    "conversation" -> "محادثة"
    "skills" -> "مهارات"
    "test" -> "اختبار مستوى"
    else -> "درس"
}

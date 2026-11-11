package com.engliziha.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.engliziha.app.data.Course
import com.engliziha.app.data.DAILY_HOURS
import com.engliziha.app.data.ProgressState
import com.engliziha.app.data.ProgressStore
import com.engliziha.app.data.dailySentences
import com.engliziha.app.quiz.slotName
import com.engliziha.app.quiz.wordsHeard
import com.engliziha.app.speech.ListenResult
import com.engliziha.app.speech.Listener
import com.engliziha.app.speech.Speaker
import java.util.Calendar

private val SLOT_ICONS = listOf("🌅", "🌤", "🌙")
private val SLOT_TIMES = listOf("8:00 صباحاً", "4:00 عصراً", "9:00 مساءً")

/** Sentence of the day: three sentences that open through the day, then an evening test. */
@Composable
fun DailyScreen(course: Course, state: ProgressState, onBack: () -> Unit, onQuiz: (QuizMode) -> Unit) {
    val extra = LocalExtra.current
    val sentences = remember(state.startLevel, state.lessons.count { it.value.passed }) { course.dailySentences(state) }
    val daily = state.dailyToday()
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val allLearned = sentences.isNotEmpty() && sentences.indices.all { it in daily.learned }

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        TopBar("جملة اليوم", onBack)
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("ثلاث جمل كل يوم: احفظ كل واحدة كتابةً ونطقاً، ثم اختبر نفسك فيها مساءً.",
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            sentences.forEachIndexed { slot, s ->
                val open = hour >= DAILY_HOURS[slot]
                val learned = slot in daily.learned
                Surface(
                    onClick = { onQuiz(QuizMode.DailyLearn(slot)) }, enabled = open,
                    shape = MaterialTheme.shapes.large,
                    color = if (learned) extra.successContainer else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(SLOT_ICONS[slot], fontSize = 34.sp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(slotName(slot), style = MaterialTheme.typography.titleMedium)
                            if (open) {
                                English(s.phrase.en, Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.End),
                                    color = MaterialTheme.colorScheme.primary)
                                Text(s.phrase.ar, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                Text("🔒 تُفتح الساعة ${SLOT_TIMES[slot]}", style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when { learned -> "✓ حفظتها"; open -> "احفظها"; else -> "" },
                            style = MaterialTheme.typography.labelLarge,
                            color = if (learned) extra.success else MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            if (sentences.isEmpty()) Text("لا توجد جمل متاحة الآن.", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = { onQuiz(QuizMode.DailyTest) }, enabled = allLearned,
                modifier = Modifier.fillMaxWidth().height(54.dp), shape = MaterialTheme.shapes.medium,
            ) {
                Text(if (daily.tested) "أعد اختبار جمل اليوم ✓" else "اختبار جمل اليوم (كتابة ونطق)")
            }
            if (!allLearned) Text("يُفتح الاختبار بعد أن تحفظ الجمل الثلاث.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
    }
}

/** Words and sentences the learner starred in lessons. */
@Composable
fun NotebookScreen(course: Course, state: ProgressState, store: ProgressStore, onBack: () -> Unit) {
    val context = LocalContext.current
    val speaker = remember { Speaker(context) }
    DisposableEffect(Unit) { onDispose { speaker.release() } }
    // key -> (english, arabic, picture)
    val entries = state.starred.asReversed().mapNotNull { key ->
        val lesson = course.lesson(key.substringBefore('|')) ?: return@mapNotNull null
        val en = key.substringAfter('|')
        lesson.vocab.firstOrNull { it.en == en }?.let { Triple(key, it.en to it.ar, it.emoji) }
            ?: lesson.phrases.firstOrNull { it.en == en }?.let { Triple(key, it.en to it.ar, "💬") }
    }
    Column(Modifier.fillMaxSize()) {
        TopBar("دفتر كلماتي", onBack)
        LazyColumn(
            Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (entries.isEmpty()) item {
                Text("دفترك فارغ. اضغط النجمة ☆ بجانب أي كلمة أو جملة داخل الدرس لتحفظها هنا.",
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(entries, key = { it.first }) { (key, text, emoji) ->
                Surface(
                    onClick = { speaker.say(text.first) },
                    shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(start = 14.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(emoji, fontSize = 26.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            English("🔊 ${text.first}", Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.End),
                                color = MaterialTheme.colorScheme.primary)
                            Text(text.second, style = MaterialTheme.typography.bodyMedium)
                        }
                        TextButton(onClick = { store.toggleStar(key) }) { Text("★", fontSize = 22.sp, color = LocalExtra.current.attention) }
                    }
                }
            }
        }
    }
}

/** Listen and repeat: hear a sentence, say it, and see which words were heard. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShadowScreen(course: Course, n: String, onBack: () -> Unit) {
    val lesson = course.lesson(n) ?: return
    val context = LocalContext.current
    val extra = LocalExtra.current
    val speaker = remember { Speaker(context) }
    val listener = remember { Listener(context) }
    DisposableEffect(Unit) { onDispose { speaker.release(); listener.stop() } }

    val lines = remember { lesson.phrases.map { it.en to it.ar } + lesson.vocab.map { it.en to it.ar } }
    var index by remember { mutableIntStateOf(0) }
    var listening by remember { mutableStateOf(false) }
    var hits by remember { mutableStateOf<List<Boolean>?>(null) }
    var heard by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val line = lines.getOrNull(index)

    fun start(text: String) {
        message = null
        listening = true
        listener.listen(onStart = {}) { result ->
            listening = false
            when (result) {
                is ListenResult.Failed -> message = result.message
                is ListenResult.Heard -> {
                    val best = result.alternatives.maxBy { alt -> wordsHeard(text, alt).count { it } }
                    heard = best
                    hits = wordsHeard(text, best)
                }
            }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) line?.let { start(it.first) } else message = "يجب السماح باستخدام الميكروفون"
    }

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        TopBar("استمع وردّد", onBack)
        if (line == null) {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("👏", fontSize = 64.sp)
                Text("أنهيت تمرين الترديد", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(20.dp))
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth().height(52.dp), shape = MaterialTheme.shapes.medium) { Text("تم") }
            }
            return
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressLine(index.toFloat() / lines.size, MaterialTheme.colorScheme.primary, Modifier.weight(1f), height = 8.dp)
                Text("${index + 1}/${lines.size}", Modifier.padding(start = 12.dp), style = Numeric, fontSize = 14.sp)
            }
            Spacer(Modifier.height(16.dp))
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Ltr {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
                            line.first.split(" ").filter { it.isNotBlank() }.forEachIndexed { i, word ->
                                val state = hits?.getOrNull(i)
                                Text(
                                    word, style = MaterialTheme.typography.headlineMedium,
                                    color = when (state) {
                                        true -> extra.success
                                        false -> MaterialTheme.colorScheme.error
                                        null -> MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(line.second, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { speaker.say(line.first) }) { Text("🔊 استمع") }
                        TextButton(onClick = { speaker.say(line.first, 0.5f) }) { Text("🐢 ببطء") }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Surface(
                onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) start(line.first)
                    else permission.launch(Manifest.permission.RECORD_AUDIO)
                },
                enabled = !listening && listener.available, shape = CircleShape,
                color = if (listening) extra.attention else MaterialTheme.colorScheme.primary, modifier = Modifier.size(96.dp),
            ) { Box(contentAlignment = Alignment.Center) { Text("🎤", fontSize = 40.sp) } }
            Spacer(Modifier.height(10.dp))
            Text(
                when {
                    !listener.available -> "خدمة التعرف على الصوت غير متوفرة على هذا الجهاز"
                    listening -> "أسمعك الآن… ردّد الجملة"
                    hits == null -> "استمع أولاً ثم اضغط الميكروفون وردّد"
                    hits?.all { it } == true -> "ممتاز! نطقت كل الكلمات ✓"
                    else -> "الكلمات الحمراء لم تُسمع بوضوح — حاول مرة أخرى"
                },
                style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
            )
            heard?.let { English("“$it”", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            message?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
            Spacer(Modifier.weight(1f))
            OutlinedButton(
                onClick = { listener.stop(); listening = false; hits = null; heard = null; message = null; index++ },
                modifier = Modifier.fillMaxWidth().height(52.dp), shape = MaterialTheme.shapes.medium,
            ) { Text(if (index == lines.lastIndex) "إنهاء" else "الجملة التالية") }
            Spacer(Modifier.height(12.dp))
        }
    }
}

package com.engliziha.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import com.engliziha.app.data.Course
import com.engliziha.app.data.ProgressStore
import com.engliziha.app.data.dueWords
import com.engliziha.app.speech.Speaker

private const val SESSION_SIZE = 15

/** Flashcards for words from passed lessons. Known words come back after longer and longer gaps. */
@Composable
fun ReviewScreen(course: Course, store: ProgressStore, onBack: () -> Unit) {
    val context = LocalContext.current
    val speaker = remember { Speaker(context) }
    DisposableEffect(Unit) { onDispose { speaker.release() } }

    val cards = remember { course.dueWords(store.state.value).take(SESSION_SIZE) }
    var index by remember { mutableIntStateOf(0) }
    var revealed by remember { mutableStateOf(false) }
    var known by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        TopBar("مراجعة اليوم", onBack)
        if (index >= cards.size) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("🎉", fontSize = 64.sp)
                Spacer(Modifier.height(12.dp))
                Text(if (cards.isEmpty()) "لا كلمات للمراجعة الآن" else "أنهيت مراجعة اليوم",
                    style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                if (cards.isNotEmpty()) Text(
                    "عرفت $known من ${cards.size} كلمة. الكلمات التي لم تعرفها ستعود إليك غداً.",
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth().height(52.dp), shape = MaterialTheme.shapes.medium) { Text("تم") }
            }
            return
        }
        val card = cards[index]
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressLine(index.toFloat() / cards.size, MaterialTheme.colorScheme.primary, Modifier.weight(1f), height = 8.dp)
                Text("${index + 1}/${cards.size}", Modifier.padding(start = 12.dp), style = Numeric, fontSize = 14.sp)
            }
            Spacer(Modifier.height(16.dp))
            Surface(
                onClick = { if (!revealed) { revealed = true; speaker.say(card.word.en) } },
                shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(card.word.emoji, fontSize = 96.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(card.word.ar, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(20.dp))
                    if (revealed) {
                        English(card.word.en, style = MaterialTheme.typography.displaySmall.copy(textAlign = TextAlign.Center),
                            color = MaterialTheme.colorScheme.primary)
                        TextButton(onClick = { speaker.say(card.word.en) }) { Text("🔊 استمع مرة أخرى") }
                    } else {
                        Box(Modifier.background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.small).padding(horizontal = 18.dp, vertical = 10.dp)) {
                            Text("تذكّر الكلمة بالإنجليزية ثم اضغط للكشف", style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("من الدرس ${card.lesson.label} · ${card.lesson.title_ar}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
            Spacer(Modifier.height(16.dp))
            fun answer(ok: Boolean) {
                store.reviewWord(card.key, ok)
                if (ok) known++
                revealed = false
                index++
            }
            if (revealed) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { answer(false) }, modifier = Modifier.weight(1f).height(54.dp), shape = MaterialTheme.shapes.medium) {
                        Text("لم أعرفها")
                    }
                    Button(onClick = { answer(true) }, modifier = Modifier.weight(1f).height(54.dp), shape = MaterialTheme.shapes.medium) {
                        Text("عرفتها ✓")
                    }
                }
            } else {
                Button(onClick = { revealed = true; speaker.say(card.word.en) }, modifier = Modifier.fillMaxWidth().height(54.dp), shape = MaterialTheme.shapes.medium) {
                    Text("اكشف الكلمة")
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

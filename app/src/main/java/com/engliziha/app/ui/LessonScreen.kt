package com.engliziha.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.engliziha.app.data.Alert
import com.engliziha.app.data.Course
import com.engliziha.app.data.Lesson
import com.engliziha.app.data.PASS_PERCENT
import com.engliziha.app.data.ProgressState
import com.engliziha.app.data.ProgressStore
import com.engliziha.app.speech.Speaker
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

@Composable
fun LessonScreen(
    course: Course,
    n: String,
    state: ProgressState,
    store: ProgressStore,
    onBack: () -> Unit,
    onQuiz: () -> Unit,
) {
    val lesson = course.lesson(n) ?: return
    val context = LocalContext.current
    val speaker = remember { Speaker(context) }
    DisposableEffect(Unit) { onDispose { speaker.release() } }
    LaunchedEffect(n) { store.markOpened(n) }

    var finished by remember { mutableStateOf(false) }
    val best = state.lessons[n]?.best ?: -1

    Column(Modifier.fillMaxSize()) {
        TopBar("الدرس ${lesson.label} · ${lesson.level}", onBack)
        LessonPlayer(lesson, store, onEnded = { finished = true })
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(typeName(lesson.type), style = MaterialTheme.typography.labelMedium,
                color = LocalExtra.current.level(lesson.level))
            Text(lesson.title_ar, style = MaterialTheme.typography.headlineMedium)
            English(lesson.title_en, Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleSmall.copy(textAlign = TextAlign.End),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(lesson.summary, style = MaterialTheme.typography.bodyLarge)

            Button(onClick = onQuiz, modifier = Modifier.fillMaxWidth().height(52.dp), shape = MaterialTheme.shapes.medium) {
                Text(
                    when {
                        best < 0 -> "ابدأ اختبار الدرس"
                        best >= PASS_PERCENT -> "أعد الاختبار · أفضل نتيجة $best%"
                        else -> "أعد الاختبار · نتيجتك $best% (النجاح من $PASS_PERCENT%)"
                    }
                )
            }

            if (lesson.alerts.isNotEmpty()) {
                SectionTitle("انتبه لهذه النقاط")
                lesson.alerts.forEach { AlertCard(it) }
            }
            if (lesson.notes.isNotEmpty()) {
                SectionTitle("ملخص الدرس")
                lesson.notes.forEachIndexed { i, note ->
                    Surface(
                        shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${i + 1}", style = Numeric, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(10.dp))
                                Text(note.h, style = MaterialTheme.typography.titleMedium)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(note.t, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
            if (lesson.vocab.isNotEmpty()) {
                SectionTitle("كلمات الدرس — اضغط لتسمع النطق")
                Surface(
                    shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        lesson.vocab.forEach { v ->
                            Row(
                                Modifier.fillMaxWidth().clickable { speaker.say(v.en) }.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(v.emoji, fontSize = 26.sp)
                                Spacer(Modifier.width(12.dp))
                                Text(v.ar, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                English(v.en, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text("🔊", fontSize = 16.sp)
                            }
                        }
                    }
                }
            }
            if (lesson.phrases.isNotEmpty()) {
                SectionTitle("جمل مفيدة")
                lesson.phrases.forEach { p ->
                    Surface(
                        onClick = { speaker.say(p.en) },
                        shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            English("🔊  ${p.en}", Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleMedium)
                            Text(p.ar, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    if (finished) {
        AlertDialog(
            onDismissRequest = { finished = false },
            title = { Text("أنهيت مشاهدة الدرس") },
            text = { Text("اختبر نفسك الآن في ما شاهدته. تحتاج $PASS_PERCENT% لفتح الدرس التالي.") },
            confirmButton = { Button(onClick = { finished = false; onQuiz() }) { Text("ابدأ الاختبار") } },
            dismissButton = { TextButton(onClick = { finished = false }) { Text("راجع الملخص أولاً") } },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Spacer(Modifier.height(8.dp))
    Text(text, style = MaterialTheme.typography.titleLarge)
}

@Composable
private fun AlertCard(alert: Alert) {
    val extra = LocalExtra.current
    val label = when (alert.k) {
        "pron" -> "نطق"
        "grammar" -> "قاعدة"
        "mistake" -> "خطأ شائع"
        else -> "نصيحة"
    }
    Surface(shape = MaterialTheme.shapes.medium, color = extra.attentionContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp)) {
            Box(Modifier.width(4.dp).height(44.dp).clip(CircleShape).background(extra.attention))
            Spacer(Modifier.width(12.dp))
            Column {
                Text("⚠ $label", style = MaterialTheme.typography.labelLarge, color = extra.attention)
                Spacer(Modifier.height(2.dp))
                Text(alert.t, style = MaterialTheme.typography.bodyLarge, color = extra.onAttentionContainer)
            }
        }
    }
}

/** The video with our own controls: play/pause, ±10s, seek, speed. Remembers where the learner stopped. */
@Composable
private fun LessonPlayer(lesson: Lesson, store: ProgressStore, onEnded: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val startAt = remember(lesson.n) {
        val saved = store.state.value.lessons[lesson.n]?.pos ?: 0f
        // Start over if the learner had reached the very end.
        if (lesson.sec > 0 && saved > lesson.sec - 8) 0f else saved
    }

    var player by remember { mutableStateOf<YouTubePlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    var current by remember { mutableFloatStateOf(startAt) }
    var duration by remember { mutableFloatStateOf(lesson.sec.toFloat()) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    var failed by remember { mutableStateOf(false) }
    var speed by remember { mutableIntStateOf(1) }
    var lastSaved by remember { mutableFloatStateOf(startAt) }

    val view = remember(lesson.n) {
        YouTubePlayerView(context).apply {
            enableAutomaticInitialization = false
            initialize(object : AbstractYouTubePlayerListener() {
                override fun onReady(youTubePlayer: YouTubePlayer) {
                    player = youTubePlayer
                    youTubePlayer.cueVideo(lesson.id, startAt)
                }

                override fun onStateChange(youTubePlayer: YouTubePlayer, state: PlayerConstants.PlayerState) {
                    playing = state == PlayerConstants.PlayerState.PLAYING
                    if (state == PlayerConstants.PlayerState.PAUSED) store.savePosition(lesson.n, current, duration)
                    if (state == PlayerConstants.PlayerState.ENDED) {
                        store.savePosition(lesson.n, duration, duration)
                        onEnded()
                    }
                }

                override fun onCurrentSecond(youTubePlayer: YouTubePlayer, second: Float) {
                    current = second
                    if (kotlin.math.abs(second - lastSaved) >= 5f) {
                        lastSaved = second
                        store.savePosition(lesson.n, second, duration)
                    }
                }

                override fun onVideoDuration(youTubePlayer: YouTubePlayer, duration_: Float) {
                    if (duration_ > 0f) duration = duration_
                }

                override fun onError(youTubePlayer: YouTubePlayer, error: PlayerConstants.PlayerError) {
                    failed = true
                }
            }, true, IFramePlayerOptions.Builder(context).controls(0).rel(0).build())
        }
    }
    DisposableEffect(view) {
        lifecycle.addObserver(view)
        onDispose {
            if (current > 0f) store.savePosition(lesson.n, current, duration)
            lifecycle.removeObserver(view)
            view.release()
        }
    }

    fun seek(to: Float) {
        val target = to.coerceIn(0f, (duration - 1f).coerceAtLeast(0f))
        current = target
        player?.seekTo(target)
    }

    Column(Modifier.fillMaxWidth().background(Color.Black)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
            if (failed) {
                Column(
                    Modifier.fillMaxSize().background(Color.Black).padding(24.dp),
                    verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("تعذّر تشغيل الفيديو داخل التطبيق", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=${lesson.id}")))
                    }) { Text("افتحه في يوتيوب", color = Color.White) }
                }
            }
        }
        Ltr {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatTime(dragging ?: current), color = Color.White, style = Numeric, fontSize = 12.sp)
                    Slider(
                        value = (dragging ?: current).coerceIn(0f, duration.coerceAtLeast(1f)),
                        onValueChange = { dragging = it },
                        onValueChangeFinished = { dragging?.let(::seek); dragging = null },
                        valueRange = 0f..duration.coerceAtLeast(1f),
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                    Text(formatTime(duration), color = Color.White, style = Numeric, fontSize = 12.sp)
                }
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
                ) {
                    ControlText("${SPEEDS[speed].first}x", "سرعة التشغيل") {
                        speed = (speed + 1) % SPEEDS.size
                        player?.setPlaybackRate(SPEEDS[speed].second)
                    }
                    ControlText("−10", "ترجيع عشر ثوانٍ") { seek(current - 10f) }
                    Box(
                        Modifier.size(56.dp).clip(CircleShape).background(Color.White)
                            .clickable(enabled = player != null) { if (playing) player?.pause() else player?.play() },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (playing) {
                            Row {
                                Box(Modifier.size(width = 6.dp, height = 20.dp).background(Color.Black))
                                Spacer(Modifier.width(6.dp))
                                Box(Modifier.size(width = 6.dp, height = 20.dp).background(Color.Black))
                            }
                        } else {
                            Icon(Icons.Filled.PlayArrow, "تشغيل", tint = Color.Black, modifier = Modifier.size(34.dp))
                        }
                    }
                    ControlText("+10", "تقديم عشر ثوانٍ") { seek(current + 10f) }
                    ControlText("↺", "من البداية") { seek(0f) }
                }
            }
        }
    }
}

private val SPEEDS = listOf(
    "0.75" to PlayerConstants.PlaybackRate.RATE_0_75,
    "1" to PlayerConstants.PlaybackRate.RATE_1,
    "1.25" to PlayerConstants.PlaybackRate.RATE_1_25,
    "1.5" to PlayerConstants.PlaybackRate.RATE_1_5,
)

@Composable
private fun ControlText(text: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(52.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).clickable(onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, style = Numeric, fontSize = 15.sp)
    }
}

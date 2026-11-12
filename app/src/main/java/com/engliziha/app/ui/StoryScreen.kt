package com.engliziha.app.ui

import android.net.Uri
import android.widget.Toast
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.engliziha.app.data.PASS_PERCENT
import com.engliziha.app.data.ProgressState
import com.engliziha.app.data.ProgressStore
import com.engliziha.app.data.Story
import com.engliziha.app.speech.Speaker

@Composable
fun StoriesScreen(stories: List<Story>, state: ProgressState, onBack: () -> Unit, onStory: (String) -> Unit) {
    val extra = LocalExtra.current
    Column(Modifier.fillMaxSize()) {
        TopBar("القصص الصوتية", onBack)
        LazyColumn(
            Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text("قصص قصيرة من مشروع African Storybook يقرؤها رواة حقيقيون. استمع وتابع النص، واعرض الترجمة عند الحاجة، ثم اختبر فهمك.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            stories.groupBy { it.level }.toSortedMap().forEach { (level, list) ->
                item(key = "h$level") {
                    Spacer(Modifier.height(8.dp))
                    Text("المستوى $level · ${Story.LEVEL_NAMES[level].orEmpty()}", style = MaterialTheme.typography.titleLarge)
                }
                items(list, key = { it.id }) { story ->
                    val best = state.stories[story.id]
                    Surface(
                        onClick = { onStory(story.id) }, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(52.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainer),
                                contentAlignment = Alignment.Center) { Text(story.emoji, fontSize = 28.sp) }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(story.title_ar, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                English(story.title, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall.copy(textAlign = TextAlign.End),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${story.pages.size - 1} صفحات", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (best != null) Text("$best%", style = Numeric, fontSize = 15.sp,
                                color = if (best >= PASS_PERCENT) extra.success else MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

private val SPEEDS = listOf(0.75f, 1f, 1.25f)

/** Plays the narration page by page; the page being read is highlighted and any page can be tapped to replay it. */
@Composable
fun StoryScreen(story: Story, state: ProgressState, store: ProgressStore, onBack: () -> Unit, onQuiz: () -> Unit) {
    val context = LocalContext.current
    val extra = LocalExtra.current
    val speaker = remember { Speaker(context) }
    val listState = rememberLazyListState()

    var page by remember { mutableIntStateOf((state.storyPage[story.id] ?: 0).coerceIn(0, story.pages.lastIndex)) }
    var playing by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    var showArabic by remember { mutableStateOf(false) }
    var speed by remember { mutableIntStateOf(1) }

    // ExoPlayer plays the pages as one playlist, so moving to the next page is seamless and
    // behaves the same on every phone (MediaPlayer differs between manufacturers).
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(),
                true,
            )
            setMediaItems(story.pages.indices.map { MediaItem.fromUri(Uri.parse("asset:///" + story.audio(it))) }, page, 0L)
            prepare()
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                page = player.currentMediaItemIndex
                store.storyPage(story.id, page)
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) { finished = true; playing = false }
            }
            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(context, "تعذّر تشغيل الصوت: ${error.errorCodeName}", Toast.LENGTH_LONG).show()
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release(); speaker.release() }
    }
    LaunchedEffect(page) { listState.animateScrollToItem(page + 1) }

    fun play(index: Int) {
        val target = index.coerceIn(0, story.pages.lastIndex)
        finished = false
        page = target
        store.storyPage(story.id, target)
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        player.seekTo(target, 0L)
        player.play()
    }

    fun toggle() {
        when {
            player.isPlaying -> player.pause()
            finished -> play(0)
            else -> {
                if (player.playbackState == Player.STATE_IDLE) player.prepare()
                player.play()
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(story.title_ar, onBack)
        LazyColumn(
            Modifier.weight(1f),
            state = listState,
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(story.emoji, fontSize = 40.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        English(story.title, Modifier.fillMaxWidth(), style = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.End))
                        Text(story.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    Text("اعرض الترجمة العربية", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    Switch(checked = showArabic, onCheckedChange = { showArabic = it })
                }
            }
            itemsIndexed(story.pages) { i, p ->
                val current = i == page
                val bg by animateColorAsState(
                    if (current) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, label = "page",
                )
                Surface(
                    onClick = { play(i) }, shape = MaterialTheme.shapes.medium, color = bg,
                    border = BorderStroke(1.dp, if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("${if (i == 0) "العنوان" else "صفحة $i"}${if (current && playing) " · 🔊 تُقرأ الآن" else ""}", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary)
                        English(p.en, Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Start, lineHeight = 26.sp))
                        if (showArabic) {
                            Spacer(Modifier.height(6.dp))
                            Text(p.ar, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (story.vocab.isNotEmpty()) item {
                Spacer(Modifier.height(6.dp))
                Text("كلمات من القصة — اضغط لتسمع", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()) {
                    Column {
                        story.vocab.forEach { v ->
                            Row(Modifier.fillMaxWidth().clickable { speaker.say(v.en) }.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(v.ar, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                English(v.en, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text("🔊", fontSize = 16.sp)
                            }
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                Button(onClick = { player.pause(); onQuiz() }, modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = MaterialTheme.shapes.medium) {
                    Text(state.stories[story.id]?.let { "أعد اختبار الفهم · أفضل نتيجة $it%" } ?: "اختبر فهمك للقصة")
                }
                Spacer(Modifier.height(12.dp))
                English(story.credits, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // Player bar
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
                ProgressLine((page + if (finished) 1 else 0).toFloat() / story.pages.size, MaterialTheme.colorScheme.primary, Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Ltr {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        BarButton("${SPEEDS[speed]}x", "سرعة القراءة") {
                            speed = (speed + 1) % SPEEDS.size
                            player.setPlaybackSpeed(SPEEDS[speed])
                        }
                        BarButton("⏮", "الصفحة السابقة") { play(page - 1) }
                        Box(
                            Modifier.size(60.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).clickable { toggle() },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (playing) Row {
                                Box(Modifier.size(width = 6.dp, height = 20.dp).background(Color.White))
                                Spacer(Modifier.width(6.dp))
                                Box(Modifier.size(width = 6.dp, height = 20.dp).background(Color.White))
                            } else Icon(Icons.Filled.PlayArrow, "تشغيل", tint = Color.White, modifier = Modifier.size(36.dp))
                        }
                        BarButton("⏭", "الصفحة التالية") { play(page + 1) }
                        BarButton("↻", "أعد الصفحة") { play(page) }
                    }
                }
                if (finished) Text("انتهت القصة 🎉 — اختبر فهمك من الزر في الأسفل", Modifier.fillMaxWidth().padding(top = 6.dp),
                    style = MaterialTheme.typography.titleSmall, color = extra.success, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun BarButton(text: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = Numeric, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface) }
}

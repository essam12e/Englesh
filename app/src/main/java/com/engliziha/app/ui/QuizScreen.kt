package com.engliziha.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.engliziha.app.data.Attempt
import com.engliziha.app.data.Course
import com.engliziha.app.data.Dictionary
import com.engliziha.app.quiz.QuizItem
import com.engliziha.app.data.PASS_PERCENT
import com.engliziha.app.data.ProgressStore
import com.engliziha.app.data.SkillScore
import com.engliziha.app.quiz.Question
import com.engliziha.app.quiz.SPEECH_PASS
import com.engliziha.app.quiz.Skill
import com.engliziha.app.quiz.buildItems
import com.engliziha.app.quiz.dailyLearnItems
import com.engliziha.app.quiz.dailyTestItems
import com.engliziha.app.quiz.examItems
import com.engliziha.app.quiz.mistakeItems
import com.engliziha.app.quiz.placementItems
import com.engliziha.app.quiz.placementLevel
import com.engliziha.app.quiz.slotName
import com.engliziha.app.quiz.sameText
import com.engliziha.app.quiz.speechScore
import com.engliziha.app.speech.ListenResult
import com.engliziha.app.speech.Listener
import com.engliziha.app.speech.Speaker

/** What happened on one question: right, wrong, or skipped (not counted). */
private data class Outcome(val correct: Boolean?, val feedback: String)

/** The different tests that share this screen. */
sealed interface QuizMode {
    data class LessonQuiz(val n: String) : QuizMode
    data object Mistakes : QuizMode
    data object Placement : QuizMode
    data class Exam(val code: String) : QuizMode
    data class DailyLearn(val slot: Int) : QuizMode
    data object DailyTest : QuizMode
    data object Spelling : QuizMode
}

private data class Summary(
    val correct: Int, val total: Int,
    val questions: SkillScore, val writing: SkillScore, val speaking: SkillScore,
    val placed: String? = null,
) {
    val percent: Int get() = if (total == 0) 0 else correct * 100 / total
    val passed: Boolean get() = percent >= PASS_PERCENT
}

@Composable
fun QuizScreen(course: Course, mode: QuizMode, store: ProgressStore, dictionary: () -> Dictionary, onClose: () -> Unit, onNext: (String) -> Unit) {
    val context = LocalContext.current
    val speaker = remember { Speaker(context) }
    val listener = remember { Listener(context) }
    DisposableEffect(Unit) { onDispose { speaker.release(); listener.stop() } }

    var round by remember { mutableIntStateOf(0) }
    val items = remember(round) {
        val state = store.state.value
        when (mode) {
            is QuizMode.LessonQuiz -> course.lesson(mode.n)?.let { buildItems(it) }.orEmpty()
            QuizMode.Mistakes -> mistakeItems(course, state.mistakes)
            QuizMode.Placement -> placementItems(course)
            is QuizMode.Exam -> examItems(course, mode.code)
            is QuizMode.DailyLearn -> dailyLearnItems(course, state, mode.slot)
            QuizMode.DailyTest -> dailyTestItems(course, state)
            // Hear each word you misspelled in the writing pad and type it.
            QuizMode.Spelling -> state.spelling.shuffled().take(10).map { w ->
                QuizItem(Question.Dictation(w, dictionary().example(w)?.arabic.orEmpty()), "")
            }
        }
    }
    val results = remember(round) { mutableStateListOf<Boolean?>() }
    var index by remember(round) { mutableIntStateOf(0) }
    var outcome by remember(round) { mutableStateOf<Outcome?>(null) }
    var summary by remember(round) { mutableStateOf<Summary?>(null) }
    var confirmExit by remember { mutableStateOf(false) }

    BackHandler(enabled = summary == null) { confirmExit = true }

    fun finish() {
        fun score(skill: Skill): SkillScore {
            val picked = items.indices.filter { items[it].question.skill == skill && results[it] != null }
            return SkillScore(picked.count { results[it] == true }, picked.size)
        }
        var s = Summary(
            results.count { it == true }, results.count { it != null },
            score(Skill.QUESTIONS), score(Skill.WRITING), score(Skill.SPEAKING),
        )
        // A wrong answer in a lesson quiz or level exam goes to "my mistakes"; a right answer anywhere clears it.
        val wrong = items.indices.filter { results[it] == false }.mapNotNull { items[it].mistake }
        val right = items.indices.filter { results[it] == true }.mapNotNull { items[it].mistake }
        val track = mode is QuizMode.LessonQuiz || mode is QuizMode.Exam
        store.updateMistakes(if (track) wrong else emptyList(), right)

        fun attempt(lesson: String, title: String, level: String) = Attempt(
            lesson = lesson, title = title, level = level, time = System.currentTimeMillis(),
            correct = s.correct, total = s.total, questions = s.questions, writing = s.writing, speaking = s.speaking,
        )
        when (mode) {
            is QuizMode.LessonQuiz -> course.lesson(mode.n)?.let { store.addAttempt(attempt(it.n, it.title_ar, it.level)) }
            is QuizMode.Exam -> {
                store.addAttempt(attempt("exam-${mode.code}", "الاختبار الشامل للمستوى ${mode.code}", mode.code), lessonQuiz = false)
                store.setExam(mode.code, s.percent)
            }
            QuizMode.Placement -> {
                val placed = placementLevel(course, items, results)
                store.setPlacement(placed)
                s = s.copy(placed = placed)
            }
            is QuizMode.DailyLearn -> store.dailyLearned(mode.slot)
            QuizMode.DailyTest -> {
                store.dailyTested()
                store.addAttempt(attempt("daily", "اختبار جمل اليوم", items.firstOrNull()?.let { course.lesson(it.lesson)?.level } ?: ""), lessonQuiz = false)
            }
            QuizMode.Mistakes -> {}
            QuizMode.Spelling -> store.spellingFixed(
                items.indices.filter { results[it] == true }.mapNotNull { (items[it].question as? Question.Dictation)?.text }
            )
        }
        summary = s
    }

    val done = summary
    if (done != null) {
        ResultView(course, mode, done, store, onNext = onNext, onRetry = { round++ }, onClose = onClose)
        return
    }
    if (items.isEmpty()) {
        Column(Modifier.fillMaxSize()) {
            TopBar("اختبار", onClose)
            Text("لا توجد أسئلة هنا الآن.", Modifier.padding(20.dp))
        }
        return
    }

    val question = items[index].question
    fun advance(correct: Boolean?) {
        results += correct
        outcome = null
        if (index == items.lastIndex) finish() else index++
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { confirmExit = true }) { Icon(Icons.Filled.Close, "إغلاق الاختبار") }
            ProgressLine(index.toFloat() / items.size, MaterialTheme.colorScheme.primary, Modifier.weight(1f), height = 8.dp)
            Text("${index + 1}/${items.size}", Modifier.padding(horizontal = 14.dp), style = Numeric, fontSize = 14.sp)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Text(
                when {
                    question is Question.Study -> question.label
                    question.skill == Skill.WRITING -> "كتابة"
                    question.skill == Skill.SPEAKING -> "نطق"
                    else -> "سؤال"
                },
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            val answered = outcome != null
            val answer: (Outcome) -> Unit = { if (outcome == null) outcome = it }
            // key() gives every question its own fresh state.
            androidx.compose.runtime.key(round, index) {
                when (question) {
                    is Question.Study -> StudyBody(question, speaker) { advance(null) }
                    is Question.Choice -> ChoiceBody(question.prompt, null, question.options, question.answer, false, answered) { ok ->
                        answer(Outcome(ok, question.why))
                    }
                    is Question.Picture -> ChoiceBody("ما اسم هذا بالإنجليزية؟", question.emoji, question.options, question.answer, true, answered) { ok ->
                        answer(Outcome(ok, "${question.options[question.answer]} = ${question.arabic}"))
                    }
                    is Question.Order -> OrderBody(question, answered, answer)
                    is Question.Dictation -> WriteBody(
                        title = "استمع ثم اكتب الكلمة التي سمعتها", emoji = null, hint = null,
                        expected = question.text, meaning = question.arabic, speaker = speaker, autoSpeak = true,
                        answered = answered, onAnswer = answer,
                    )
                    is Question.Translate -> WriteBody(
                        title = if (' ' in question.text.trim()) "اكتب الجملة بالإنجليزية" else "اكتب هذه الكلمة بالإنجليزية",
                        emoji = question.emoji, hint = question.arabic,
                        expected = question.text, meaning = question.arabic, speaker = speaker, autoSpeak = false,
                        answered = answered, onAnswer = answer,
                    )
                    is Question.Speak -> SpeakBody(question, speaker, listener, answered, answer)
                }
            }
        }
        outcome?.let { o -> FeedbackBar(o, last = index == items.lastIndex) { advance(o.correct) } }
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("الخروج الآن؟") },
            text = { Text("لن تُحفظ إجاباتك في السجلات إذا خرجت الآن.") },
            confirmButton = { TextButton(onClick = { confirmExit = false; onClose() }) { Text("اخرج") } },
            dismissButton = { Button(onClick = { confirmExit = false }) { Text("أكمل") } },
        )
    }
}

/** A sentence to memorise before being asked to write and say it. */
@Composable
private fun StudyBody(q: Question.Study, speaker: Speaker, onReady: () -> Unit) {
    LaunchedEffect(Unit) { speaker.say(q.text) }
    Text("احفظ هذه الجملة", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(16.dp))
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            English(q.text, style = MaterialTheme.typography.headlineMedium.copy(textAlign = TextAlign.Center),
                color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.height(8.dp))
            Text(q.arabic, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { speaker.say(q.text) }) { Text("🔊 استمع") }
                TextButton(onClick = { speaker.say(q.text, 0.5f) }) { Text("🐢 ببطء") }
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    Text("اقرأها بصوت مسموع عدة مرات وانتبه لإملاء كل كلمة. بعدها ستكتبها من حفظك ثم تنطقها.",
        style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(20.dp))
    Button(onClick = onReady, modifier = Modifier.fillMaxWidth().height(52.dp), shape = MaterialTheme.shapes.medium) { Text("حفظتها — اختبرني") }
}

@Composable
private fun FeedbackBar(outcome: Outcome, last: Boolean, onContinue: () -> Unit) {
    val extra = LocalExtra.current
    val (bg, fg, title) = when (outcome.correct) {
        true -> Triple(extra.successContainer, extra.success, "✓ إجابة صحيحة")
        false -> Triple(MaterialTheme.colorScheme.error.copy(alpha = 0.12f), MaterialTheme.colorScheme.error, "✗ إجابة غير صحيحة")
        null -> Triple(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.colorScheme.onSurfaceVariant, "تم التخطي — لن يُحتسب هذا السؤال")
    }
    Surface(color = bg, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = fg)
            if (outcome.feedback.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(outcome.feedback, style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().height(50.dp), shape = MaterialTheme.shapes.medium) {
                Text(if (last) "اعرض النتيجة" else "التالي")
            }
        }
    }
}

@Composable
private fun Illustration(emoji: String) {
    Box(
        Modifier.fillMaxWidth().padding(vertical = 12.dp).height(150.dp)
            .clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center,
    ) { Text(emoji, fontSize = 84.sp) }
}

@Composable
private fun ChoiceBody(
    prompt: String, emoji: String?, options: List<String>, answer: Int, english: Boolean,
    answered: Boolean, onPick: (Boolean) -> Unit,
) {
    var picked by remember { mutableStateOf<Int?>(null) }
    val extra = LocalExtra.current
    Text(prompt, style = MaterialTheme.typography.headlineSmall)
    if (emoji != null) Illustration(emoji) else Spacer(Modifier.height(16.dp))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        options.forEachIndexed { i, option ->
            val border = when {
                answered && i == answer -> extra.success
                answered && i == picked -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.outline
            }
            val fill = when {
                answered && i == answer -> extra.successContainer
                else -> MaterialTheme.colorScheme.surface
            }
            Surface(
                onClick = { picked = i; onPick(i == answer) },
                enabled = !answered,
                shape = MaterialTheme.shapes.medium, color = fill,
                border = BorderStroke(if (answered && (i == answer || i == picked)) 2.dp else 1.dp, border),
                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
            ) {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), contentAlignment = Alignment.CenterStart) {
                    val latin = english || option.none { it in '؀'..'ۿ' }
                    Text(
                        option, Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.titleMedium.copy(
                            textDirection = if (latin) TextDirection.Ltr else TextDirection.Rtl,
                            textAlign = if (latin) TextAlign.Left else TextAlign.Right,
                        ),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OrderBody(q: Question.Order, answered: Boolean, onAnswer: (Outcome) -> Unit) {
    // Positions into q.shuffled, in the order the learner tapped them.
    val chosen = remember { mutableStateListOf<Int>() }
    Text("رتّب الكلمات لتكوّن الجملة", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(6.dp))
    Text(q.arabic, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(16.dp))
    Ltr {
        Surface(
            shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp),
        ) {
            FlowRow(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                chosen.forEach { i ->
                    WordChip(q.shuffled[i], MaterialTheme.colorScheme.primaryContainer, enabled = !answered) { chosen.remove(i) }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            q.shuffled.forEachIndexed { i, word ->
                val used = i in chosen
                WordChip(
                    word, if (used) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    enabled = !used && !answered, faded = used,
                ) { chosen += i }
            }
        }
    }
    Spacer(Modifier.height(20.dp))
    if (!answered) {
        Button(
            onClick = {
                val ok = chosen.map { q.shuffled[it] } == q.words
                onAnswer(Outcome(ok, q.words.joinToString(" ")))
            },
            enabled = chosen.size == q.shuffled.size,
            modifier = Modifier.fillMaxWidth().height(50.dp), shape = MaterialTheme.shapes.medium,
        ) { Text("تحقق") }
    }
}

@Composable
private fun WordChip(word: String, color: Color, enabled: Boolean, faded: Boolean = false, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, shape = MaterialTheme.shapes.small, color = color) {
        Text(
            word, Modifier.padding(horizontal = 14.dp, vertical = 10.dp), style = MaterialTheme.typography.titleMedium,
            color = if (faded) Color.Transparent else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun WriteBody(
    title: String, emoji: String?, hint: String?, expected: String, meaning: String,
    speaker: Speaker, autoSpeak: Boolean, answered: Boolean, onAnswer: (Outcome) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val submit = {
        if (text.isNotBlank() && !answered) onAnswer(Outcome(sameText(text, expected), if (meaning.isBlank()) expected else "$expected = $meaning"))
    }
    if (autoSpeak) LaunchedEffect(Unit) { speaker.say(expected) }
    Text(title, style = MaterialTheme.typography.headlineSmall)
    if (emoji != null) Illustration(emoji)
    if (hint != null) Text(hint, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
    if (autoSpeak) {
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { speaker.say(expected) }, modifier = Modifier.weight(1f).height(56.dp)) { Text("🔊  استمع") }
            OutlinedButton(onClick = { speaker.say(expected, 0.5f) }, modifier = Modifier.weight(1f).height(56.dp)) { Text("🐢  ببطء") }
        }
    }
    Spacer(Modifier.height(16.dp))
    Ltr {
        OutlinedTextField(
            value = text, onValueChange = { text = it }, enabled = !answered, singleLine = true,
            textStyle = MaterialTheme.typography.headlineSmall.copy(textDirection = TextDirection.Ltr),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false,
                keyboardType = KeyboardType.Password, imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            placeholder = { Text("type here") },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Spacer(Modifier.height(16.dp))
    if (!answered) {
        Button(onClick = submit, enabled = text.isNotBlank(), modifier = Modifier.fillMaxWidth().height(50.dp), shape = MaterialTheme.shapes.medium) {
            Text("تحقق")
        }
    }
}

private const val MAX_TRIES = 3

@Composable
private fun SpeakBody(q: Question.Speak, speaker: Speaker, listener: Listener, answered: Boolean, onAnswer: (Outcome) -> Unit) {
    val context = LocalContext.current
    var listening by remember { mutableStateOf(false) }
    var tries by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var heard by remember { mutableStateOf<String?>(null) }

    fun start() {
        message = null
        listening = true
        listener.listen(onStart = {}) { result ->
            listening = false
            when (result) {
                is ListenResult.Failed -> message = result.message
                is ListenResult.Heard -> {
                    val best = result.alternatives.maxBy { speechScore(q.text, it) }
                    heard = best
                    tries++
                    when {
                        speechScore(q.text, best) >= SPEECH_PASS -> onAnswer(Outcome(true, "سمعتك تقول: $best"))
                        tries >= MAX_TRIES -> onAnswer(Outcome(false, "سمعتك تقول: $best\nالمطلوب: ${q.text}"))
                        else -> message = "لم يكن النطق واضحاً. استمع مرة أخرى ثم حاول (${MAX_TRIES - tries} محاولات باقية)"
                    }
                }
            }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) start() else message = "يجب السماح باستخدام الميكروفون لاختبار النطق"
    }
    DisposableEffect(Unit) { onDispose { listener.stop() } }

    Text("انطق بصوت واضح", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(16.dp))
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            English(q.text, style = MaterialTheme.typography.headlineMedium.copy(textAlign = TextAlign.Center))
            Spacer(Modifier.height(4.dp))
            Text(q.arabic, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { speaker.say(q.text) }) { Text("🔊 استمع للنطق") }
                TextButton(onClick = { speaker.say(q.text, 0.5f) }) { Text("🐢 ببطء") }
            }
        }
    }
    Spacer(Modifier.height(24.dp))
    if (!answered) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (!listener.available) {
                Text("خدمة التعرف على الصوت غير متوفرة على هذا الجهاز (تحتاج تطبيق Google).",
                    style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            } else {
                Surface(
                    onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) start()
                        else permission.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    enabled = !listening, shape = CircleShape,
                    color = if (listening) LocalExtra.current.attention else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(96.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) { Text("🎤", fontSize = 40.sp) }
                }
                Spacer(Modifier.height(10.dp))
                Text(if (listening) "أسمعك الآن… تكلّم" else "اضغط الميكروفون ثم انطق", style = MaterialTheme.typography.titleMedium)
            }
            message?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
            heard?.let {
                Spacer(Modifier.height(6.dp))
                English("“$it”", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = { listener.stop(); onAnswer(Outcome(null, "")) }) { Text("لا أستطيع التحدث الآن — تخطَّ") }
        }
    }
}

@Composable
private fun ResultView(
    course: Course, mode: QuizMode, s: Summary, store: ProgressStore,
    onNext: (String) -> Unit, onRetry: () -> Unit, onClose: () -> Unit,
) {
    val extra = LocalExtra.current
    val scored = mode !is QuizMode.DailyLearn && mode !is QuizMode.Placement
    val good = when (mode) {
        is QuizMode.LessonQuiz, is QuizMode.Exam -> s.passed
        else -> true
    }
    val color = if (good) extra.success else MaterialTheme.colorScheme.error
    val next = (mode as? QuizMode.LessonQuiz)?.let { course.next(it.n) }
    val (headline, message) = when (mode) {
        is QuizMode.LessonQuiz ->
            if (s.passed) "أحسنت! نجحت في الدرس" to "أجبت عن ${s.correct} من ${s.total} بشكل صحيح، وفُتح لك الدرس التالي."
            else "لم تصل إلى $PASS_PERCENT% بعد" to "أجبت عن ${s.correct} من ${s.total}. راجع ملخص الدرس ثم أعد المحاولة. أخطاؤك حُفظت في «أخطائي»."
        is QuizMode.Exam ->
            if (s.passed) "مبروك! أتممت المستوى ${mode.code}" to "أجبت عن ${s.correct} من ${s.total} في الاختبار الشامل."
            else "لم تصل إلى $PASS_PERCENT% بعد" to "أجبت عن ${s.correct} من ${s.total}. راجع دروس المستوى ثم أعد الاختبار."
        QuizMode.Spelling -> "كتبت ${s.correct} من ${s.total} صحيحة" to "الكلمات التي كتبتها صحيحة خرجت من قائمة أخطائك الإملائية."
        QuizMode.Mistakes -> "صحّحت ${s.correct} من ${s.total}" to "الأسئلة التي أجبتها صحيحة حُذفت من قائمة أخطائك، والباقي ينتظرك."
        QuizMode.Placement -> "مستواك المناسب: ${s.placed}" to
            "أجبت عن ${s.correct} من ${s.total}. فتحنا لك المستوى ${s.placed} وكل ما قبله، وتستطيع دائماً الرجوع للدروس السابقة."
        is QuizMode.DailyLearn -> "حفظت ${slotName(mode.slot)} ✓" to "ستُختبر فيها مع بقية جمل اليوم في اختبار المساء."
        QuizMode.DailyTest -> "أنهيت اختبار جمل اليوم" to "أجبت عن ${s.correct} من ${s.total} بشكل صحيح. جمل جديدة تنتظرك غداً."
    }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        if (scored) {
            ProgressRing(s.percent / 100f, color, size = 168.dp, stroke = 14.dp) {
                Text("${s.percent}%", style = Numeric, fontSize = 40.sp, color = color)
            }
        } else {
            Text(if (mode is QuizMode.Placement) "🎯" else "🌟", fontSize = 84.sp)
        }
        Spacer(Modifier.height(20.dp))
        Text(headline, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        if (mode is QuizMode.Exam && s.passed) {
            Certificate(course, mode.code, s.percent, store)
            Spacer(Modifier.height(24.dp))
        } else if (scored && mode != QuizMode.Mistakes && mode != QuizMode.Spelling) {
            SkillBars(s.questions, s.writing, s.speaking)
            Spacer(Modifier.height(28.dp))
        }
        val big = Modifier.fillMaxWidth().height(52.dp)
        when {
            mode is QuizMode.LessonQuiz && s.passed && next != null -> {
                Button(onClick = { onNext(next.n) }, modifier = big, shape = MaterialTheme.shapes.medium) { Text("الدرس التالي") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onRetry, modifier = big, shape = MaterialTheme.shapes.medium) { Text("أعد الاختبار") }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onClose) { Text("ارجع إلى الدرس") }
            }
            !good -> {
                Button(onClick = onRetry, modifier = big, shape = MaterialTheme.shapes.medium) { Text("أعد الاختبار") }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onClose) { Text("رجوع") }
            }
            else -> Button(onClick = onClose, modifier = big, shape = MaterialTheme.shapes.medium) { Text("تم") }
        }
    }
}

/** A simple completion certificate shown after passing a level exam. */
@Composable
fun Certificate(course: Course, code: String, percent: Int, store: ProgressStore) {
    val state by store.state.collectAsState()
    val extra = LocalExtra.current
    var name by remember { mutableStateOf(state.name) }
    Surface(
        shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(2.dp, extra.level(code)), modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🏅", fontSize = 48.sp)
            Text("شهادة إنجاز", style = MaterialTheme.typography.headlineSmall, color = extra.level(code))
            Spacer(Modifier.height(8.dp))
            Text("تشهد هذه البطاقة أن", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.name.isBlank()) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, singleLine = true,
                    placeholder = { Text("اكتب اسمك") }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
                TextButton(onClick = { store.setName(name.trim()) }, enabled = name.isNotBlank()) { Text("احفظ الاسم") }
            } else {
                Text(state.name, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            }
            Text(
                "أتمّ المستوى $code (${course.level(code)?.name.orEmpty()}) بنتيجة $percent%",
                style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(java.time.LocalDate.now().toString(), style = Numeric, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The small chart used on the result and records screens: accuracy per skill. */
@Composable
fun SkillBars(questions: SkillScore, writing: SkillScore, speaking: SkillScore, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf("❓ الأسئلة" to questions, "✍️ الكتابة" to writing, "🎤 النطق" to speaking).forEach { (label, s) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.width(96.dp), style = MaterialTheme.typography.titleSmall)
                ProgressLine(
                    if (s.total == 0) 0f else s.correct.toFloat() / s.total,
                    MaterialTheme.colorScheme.primary, Modifier.weight(1f), height = 10.dp,
                )
                Text(
                    if (s.total == 0) "—" else "${s.correct}/${s.total}",
                    Modifier.width(52.dp), style = Numeric, fontSize = 14.sp, textAlign = TextAlign.End,
                )
            }
        }
    }
}

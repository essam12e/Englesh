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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.engliziha.app.data.Dictionary
import com.engliziha.app.data.Example
import com.engliziha.app.data.ProgressState
import com.engliziha.app.data.ProgressStore
import com.engliziha.app.data.WordCheck
import com.engliziha.app.quiz.sameText
import com.engliziha.app.speech.Speaker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Write what you know": the learner types every English word they know, and each one is checked.
 * Misspelled words show what was wrong and the right spelling; correct ones give a sentence to complete.
 */
@Composable
fun WritingScreen(state: ProgressState, store: ProgressStore, dictionary: () -> Dictionary, onBack: () -> Unit, onPractice: () -> Unit) {
    val context = LocalContext.current
    val speaker = remember { Speaker(context) }
    DisposableEffect(Unit) { onDispose { speaker.release() } }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current

    var text by rememberSaveable { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<WordCheck>?>(null) }

    fun check() {
        if (text.isBlank() || checking) return
        focus.clearFocus()
        checking = true
        scope.launch {
            val found = withContext(Dispatchers.Default) { dictionary().check(text) }
            store.recordWriting(
                correct = found.filterIsInstance<WordCheck.Correct>().map { it.typed },
                misspelled = found.filterIsInstance<WordCheck.Wrong>().mapNotNull { it.correct },
            )
            results = found
            checking = false
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        TopBar("اكتب ما تعرف", onBack)
        LazyColumn(
            Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text("اكتب كل الكلمات الإنجليزية التي تعرفها، وافصل بينها بمسافة أو سطر جديد، ثم اضغط «افحص».",
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Ltr {
                    OutlinedTextField(
                        value = text, onValueChange = { text = it; results = null },
                        textStyle = MaterialTheme.typography.titleLarge.copy(textDirection = TextDirection.Ltr, fontWeight = FontWeight.Medium),
                        placeholder = { Text("cat  dog  apple  school ...") },
                        // No suggestions or auto-correct: the spelling must be the learner's own.
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Password,
                        ),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 130.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Button(onClick = { check() }, enabled = text.isNotBlank() && !checking,
                    modifier = Modifier.fillMaxWidth().height(52.dp), shape = MaterialTheme.shapes.medium) {
                    Text(if (checking) "جارٍ الفحص…" else "افحص كلماتي")
                }
                Spacer(Modifier.height(6.dp))
                Text("رصيدك: ${state.knownWords.size} كلمة كتبتها صحيحة حتى الآن", style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary)
            }
            val found = results
            if (found != null) {
                val wrong = found.filterIsInstance<WordCheck.Wrong>()
                val right = found.filterIsInstance<WordCheck.Correct>()
                item {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (found.isEmpty()) "لم أجد كلمات إنجليزية في ما كتبت."
                        else "✓ ${right.size} صحيحة   ✗ ${wrong.size} تحتاج تصحيحاً",
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                items(wrong, key = { "w" + it.typed }) { WrongCard(it, speaker) }
                if (right.isNotEmpty()) item {
                    Text("كلماتك الصحيحة — أكمل الجملة بكل كلمة لتعرف أين تُستخدم", style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp))
                }
                items(right, key = { "c" + it.typed }) { CorrectCard(it, speaker) }
            }
            if (state.spelling.isNotEmpty()) item {
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = onPractice, modifier = Modifier.fillMaxWidth().height(52.dp), shape = MaterialTheme.shapes.medium) {
                    Text("🔁 تدرّب على أخطائك الإملائية (${state.spelling.size})")
                }
                Text("تسمع الكلمة وتكتبها حتى تتقن إملاءها.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun WrongCard(check: WordCheck.Wrong, speaker: Speaker) {
    val error = MaterialTheme.colorScheme.error
    val success = LocalExtra.current.success
    Surface(
        shape = MaterialTheme.shapes.medium, color = error.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, error.copy(alpha = 0.4f)), modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Ltr {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(check.typed, style = MaterialTheme.typography.titleLarge.copy(textDecoration = TextDecoration.LineThrough), color = error)
                    val fix = check.correct
                    if (fix != null) {
                        Text("  →  ", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val changed = Dictionary.fixedLetters(check.typed, fix)
                        // The letters the learner got wrong are bold and underlined in the correct word.
                        Text(
                            buildAnnotatedString {
                                fix.forEachIndexed { i, c ->
                                    if (changed[i]) withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, textDecoration = TextDecoration.Underline)) { append(c) }
                                    else append(c)
                                }
                            },
                            style = MaterialTheme.typography.titleLarge, color = success, modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { speaker.say(fix) }) { Text("🔊") }
                    }
                }
            }
            Text(check.reason, style = MaterialTheme.typography.bodyLarge)
            check.example?.let {
                Text("المعنى: ${it.arabic}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CorrectCard(check: WordCheck.Correct, speaker: Speaker) {
    val success = LocalExtra.current.success
    Surface(
        shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("✓", style = MaterialTheme.typography.titleLarge, color = success)
                Spacer(Modifier.width(8.dp))
                Text(check.example?.arabic ?: "كلمة صحيحة", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                English(check.typed, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                TextButton(onClick = { speaker.say(check.typed) }) { Text("🔊") }
            }
            val example = check.example
            val blanked = example?.let { Dictionary.blank(it.sentence, check.typed) }
            if (example != null && blanked != null) Cloze(check.typed, example, blanked, speaker)
            else Text("لا توجد جملة مثال لهذه الكلمة بعد.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A sentence with the word missing; the learner types the word to complete it. */
@Composable
private fun Cloze(word: String, example: Example, blanked: String, speaker: Speaker) {
    val success = LocalExtra.current.success
    var answer by remember { mutableStateOf("") }
    var solved by remember { mutableStateOf(false) }
    var tried by remember { mutableStateOf(false) }
    val submit = {
        if (answer.isNotBlank()) {
            tried = true
            if (sameText(answer, word)) { solved = true; speaker.say(example.sentence) }
        }
    }
    Spacer(Modifier.height(6.dp))
    English(if (solved) example.sentence else blanked, Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.titleMedium, color = if (solved) success else MaterialTheme.colorScheme.onSurface)
    Text(example.sentenceArabic, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (solved) {
        TextButton(onClick = { speaker.say(example.sentence) }) { Text("🔊 استمع للجملة") }
    } else {
        Spacer(Modifier.height(6.dp))
        Ltr {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = answer, onValueChange = { answer = it; tried = false }, singleLine = true,
                    isError = tried,
                    textStyle = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Ltr),
                    placeholder = { Text("_____", fontSize = 14.sp) },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Password, imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = submit, enabled = answer.isNotBlank()) { Text("تحقق") }
            }
        }
        if (tried) Text("ليست هذه الكلمة — اكتب الكلمة نفسها التي في أعلى البطاقة.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error)
    }
}

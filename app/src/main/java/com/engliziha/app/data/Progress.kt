package com.engliziha.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val PASS_PERCENT = 70

@Serializable
data class LessonProgress(
    val pos: Float = 0f,        // where the learner stopped, in seconds
    val watched: Float = 0f,    // furthest point reached, 0..1
    val best: Int = -1,         // best quiz score in percent, -1 = never taken
) {
    val passed: Boolean get() = best >= PASS_PERCENT
}

@Serializable
data class SkillScore(val correct: Int = 0, val total: Int = 0)

@Serializable
data class Attempt(
    val lesson: String,
    val title: String,
    val level: String,
    val time: Long,
    val correct: Int,
    val total: Int,
    val questions: SkillScore = SkillScore(),
    val writing: SkillScore = SkillScore(),
    val speaking: SkillScore = SkillScore(),
) {
    val percent: Int get() = if (total == 0) 0 else correct * 100 / total
    val passed: Boolean get() = percent >= PASS_PERCENT
}

/** Spaced-review state of one word: which box it is in and the day it is due again. */
@Serializable
data class WordState(val box: Int = 0, val due: Long = 0)

@Serializable
data class ProgressState(
    val lessons: Map<String, LessonProgress> = emptyMap(),
    val attempts: List<Attempt> = emptyList(),
    val last: String? = null,
    val words: Map<String, WordState> = emptyMap(),
    val days: List<Long> = emptyList(),   // days (epoch day) on which the learner studied
) {
    /** Consecutive study days ending today or yesterday. */
    fun streak(today: Long = today()): Int {
        val set = days.toSet()
        var day = if (today in set) today else today - 1
        var count = 0
        while (day in set) { count++; day-- }
        return count
    }
}

fun today(): Long = java.time.LocalDate.now().toEpochDay()

/** Days to wait after a correct answer, by box. */
private val INTERVALS = longArrayOf(1, 3, 7, 16, 35)

data class ReviewCard(val key: String, val word: Vocab, val lesson: Lesson)

/** Words from lessons already passed that are due for review today, oldest first. */
fun Course.dueWords(state: ProgressState, today: Long = today()): List<ReviewCard> =
    all.filter { state.lessons[it.n]?.passed == true }.flatMap { lesson ->
        lesson.vocab.map { ReviewCard("${lesson.n}|${it.en}", it, lesson) }
    }.filter { (state.words[it.key]?.due ?: 0) <= today }.sortedBy { state.words[it.key]?.due ?: 0 }

/** Everything the learner has done, kept on the device only. */
class ProgressStore(context: Context) {
    private val prefs = context.getSharedPreferences("progress", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val _state = MutableStateFlow(read())
    val state: StateFlow<ProgressState> = _state

    private fun read(): ProgressState = try {
        prefs.getString("state", null)?.let { json.decodeFromString<ProgressState>(it) } ?: ProgressState()
    } catch (e: Exception) {
        ProgressState()
    }

    private fun update(change: (ProgressState) -> ProgressState) {
        val next = change(_state.value)
        _state.value = next
        prefs.edit().putString("state", json.encodeToString(next)).apply()
    }

    fun savePosition(lesson: String, seconds: Float, duration: Float) = update { s ->
        val old = s.lessons[lesson] ?: LessonProgress()
        val fraction = if (duration > 0f) (seconds / duration).coerceIn(0f, 1f) else 0f
        s.copy(
            lessons = s.lessons + (lesson to old.copy(pos = seconds, watched = maxOf(old.watched, fraction))),
            last = lesson,
        )
    }

    fun markOpened(lesson: String) = update { it.copy(last = lesson) }

    fun reviewWord(key: String, known: Boolean) = update { s ->
        val old = s.words[key] ?: WordState()
        val box = if (known) (old.box + 1).coerceAtMost(INTERVALS.size) else 0
        val wait = if (known) INTERVALS[box - 1] else 1
        s.copy(words = s.words + (key to WordState(box, today() + wait)), days = (s.days + today()).distinct())
    }

    fun addAttempt(attempt: Attempt) = update { s ->
        val old = s.lessons[attempt.lesson] ?: LessonProgress()
        s.copy(
            lessons = s.lessons + (attempt.lesson to old.copy(best = maxOf(old.best, attempt.percent))),
            attempts = s.attempts + attempt,
            days = (s.days + today()).distinct(),
        )
    }
}

/** A lesson opens once the one before it has been passed. */
fun Course.isUnlocked(n: String, state: ProgressState): Boolean {
    val prev = previous(n) ?: return true
    return state.lessons[prev.n]?.passed == true
}

fun Course.currentLesson(state: ProgressState): Lesson? =
    all.firstOrNull { state.lessons[it.n]?.passed != true } ?: all.lastOrNull()

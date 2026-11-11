package com.engliziha.app.quiz

import com.engliziha.app.data.Course
import com.engliziha.app.data.Lesson
import com.engliziha.app.data.McqItem
import com.engliziha.app.data.Mistake
import com.engliziha.app.data.Phrase
import com.engliziha.app.data.ProgressState
import com.engliziha.app.data.Vocab
import com.engliziha.app.data.dailySentences
import kotlin.random.Random

enum class Skill { QUESTIONS, WRITING, SPEAKING }

sealed class Question(val skill: Skill) {
    /** Hand-written question about the lesson's rules or meaning. */
    data class Choice(val prompt: String, val options: List<String>, val answer: Int, val why: String) :
        Question(Skill.QUESTIONS)

    /** A picture (emoji) and four English words. */
    data class Picture(val emoji: String, val arabic: String, val options: List<String>, val answer: Int) :
        Question(Skill.QUESTIONS)

    /** Put the shuffled words of a sentence back in order. */
    data class Order(val arabic: String, val words: List<String>, val shuffled: List<String>) :
        Question(Skill.QUESTIONS)

    /** Hear a word and type it. */
    data class Dictation(val text: String, val arabic: String) : Question(Skill.WRITING)

    /** See the picture and the Arabic meaning, type the English word or sentence. */
    data class Translate(val emoji: String, val arabic: String, val text: String) : Question(Skill.WRITING)

    /** Say the text aloud; speech recognition checks it. */
    data class Speak(val text: String, val arabic: String) : Question(Skill.SPEAKING)

    /** Not a question: a sentence to study before being asked about it. Never scored. */
    data class Study(val label: String, val text: String, val arabic: String) : Question(Skill.QUESTIONS)
}

/** A question together with where it came from, so a wrong answer can be reviewed later. */
data class QuizItem(val question: Question, val lesson: String, val mistake: Mistake? = null)

fun normalize(s: String): String =
    s.lowercase().replace("’", "'").replace(Regex("[^a-z0-9' ]"), " ").trim().replace(Regex("\\s+"), " ")

fun sameText(a: String, b: String): Boolean = normalize(a) == normalize(b)

/** For each expected word, whether it was heard (each heard word is used once). */
fun wordsHeard(expected: String, heard: String): List<Boolean> {
    val got = normalize(heard).split(" ").filter { it.isNotEmpty() }.toMutableList()
    return normalize(expected).split(" ").filter { it.isNotEmpty() }.map { got.remove(it) }
}

/** Share of the expected words that were heard. */
fun speechScore(expected: String, heard: String): Float {
    val hits = wordsHeard(expected, heard)
    return if (hits.isEmpty()) 0f else hits.count { it }.toFloat() / hits.size
}

const val SPEECH_PASS = 0.7f

private fun typeable(en: String) = en.length <= 14 && en.all { it.isLetter() || it == ' ' || it == '\'' }

private fun choice(lesson: Lesson, m: McqItem, random: Random): QuizItem {
    val order = m.o.indices.shuffled(random)
    return QuizItem(
        Question.Choice(m.q, order.map { m.o[it] }, order.indexOf(m.a), m.why),
        lesson.n, Mistake(lesson.n, "choice", m.q + "\n" + m.o[m.a]),
    )
}

private fun picture(lesson: Lesson, v: Vocab, random: Random): QuizItem {
    val wrong = lesson.vocab.filter { it.en != v.en }.shuffled(random).take(3).map { it.en }
    val options = (wrong + v.en).shuffled(random)
    return QuizItem(Question.Picture(v.emoji, v.ar, options, options.indexOf(v.en)), lesson.n, Mistake(lesson.n, "word", v.en))
}

private fun order(lesson: Lesson, p: Phrase, random: Random): QuizItem {
    val words = p.en.split(" ").filter { it.isNotBlank() }
    var shuffled = words.shuffled(random)
    if (shuffled == words && words.size > 1) shuffled = words.reversed()
    return QuizItem(Question.Order(p.ar, words, shuffled), lesson.n, Mistake(lesson.n, "phrase", p.en))
}

private fun translate(lesson: Lesson, v: Vocab) =
    QuizItem(Question.Translate(v.emoji, v.ar, v.en), lesson.n, Mistake(lesson.n, "word", v.en))

/** The quiz shown after a lesson: questions, writing and pronunciation. */
fun buildItems(lesson: Lesson, random: Random = Random.Default): List<QuizItem> {
    val out = mutableListOf<QuizItem>()
    val vocab = lesson.vocab.shuffled(random)
    val canType = vocab.filter { typeable(it.en) }
    val phrases = lesson.phrases.shuffled(random)

    lesson.quiz.shuffled(random).take(4).forEach { out += choice(lesson, it, random) }
    vocab.take(2).forEach { out += picture(lesson, it, random) }
    phrases.firstOrNull()?.let { out += order(lesson, it, random) }
    canType.getOrNull(2)?.let { out += QuizItem(Question.Dictation(it.en, it.ar), lesson.n, Mistake(lesson.n, "word", it.en)) }
    canType.getOrNull(3)?.let { out += translate(lesson, it) }
    vocab.getOrNull(4)?.let { out += QuizItem(Question.Speak(it.en, it.ar), lesson.n, Mistake(lesson.n, "word", it.en)) }
    phrases.getOrNull(1)?.let { out += QuizItem(Question.Speak(it.en, it.ar), lesson.n, Mistake(lesson.n, "phrase", it.en)) }
    return out
}

fun buildQuiz(lesson: Lesson, random: Random = Random.Default): List<Question> = buildItems(lesson, random).map { it.question }

/** Turns stored mistakes back into questions; ones that no longer match the content are dropped. */
fun mistakeItems(course: Course, mistakes: List<Mistake>, random: Random = Random.Default): List<QuizItem> =
    mistakes.shuffled(random).mapNotNull { m ->
        val lesson = course.lesson(m.lesson) ?: return@mapNotNull null
        when (m.kind) {
            "choice" -> lesson.quiz.firstOrNull { it.q + "\n" + it.o[it.a] == m.text }?.let { choice(lesson, it, random) }
            "word" -> lesson.vocab.firstOrNull { it.en == m.text }?.let {
                if (typeable(it.en)) translate(lesson, it) else picture(lesson, it, random)
            }
            "phrase" -> lesson.phrases.firstOrNull { it.en == m.text }?.let { order(lesson, it, random) }
            else -> null
        }
    }.take(10)

const val PLACEMENT_PER_LEVEL = 5
const val PLACEMENT_PASS = 4

/** Five questions from each level below C1, easiest level first. */
fun placementItems(course: Course, random: Random = Random.Default): List<QuizItem> =
    course.levels.filter { it.code != "C1" }.flatMap { level ->
        level.lessons.shuffled(random).take(PLACEMENT_PER_LEVEL).mapNotNull { l ->
            l.quiz.shuffled(random).firstOrNull()?.let { choice(l, it, random) }
        }
    }

/** The level to start from: the first one where fewer than four of five answers were right. */
fun placementLevel(course: Course, items: List<QuizItem>, results: List<Boolean?>): String {
    for (level in course.levels) {
        val mine = items.indices.filter { course.lesson(items[it].lesson)?.level == level.code }
        if (mine.isEmpty()) return level.code
        if (mine.count { results[it] == true } < PLACEMENT_PASS) return level.code
    }
    return course.levels.last().code
}

/** A 20-question exam drawn from every lesson of one level. */
fun examItems(course: Course, code: String, random: Random = Random.Default): List<QuizItem> {
    val lessons = course.level(code)?.lessons.orEmpty().shuffled(random)
    val out = mutableListOf<QuizItem>()
    // Go round the lessons more than once if the level is small (C1 has five lessons).
    var round = 0
    while (out.size < 14 && round < 4) {
        lessons.forEach { l -> if (out.size < 14) l.quiz.shuffled(random).getOrNull(round)?.let { out += choice(l, it, random) } }
        round++
    }
    lessons.take(3).forEach { l -> l.vocab.shuffled(random).firstOrNull { typeable(it.en) }?.let { out += translate(l, it) } }
    lessons.takeLast(3).forEach { l -> l.phrases.shuffled(random).firstOrNull()?.let { out += order(l, it, random) } }
    return out
}

private val SLOT_NAMES = listOf("جملة الصباح", "جملة العصر", "جملة المساء")
fun slotName(slot: Int): String = SLOT_NAMES.getOrElse(slot) { "جملة اليوم" }

/** Study one of today's sentences, then write it and say it. */
fun dailyLearnItems(course: Course, state: ProgressState, slot: Int): List<QuizItem> {
    val s = course.dailySentences(state).getOrNull(slot) ?: return emptyList()
    return listOf(
        QuizItem(Question.Study(slotName(slot), s.phrase.en, s.phrase.ar), s.lesson.n),
        QuizItem(Question.Translate("📝", s.phrase.ar, s.phrase.en), s.lesson.n),
        QuizItem(Question.Speak(s.phrase.en, s.phrase.ar), s.lesson.n),
    )
}

/** The evening test: write and say all three sentences of the day. */
fun dailyTestItems(course: Course, state: ProgressState): List<QuizItem> =
    course.dailySentences(state).flatMap { s ->
        listOf(
            QuizItem(Question.Translate("📝", s.phrase.ar, s.phrase.en), s.lesson.n),
            QuizItem(Question.Speak(s.phrase.en, s.phrase.ar), s.lesson.n),
        )
    }

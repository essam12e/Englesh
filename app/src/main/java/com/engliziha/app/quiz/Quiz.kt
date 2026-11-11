package com.engliziha.app.quiz

import com.engliziha.app.data.Lesson
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

    /** See the picture and the Arabic meaning, type the English word. */
    data class Translate(val emoji: String, val arabic: String, val text: String) : Question(Skill.WRITING)

    /** Say the text aloud; speech recognition checks it. */
    data class Speak(val text: String, val arabic: String) : Question(Skill.SPEAKING)
}

fun normalize(s: String): String =
    s.lowercase().replace("’", "'").replace(Regex("[^a-z0-9' ]"), " ").trim().replace(Regex("\\s+"), " ")

fun sameText(a: String, b: String): Boolean = normalize(a) == normalize(b)

/** Share of the expected words that were heard, in order of appearance not required. */
fun speechScore(expected: String, heard: String): Float {
    val want = normalize(expected).split(" ").filter { it.isNotEmpty() }
    val got = normalize(heard).split(" ").filter { it.isNotEmpty() }.toMutableList()
    if (want.isEmpty()) return 0f
    var hit = 0
    for (w in want) if (got.remove(w)) hit++
    return hit.toFloat() / want.size
}

const val SPEECH_PASS = 0.7f

fun buildQuiz(lesson: Lesson, random: Random = Random.Default): List<Question> {
    val out = mutableListOf<Question>()
    val vocab = lesson.vocab.shuffled(random)
    val typeable = vocab.filter { it.en.length <= 14 && it.en.all { c -> c.isLetter() || c == ' ' || c == '\'' } }
    val phrases = lesson.phrases.shuffled(random)

    lesson.quiz.shuffled(random).take(4).forEach { m ->
        val order = m.o.indices.shuffled(random)
        out += Question.Choice(m.q, order.map { m.o[it] }, order.indexOf(m.a), m.why)
    }
    vocab.take(2).forEach { v ->
        val wrong = lesson.vocab.filter { it.en != v.en }.shuffled(random).take(3).map { it.en }
        val options = (wrong + v.en).shuffled(random)
        out += Question.Picture(v.emoji, v.ar, options, options.indexOf(v.en))
    }
    phrases.firstOrNull()?.let { p ->
        val words = p.en.split(" ").filter { it.isNotBlank() }
        var shuffled = words.shuffled(random)
        if (shuffled == words && words.size > 1) shuffled = words.reversed()
        out += Question.Order(p.ar, words, shuffled)
    }
    typeable.getOrNull(2)?.let { out += Question.Dictation(it.en, it.ar) }
    typeable.getOrNull(3)?.let { out += Question.Translate(it.emoji, it.ar, it.en) }
    vocab.getOrNull(4)?.let { out += Question.Speak(it.en, it.ar) }
    phrases.getOrNull(1)?.let { out += Question.Speak(it.en, it.ar) }
    return out
}

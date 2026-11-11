package com.engliziha.app

import com.engliziha.app.data.Lesson
import com.engliziha.app.quiz.Question
import com.engliziha.app.quiz.SPEECH_PASS
import com.engliziha.app.quiz.buildQuiz
import com.engliziha.app.quiz.sameText
import com.engliziha.app.quiz.speechScore
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

class QuizTest {
    private val json = Json { ignoreUnknownKeys = true }
    private fun lessons(): List<Lesson> = listOf("A1", "A2", "B1", "B2", "C1").flatMap {
        json.decodeFromString<List<Lesson>>(File("src/main/assets/content/$it.json").readText())
    }

    @Test
    fun typedAnswersIgnoreCaseAndPunctuation() {
        assertTrue(sameText(" Apple ", "apple"))
        assertTrue(sameText("don’t", "don't"))
        assertFalse(sameText("aple", "apple"))
    }

    @Test
    fun speechIsScoredByWordsHeard() {
        assertEquals(1f, speechScore("I am a student.", "i am a student"))
        assertTrue(speechScore("I am a student.", "I am student") >= SPEECH_PASS)
        assertTrue(speechScore("I am a student.", "hello there") < SPEECH_PASS)
    }

    @Test
    fun everyLessonLoadsAndProducesAFullQuiz() {
        val all = lessons()
        assertEquals(195, all.size)
        assertEquals(all.size, all.map { it.n }.toSet().size)
        for (lesson in all) {
            val quiz = buildQuiz(lesson, Random(7))
            assertTrue("lesson ${lesson.n} quiz too short: ${quiz.size}", quiz.size >= 9)
            assertTrue("lesson ${lesson.n} has no speaking", quiz.any { it is Question.Speak })
            assertTrue("lesson ${lesson.n} has no writing", quiz.any { it is Question.Dictation || it is Question.Translate })
            quiz.filterIsInstance<Question.Choice>().forEach { q ->
                val source = lesson.quiz.first { it.q == q.prompt && it.o.toSet() == q.options.toSet() }
                assertEquals(source.o[source.a], q.options[q.answer])
            }
            quiz.filterIsInstance<Question.Picture>().forEach { q ->
                assertEquals(4, q.options.toSet().size)
                assertEquals(lesson.vocab.first { it.emoji == q.emoji && it.ar == q.arabic }.en, q.options[q.answer])
            }
            quiz.filterIsInstance<Question.Order>().forEach { q -> assertEquals(q.words.sorted(), q.shuffled.sorted()) }
        }
    }
}

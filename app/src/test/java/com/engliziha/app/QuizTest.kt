package com.engliziha.app

import com.engliziha.app.data.Lesson
import com.engliziha.app.data.currentLesson
import com.engliziha.app.data.dailySentences
import com.engliziha.app.data.isUnlocked
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

class StreakTest {
    @Test
    fun streakCountsConsecutiveDaysUpToTodayOrYesterday() {
        val s = com.engliziha.app.data.ProgressState(days = listOf(10, 11, 12, 14))
        assertEquals(1, s.streak(today = 14))
        assertEquals(3, s.streak(today = 12))
        assertEquals(3, s.streak(today = 13))
        assertEquals(0, s.streak(today = 16))
    }
}

class FeaturesTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val course = com.engliziha.app.data.Course(
        listOf("A1", "A2", "B1", "B2", "C1").map { code ->
            com.engliziha.app.data.Level(code, code, "", json.decodeFromString<List<Lesson>>(File("src/main/assets/content/$code.json").readText()))
        }
    )
    private val empty = com.engliziha.app.data.ProgressState()

    @Test
    fun placementStartsAtTheFirstLevelNotMastered() {
        val items = com.engliziha.app.quiz.placementItems(course, Random(1))
        assertEquals(20, items.size)
        fun place(right: Set<String>) = com.engliziha.app.quiz.placementLevel(
            course, items, items.map { course.lesson(it.lesson)!!.level in right },
        )
        assertEquals("A1", place(emptySet()))
        assertEquals("B1", place(setOf("A1", "A2")))
        assertEquals("A2", place(setOf("A1", "B1")))
        assertEquals("C1", place(setOf("A1", "A2", "B1", "B2")))
    }

    @Test
    fun placementOpensEarlierLevelsAndTheFirstLessonOfTheStartLevel() {
        with(com.engliziha.app.data.ProgressState(startLevel = "B1")) {
            assertTrue(course.isUnlocked("30", this))
            assertTrue(course.isUnlocked("88", this))
            assertFalse(course.isUnlocked("89", this))
            assertEquals("88", course.currentLesson(this)?.n)
        }
        assertTrue(course.isUnlocked("1", empty))
        assertFalse(course.isUnlocked("2", empty))
        assertFalse(course.isUnlocked("50", empty))
    }

    @Test
    fun everyKindOfMistakeComesBackAsAQuestion() {
        val lesson = course.lesson("23")!!
        val mistakes = com.engliziha.app.quiz.buildItems(lesson, Random(3)).mapNotNull { it.mistake }.distinct().take(10)
        assertEquals(mistakes.size, com.engliziha.app.quiz.mistakeItems(course, mistakes, Random(3)).size)
    }

    @Test
    fun levelExamsHaveTwentyQuestions() {
        for (level in course.levels) {
            assertEquals("exam ${level.code}", 20, com.engliziha.app.quiz.examItems(course, level.code, Random(5)).size)
        }
    }

    @Test
    fun threeDailySentencesThatStayTheSameAllDay() {
        val a = course.dailySentences(empty, 20000).map { it.phrase.en }
        assertEquals(3, a.size)
        assertEquals(3, a.toSet().size)
        assertEquals(a, course.dailySentences(empty, 20000).map { it.phrase.en })
        assertEquals(6, com.engliziha.app.quiz.dailyTestItems(course, empty).size)
    }
}

class DictionaryTest {
    private val words = File("src/main/assets/dict/words.tsv").readLines().filter { it.isNotBlank() }
        .associate { it.substringBefore('\t') to it.substringAfter('\t').trim().toInt() }
    private val dict = com.engliziha.app.data.Dictionary(
        words, mapOf("apple" to com.engliziha.app.data.Example("تفاحة", "I eat an apple every day.", "آكل تفاحة كل يوم.")),
    )

    @Test
    fun misspelledWordsGetTheClosestCommonWord() {
        assertEquals("apple", dict.suggest("aple"))
        assertEquals("friend", dict.suggest("freind"))
        assertEquals("school", dict.suggest("scool"))
        assertEquals("because", dict.suggest("becuase"))
        assertEquals("beautiful", dict.suggest("beutiful"))
        assertEquals(null, dict.suggest("qqqqzzzz"))
    }

    @Test
    fun checkSplitsTextAndSeparatesRightFromWrong() {
        val result = dict.check("Apple, dog\naple  DOG teacher")
        assertEquals(listOf("apple", "dog", "aple", "teacher"), result.map { it.typed })
        assertTrue(result[0] is com.engliziha.app.data.WordCheck.Correct)
        val wrong = result[2] as com.engliziha.app.data.WordCheck.Wrong
        assertEquals("apple", wrong.correct)
        assertEquals("تفاحة", wrong.example?.arabic)
    }

    @Test
    fun mistakesAreExplained() {
        val d = com.engliziha.app.data.Dictionary
        assertTrue(d.explain("aple", "apple").startsWith("حرف ناقص"))
        assertTrue(d.explain("appple", "apple").startsWith("حرف زائد"))
        assertTrue(d.explain("freind", "friend").startsWith("حرفان متبادلان"))
        assertTrue(d.explain("cet", "cat").startsWith("حرف خاطئ"))
        assertTrue(d.explain("beutifl", "beautiful").startsWith("أكثر من خطأ"))
        assertEquals(listOf(false, false, true, false, false), d.fixedLetters("aple", "apple").toList())
    }

    @Test
    fun theWordIsBlankedOnlyAsAWholeWord() {
        val d = com.engliziha.app.data.Dictionary
        assertEquals("I eat an _____ every day.", d.blank("I eat an apple every day.", "apple"))
        assertEquals("_____ am a student.", d.blank("I am a student.", "i"))
        assertEquals(null, d.blank("I like pineapples.", "apple"))
    }
}

class StoriesTest {
    @Test
    fun everyStoryPageHasItsNarration() {
        val stories = Json { ignoreUnknownKeys = true }
            .decodeFromString<List<com.engliziha.app.data.Story>>(File("src/main/assets/stories/stories.json").readText())
        assertEquals(41, stories.size)
        for (s in stories) {
            assertEquals(s.title, s.pages.first().en)
            s.pages.indices.forEach { assertTrue("${s.id} page $it", File("src/main/assets/" + s.audio(it)).exists()) }
            assertFalse("${s.id} has an extra track", File("src/main/assets/" + s.audio(s.pages.size)).exists())
            assertTrue(s.pages.all { it.en.isNotBlank() && it.ar.isNotBlank() })
            s.quiz.forEach { assertTrue(it.a in 0..3) }
        }
    }
}

class LocaleTest {
    @Test
    fun audioPathsUseLatinDigitsEvenWithAnArabicLocale() {
        val saved = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ar-SA-u-nu-arab"))
            val story = Json { ignoreUnknownKeys = true }
                .decodeFromString<List<com.engliziha.app.data.Story>>(File("src/main/assets/stories/stories.json").readText()).first()
            assertEquals("stories/audio/${story.id}/01.mp3", story.audio(0))
            assertEquals("1:05", com.engliziha.app.ui.formatTime(65f))
        } finally {
            java.util.Locale.setDefault(saved)
        }
    }
}

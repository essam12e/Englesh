package com.engliziha.app.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Note(val h: String, val t: String)

@Serializable
data class Alert(val k: String, val t: String)

@Serializable
data class Vocab(val en: String, val ar: String, val emoji: String)

@Serializable
data class Phrase(val en: String, val ar: String)

@Serializable
data class McqItem(val q: String, val o: List<String>, val a: Int, val why: String)

@Serializable
data class Lesson(
    val n: String,
    val id: String,
    val level: String,
    val sec: Int = 0,
    val type: String = "vocab",
    val title_ar: String,
    val title_en: String,
    val summary: String,
    val notes: List<Note> = emptyList(),
    val alerts: List<Alert> = emptyList(),
    val vocab: List<Vocab> = emptyList(),
    val phrases: List<Phrase> = emptyList(),
    val quiz: List<McqItem> = emptyList(),
) {
    /** "9a" -> "9 (أ)" so split lessons read naturally. */
    val label: String
        get() = when {
            n.endsWith("a") -> n.dropLast(1) + " (١)"
            n.endsWith("b") -> n.dropLast(1) + " (٢)"
            else -> n
        }
}

data class Level(val code: String, val name: String, val blurb: String, val lessons: List<Lesson>)

class Course(val levels: List<Level>) {
    val all: List<Lesson> = levels.flatMap { it.lessons }
    private val byN = all.associateBy { it.n }
    fun lesson(n: String): Lesson? = byN[n]
    fun level(code: String): Level? = levels.firstOrNull { it.code == code }
    fun next(n: String): Lesson? = all.getOrNull(all.indexOfFirst { it.n == n } + 1)
    fun previous(n: String): Lesson? = all.getOrNull(all.indexOfFirst { it.n == n } - 1)

    companion object {
        private val META = listOf(
            Triple("A1", "المبتدئ", "الأساسيات: التحيات، الأرقام، فعل يكون، المضارع البسيط"),
            Triple("A2", "الأساسي", "الماضي والمستقبل، الوصف، المقارنة، مواقف يومية"),
            Triple("B1", "المتوسط", "المضارع المستمر والتام، التعبير عن الرأي والخطط"),
            Triple("B2", "فوق المتوسط", "الكلام المنقول، المبني للمجهول، مواضيع نقاش وكتابة"),
            Triple("C1", "المتقدم", "الإنجليزية المهنية: المقابلات، المراسلات، التفاوض"),
        )
        private val json = Json { ignoreUnknownKeys = true }

        fun load(context: Context): Course = Course(META.map { (code, name, blurb) ->
            val lessons = try {
                context.assets.open("content/$code.json").bufferedReader().use {
                    json.decodeFromString<List<Lesson>>(it.readText())
                }
            } catch (e: Exception) {
                emptyList()
            }
            Level(code, name, blurb, lessons)
        })
    }
}

package com.engliziha.app.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class StoryPage(val en: String, val ar: String)

@Serializable
data class StoryWord(val en: String, val ar: String)

/** A short story from the African Storybook Project, narrated page by page by a real person. */
@Serializable
data class Story(
    val id: String,
    val title: String,
    val level: Int,
    val license: String,
    val text_by: String = "",
    val illustration_by: String = "",
    val translation_by: String = "",
    val title_ar: String,
    val emoji: String,
    val summary: String,
    val pages: List<StoryPage>,
    val vocab: List<StoryWord> = emptyList(),
    val quiz: List<McqItem> = emptyList(),
) {
    /** Asset path of the narration for one page (pages are numbered from 01). */
    // Locale.ROOT: on phones set to Arabic digits, the default locale would write "٠١.mp3".
    fun audio(page: Int): String = String.format(java.util.Locale.ROOT, "stories/audio/%s/%02d.mp3", id, page + 1)

    val credits: String
        get() = buildString {
            append("\"$title\"")
            if (text_by.isNotBlank()) append(" — text: $text_by")
            if (illustration_by.isNotBlank()) append(", illustration: $illustration_by")
            append(". African Storybook Project, audio: Global African Storybook Project (global-asp). License: $license.")
        }

    companion object {
        val LEVEL_NAMES = mapOf(1 to "قصص قصيرة جداً", 2 to "قصص قصيرة", 3 to "قصص أطول")
        private val json = Json { ignoreUnknownKeys = true }

        fun load(context: Context): List<Story> = try {
            context.assets.open("stories/stories.json").bufferedReader().use { json.decodeFromString<List<Story>>(it.readText()) }
        } catch (e: Exception) {
            emptyList()
        }
    }
}

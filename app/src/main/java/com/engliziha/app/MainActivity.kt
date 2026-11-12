package com.engliziha.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.engliziha.app.data.Course
import com.engliziha.app.data.ProgressStore
import com.engliziha.app.ui.AppTheme
import com.engliziha.app.ui.HomeScreen
import com.engliziha.app.ui.LessonScreen
import com.engliziha.app.ui.LevelScreen
import com.engliziha.app.ui.QuizMode
import com.engliziha.app.ui.QuizScreen
import com.engliziha.app.ui.DailyScreen
import com.engliziha.app.ui.NotebookScreen
import com.engliziha.app.ui.ShadowScreen
import com.engliziha.app.ui.WritingScreen
import com.engliziha.app.data.Dictionary
import com.engliziha.app.data.Story
import com.engliziha.app.ui.StoriesScreen
import com.engliziha.app.ui.StoryScreen
import com.engliziha.app.notify.Reminders
import com.engliziha.app.ui.RecordsScreen
import com.engliziha.app.ui.ReviewScreen

sealed interface Screen {
    data object Home : Screen
    data object Records : Screen
    data object Review : Screen
    data class LevelList(val code: String) : Screen
    data class LessonView(val n: String) : Screen
    data class Quiz(val mode: QuizMode) : Screen
    data class Shadow(val n: String) : Screen
    data object Daily : Screen
    data object Notebook : Screen
    data object Writing : Screen
    data object Stories : Screen
    data class StoryView(val id: String) : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val course = Course.load(this)
        val store = ProgressStore(this)
        // Loaded on first use (about a megabyte of words), then kept.
        val dictionary by lazy { Dictionary.load(applicationContext) }
        val stories by lazy { Story.load(applicationContext) }
        Reminders.schedule(this)
        setContent {
            AppTheme {
                val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
                val state by store.state.collectAsState()
                val back: () -> Unit = { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
                BackHandler(enabled = stack.size > 1, onBack = back)

                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    AnimatedContent(
                        targetState = stack.last(),
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "screen",
                    ) { screen ->
                        when (screen) {
                            Screen.Home -> HomeScreen(
                                course, state,
                                onLevel = { stack += Screen.LevelList(it) },
                                onLesson = { stack += Screen.LessonView(it) },
                                onRecords = { stack += Screen.Records },
                                onReview = { stack += Screen.Review },
                                onDaily = { stack += Screen.Daily },
                                onNotebook = { stack += Screen.Notebook },
                                onWriting = { stack += Screen.Writing },
                                onStories = { stack += Screen.Stories },
                                onQuiz = { stack += Screen.Quiz(it) },
                                onSkipPlacement = { store.skipPlacement() },
                            )
                            Screen.Records -> RecordsScreen(course, state, store, back)
                            Screen.Daily -> DailyScreen(course, state, back) { stack += Screen.Quiz(it) }
                            Screen.Notebook -> NotebookScreen(course, state, store, back)
                            Screen.Stories -> StoriesScreen(stories, state, back) { stack += Screen.StoryView(it) }
                            is Screen.StoryView -> StoryScreen(stories.first { it.id == screen.id }, state, store, back) {
                                stack += Screen.Quiz(QuizMode.StoryQuiz(screen.id))
                            }
                            Screen.Writing -> WritingScreen(state, store, { dictionary }, back) { stack += Screen.Quiz(QuizMode.Spelling) }
                            is Screen.Shadow -> ShadowScreen(course, screen.n, back)
                            Screen.Review -> ReviewScreen(course, store, back)
                            is Screen.LevelList -> LevelScreen(
                                course, screen.code, state, back,
                                onLesson = { stack += Screen.LessonView(it) },
                                onExam = { stack += Screen.Quiz(QuizMode.Exam(screen.code)) },
                            )
                            is Screen.LessonView -> LessonScreen(
                                course, screen.n, state, store, back,
                                onQuiz = { stack += Screen.Quiz(QuizMode.LessonQuiz(screen.n)) },
                                onShadow = { stack += Screen.Shadow(screen.n) },
                            )
                            is Screen.Quiz -> QuizScreen(
                                course, screen.mode, store, { dictionary }, { stories },
                                onClose = back,
                                onNext = { next ->
                                    // Leave the quiz and the finished lesson, then open the next one.
                                    while (stack.size > 1 && stack.last() !is Screen.LevelList) stack.removeAt(stack.lastIndex)
                                    stack += Screen.LessonView(next)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

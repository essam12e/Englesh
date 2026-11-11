package com.engliziha.app.speech

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Reads English text aloud with the phone's own voice. */
class Speaker(context: Context) {
    private var ready = false
    private var pending: Pair<String, Float>? = null
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            onInit()
        }
    }

    private fun onInit() {
        tts.language = Locale.US
        ready = true
        pending?.let { say(it.first, it.second) }
        pending = null
    }

    fun say(text: String, rate: Float = 0.85f) {
        if (!ready) {
            pending = text to rate
            return
        }
        tts.setSpeechRate(rate)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "say")
    }

    fun release() {
        tts.stop()
        tts.shutdown()
    }
}

sealed interface ListenResult {
    data class Heard(val alternatives: List<String>) : ListenResult
    data class Failed(val message: String) : ListenResult
}

/**
 * One-shot English speech recognition using the recognizer installed on the phone.
 * With internet it uses the online recognizer; without, the on-device English model.
 * If the chosen one is not available on this phone, it quietly tries the other.
 */
class Listener(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null
    private val main = Handler(Looper.getMainLooper())

    val available: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    private fun online(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        return manager.getNetworkCapabilities(manager.activeNetwork)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    fun listen(onStart: () -> Unit, onResult: (ListenResult) -> Unit) = start(!online(), false, onStart, onResult)

    private fun start(offline: Boolean, retried: Boolean, onStart: () -> Unit, onResult: (ListenResult) -> Unit) {
        stop()
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = onStart()
            override fun onResults(results: Bundle?) {
                val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                onResult(if (list.isEmpty()) ListenResult.Failed("لم أسمع شيئاً، حاول مرة أخرى") else ListenResult.Heard(list))
            }

            override fun onError(error: Int) {
                // 12/13: this language is not installed for the mode we asked for.
                val modeProblem = error == ERROR_LANGUAGE_NOT_SUPPORTED || error == ERROR_LANGUAGE_UNAVAILABLE ||
                    error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT ||
                    error == SpeechRecognizer.ERROR_SERVER
                if (modeProblem && !retried) {
                    main.post { start(!offline, true, onStart, onResult) }
                    return
                }
                onResult(
                    ListenResult.Failed(
                        when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                                "لم أسمع شيئاً واضحاً، اقترب من الميكروفون وحاول مرة أخرى"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "يجب السماح باستخدام الميكروفون"
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "الميكروفون مشغول، انتظر لحظة ثم حاول"
                            else -> if (modeProblem)
                                "التعرف على الصوت يحتاج إنترنت، أو تنزيل اللغة الإنجليزية (English US) من إعدادات «الإدخال الصوتي من Google» ليعمل بدونه"
                            else "تعذّر التعرف على الصوت (رمز $error)"
                        }
                    )
                )
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-US")
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        if (offline) intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        r.startListening(intent)
    }

    fun stop() {
        recognizer?.destroy()
        recognizer = null
    }

    private companion object {
        // Literal values so this also compiles against older SDK constants.
        const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
        const val ERROR_LANGUAGE_UNAVAILABLE = 13
    }
}

package com.engliziha.app.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
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

/** One-shot English speech recognition using the recognizer installed on the phone. */
class Listener(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null

    val available: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun listen(onStart: () -> Unit, onResult: (ListenResult) -> Unit) {
        stop()
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = onStart()
            override fun onResults(results: Bundle?) {
                val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                onResult(if (list.isEmpty()) ListenResult.Failed("لم أسمع شيئاً، حاول مرة أخرى") else ListenResult.Heard(list))
            }

            override fun onError(error: Int) = onResult(
                ListenResult.Failed(
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                            "لم أسمع شيئاً واضحاً، اقترب من الميكروفون وحاول مرة أخرى"
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                            "التعرف على الصوت يحتاج اتصالاً بالإنترنت"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "يجب السماح باستخدام الميكروفون"
                        else -> "تعذّر التعرف على الصوت (رمز $error)"
                    }
                )
            )

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        r.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-US")
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        )
    }

    fun stop() {
        recognizer?.destroy()
        recognizer = null
    }
}

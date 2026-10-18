package com.zenithai.countai.util

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * Voice counting: Android's built-in SpeechRecognizer (free, no API key)
 * turns speech into a command string, and TextToSpeech reads the answer
 * back. Both prefer on-device engines when the phone has them — nothing
 * is sent anywhere by CountAI itself (the system recognizer may use the
 * network depending on the phone's own speech settings).
 */
class VoiceHelper(
    private val context: Context,
    private val listener: Listener,
) {

    interface Listener {
        fun onVoiceResult(text: String)
        fun onVoiceError(message: String)
    }

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    fun isRecognitionAvailable(): Boolean =
        SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening() {
        if (!isRecognitionAvailable()) {
            listener.onVoiceError("speech_unavailable")
            return
        }
        stopListening()
        try {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle) {
                    val list = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = list?.firstOrNull().orEmpty()
                    if (text.isBlank()) listener.onVoiceError("no_speech")
                    else listener.onVoiceResult(text)
                }

                override fun onError(error: Int) {
                    val msg = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "no_speech"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "mic_permission"
                        else -> "recognition_failed"
                    }
                    listener.onVoiceError(msg)
                }

                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                )
                // Prefer the on-device recognizer when the phone supports it.
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            try {
                startListening(intent)
            } catch (e: Exception) {
                Log.w(TAG, "startListening failed", e)
                listener.onVoiceError("recognition_failed")
            }
        } catch (e: Throwable) {
            // createSpeechRecognizer itself can throw on devices with no
            // recognition service — never crash, report it instead.
            Log.w(TAG, "createSpeechRecognizer failed", e)
            listener.onVoiceError("speech_unavailable")
        }
    }

    fun stopListening() {
        try {
            recognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "recognizer destroy failed", e)
        }
        recognizer = null
    }

    /** Reads [text] aloud (best-effort; silent when TTS is missing). */
    fun speak(text: String) {
        val engine = tts ?: TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                tts?.language = Locale.getDefault()
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "countai")
            }
        }.also { tts = it }
        if (ttsReady) {
            engine.language = Locale.getDefault()
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "countai")
        }
    }

    fun destroy() {
        stopListening()
        try {
            tts?.shutdown()
        } catch (e: Exception) {
            Log.w(TAG, "tts shutdown failed", e)
        }
        tts = null
    }

    companion object {
        private const val TAG = "VoiceHelper"
    }
}

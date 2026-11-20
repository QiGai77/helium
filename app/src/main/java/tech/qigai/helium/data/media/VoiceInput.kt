package tech.qigai.helium.data.media

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Hold-to-talk speech input on the system [SpeechRecognizer] (zh-CN, partial results). One instance per screen;
 * call [release] when it leaves composition. All callbacks arrive on the main thread.
 */
class VoiceInput(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null
    private var onPartial: (String) -> Unit = {}
    private var onFinal: (String) -> Unit = {}
    private var onError: (String) -> Unit = {}
    private var last = ""
    private var delivered = false

    val available: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(onPartial: (String) -> Unit, onFinal: (String) -> Unit, onError: (String) -> Unit) {
        this.onPartial = onPartial
        this.onFinal = onFinal
        this.onError = onError
        last = ""
        delivered = false
        val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also { it.setRecognitionListener(listener); recognizer = it }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        r.startListening(intent)
    }

    /** Finger lifted: stop recording and let the recognizer deliver its final result. */
    fun stop() {
        recognizer?.stopListening()
    }

    fun release() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun deliver(text: String) {
        if (delivered) return
        delivered = true
        onFinal(text)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            val t = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            if (t.isNotBlank()) { last = t; onPartial(t) }
        }

        override fun onResults(results: Bundle?) {
            val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            deliver(t.ifBlank { last })
        }

        override fun onError(error: Int) {
            if (last.isNotBlank()) { deliver(last); return }
            if (delivered) return
            delivered = true
            onError(
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没听清，按住麦克风再说一次"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "需要麦克风权限才能语音输入"
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "语音识别需要网络，请检查连接"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "语音识别正忙，稍后再试"
                    else -> "语音识别出错（$error）"
                },
            )
        }
    }
}

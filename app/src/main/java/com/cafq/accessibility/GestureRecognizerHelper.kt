package com.cafq.accessibility

import android.content.Context
import android.os.SystemClock
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizer
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult

class GestureRecognizerHelper(
    val context: Context,
    val listener: MyGestureRecognizerListener
) {
    private var gestureRecognizer: GestureRecognizer? = null

    init {
        setupGestureRecognizer()
    }

    private fun setupGestureRecognizer() {
        val baseOptionsBuilder = BaseOptions.builder()
            .setModelAssetPath("gesture_recognizer.task")

        val optionsBuilder = GestureRecognizer.GestureRecognizerOptions.builder()
            .setBaseOptions(baseOptionsBuilder.build())
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumHands(1) // Configurado para 1 mano con precisión
            .setMinHandDetectionConfidence(0.6f)
            .setMinHandPresenceConfidence(0.6f)
            .setMinTrackingConfidence(0.6f)
            .setResultListener { result: GestureRecognizerResult, input: MPImage ->
                listener.onResults(result)
            }
            .setErrorListener { error ->
                listener.onError(error.localizedMessage ?: "Error desconocido en MediaPipe")
            }

        try {
            gestureRecognizer = GestureRecognizer.createFromOptions(context, optionsBuilder.build())
        } catch (e: Exception) {
            listener.onError("No se pudo inicializar MediaPipe: ${e.message}")
        }
    }

    fun recognizeAsync(mpImage: MPImage) {
        val frameTime = SystemClock.uptimeMillis()
        gestureRecognizer?.recognizeAsync(mpImage, frameTime)
    }

    fun clear() {
        gestureRecognizer?.close()
        gestureRecognizer = null
    }

    interface MyGestureRecognizerListener {
        fun onResults(result: GestureRecognizerResult)
        fun onError(error: String)
    }
}
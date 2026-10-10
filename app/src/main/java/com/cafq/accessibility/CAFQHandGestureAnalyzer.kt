package com.cafq.accessibility

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlin.math.abs
import kotlin.math.sqrt

class CAFQHandGestureAnalyzer(
    private val context: Context,
    private val screenWidth: Int,
    private val screenHeight: Int,
    private val listener: CAFQGestureListener
) : ImageAnalysis.Analyzer {

    private val handLandmarker: HandLandmarker

    private var previousIndexY: Float = -1f
    private var isPinched: Boolean = false
    private var initialZoomDistance: Float = -1f

    init {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("gesture_recognizer.task") // <-- Cambiado aquí
            .build()

        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.IMAGE)
            .setNumHands(1)
            .setMinHandDetectionConfidence(0.6f)
            .setMinHandPresenceConfidence(0.6f)
            .setMinTrackingConfidence(0.6f)
            .build()

        handLandmarker = HandLandmarker.createFromOptions(context, options)
    }

    @androidx.camera.core.ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val bitmap = toBitmap(imageProxy)
            if (bitmap != null) {
                val mpImage = BitmapImageBuilder(bitmap).build()
                val result = handLandmarker.detect(mpImage)
                processHandLandmarks(result)
            }
        }
        imageProxy.close()
    }

    private fun processHandLandmarks(result: HandLandmarkerResult) {
        if (result.landmarks().isEmpty()) {
            resetState()
            return
        }

        val landmarks = result.landmarks()[0]
        val wrist = landmarks[0]
        val thumbTip = landmarks[4]
        val indexTip = landmarks[8]

        // 1. Clic Fino por Pellizco
        val pinchDistance = calculateEuclideanDistance(thumbTip.x(), thumbTip.y(), indexTip.x(), indexTip.y())
        val pinchThreshold = 0.05f

        if (pinchDistance < pinchThreshold) {
            if (!isPinched) {
                isPinched = true
                val mappedX = indexTip.x() * screenWidth
                val mappedY = indexTip.y() * screenHeight
                listener.onClick(mappedX, mappedY)
            }
        } else {
            isPinched = false
        }

        // 2. Scroll Vertical
        if (previousIndexY != -1f) {
            val deltaY = indexTip.y() - previousIndexY
            val deadZone = 0.015f

            if (abs(deltaY) > deadZone) {
                val direction = if (deltaY > 0) ScrollDirection.DOWN else ScrollDirection.UP
                listener.onScroll(direction, deltaY)
            }
        }
        previousIndexY = indexTip.y()

        // 3. Zoom / Expansión
        val currentZoomDistance = calculateEuclideanDistance(wrist.x(), wrist.y(), indexTip.x(), indexTip.y())
        if (initialZoomDistance == -1f) {
            initialZoomDistance = currentZoomDistance
        } else {
            val scaleFactor = currentZoomDistance / initialZoomDistance
            if (abs(scaleFactor - 1.0f) > 0.1f) {
                listener.onZoom(scaleFactor)
            }
        }
    }

    private fun calculateEuclideanDistance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        return sqrt(dx * dx + dy * dy)
    }

    private fun resetState() {
        previousIndexY = -1f
        initialZoomDistance = -1f
        isPinched = false
    }

    private fun toBitmap(imageProxy: ImageProxy): Bitmap? {
        val buffer = imageProxy.planes[0].buffer
        buffer.rewind()
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        if (rotationDegrees == 0) return bitmap

        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
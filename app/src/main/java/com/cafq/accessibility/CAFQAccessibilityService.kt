package com.cafq.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.util.Log

class CAFQAccessibilityService : AccessibilityService() {

    companion object {
        var instance: CAFQAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d("CAFQService", "Servicio de accesibilidad conectado con éxito")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Sin eventos por ahora
    }

    override fun onInterrupt() {
        Log.d("CAFQService", "Servicio interrumpido")
    }

    fun performClick(x: Float, y: Float) {
        val path = Path().apply {
            moveTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val gestureBuilder = GestureDescription.Builder().addStroke(stroke)

        dispatchGesture(gestureBuilder.build(), object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                Log.d("CAFQService", "Clic simulado exitosamente en ($x, $y)")
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                Log.d("CAFQService", "Clic cancelado")
            }
        }, null)
    }

    // Nuevas acciones globales de accesibilidad
    fun performHomeAction() {
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    fun performBackAction() {
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    fun performRecentsAction() {
        performGlobalAction(GLOBAL_ACTION_RECENTS)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }
}
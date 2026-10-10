package com.cafq.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class CAFQAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Aquí recibiremos los eventos de accesibilidad en el futuro
    }

    override fun onInterrupt() {
        // Manejo de interrupciones del servicio
    }
}
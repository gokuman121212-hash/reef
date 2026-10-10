package com.cafq.accessibility

enum class ScrollDirection {
    UP, DOWN
}

interface CAFQGestureListener {
    fun onScroll(direction: ScrollDirection, deltaY: Float)
    fun onClick(x: Float, y: Float)
    fun onZoom(scaleFactor: Float)
}
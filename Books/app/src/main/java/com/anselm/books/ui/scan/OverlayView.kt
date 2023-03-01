package com.anselm.books.ui.scan

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View

class OverlayView(
    context: Context,
    attrs: AttributeSet
): View(context, attrs) {
    private val rects = mutableListOf<Pair<Rect, Paint>>()
    private val redPaint =
        Paint().apply {
            isAntiAlias = true
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = 10.0F
        }
    private val greenPaint =
        Paint().apply {
            isAntiAlias = true
            color = Color.GREEN
            style = Paint.Style.STROKE
            strokeWidth = 10.0F
        }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        canvas?.let{ rects.forEach { (rect, paint) -> it.drawRect(rect, paint) } }
    }

    fun drawRect(rect: Rect, known: Boolean) {
        rects.add(Pair(rect, if (known) greenPaint else redPaint))
    }

    fun clearRect() {
        rects.clear()
    }
}
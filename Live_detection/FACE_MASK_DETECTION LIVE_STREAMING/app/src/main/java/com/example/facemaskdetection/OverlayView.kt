package com.example.facemaskdetection

import android.graphics.Canvas
import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View


class OverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    var boundingBox: MutableList<Box> = mutableListOf()
    var paint = Paint()

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeMiter = 100f
        boundingBox.forEach { box ->
            if (box.isMask){
                paint.color = Color.GREEN
            } else {
                paint.color = Color.RED
            }
            paint.setTextAlign(Paint.Align.LEFT)
            canvas.drawText(box.label, box.rectF.left, box.rectF.top-9F, paint)
            canvas.drawRoundRect(box.rectF, 2F, 2F, paint)
        }
    }
}
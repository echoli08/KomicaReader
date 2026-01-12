package com.komica.reader.widget

import android.content.Context
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.min

class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    var minScale = 1.0f
    var maxScale = 4.0f
    var onZoomChanged: ((Boolean) -> Unit)? = null

    private val baseMatrix = Matrix()
    private val supportMatrix = Matrix()
    private val drawMatrix = Matrix()
    private var currentScale = 1.0f
    private var lastTouchX = 0.0f
    private var lastTouchY = 0.0f
    private var activePointerId = MotionEvent.INVALID_POINTER_ID

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val targetScale = (currentScale * detector.scaleFactor).coerceIn(minScale, maxScale)
                val deltaScale = targetScale / currentScale
                if (deltaScale == 1.0f) return true
                supportMatrix.postScale(deltaScale, deltaScale, detector.focusX, detector.focusY)
                currentScale = targetScale
                checkBounds()
                applyMatrix()
                notifyZoomChanged()
                return true
            }
        }
    )

    init {
        scaleType = ScaleType.MATRIX
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        resetZoom()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        resetZoom()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                activePointerId = event.getPointerId(0)
            }
            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = event.findPointerIndex(activePointerId)
                if (pointerIndex != -1 && !scaleDetector.isInProgress && currentScale > minScale) {
                    val x = event.getX(pointerIndex)
                    val y = event.getY(pointerIndex)
                    val deltaX = x - lastTouchX
                    val deltaY = y - lastTouchY
                    supportMatrix.postTranslate(deltaX, deltaY)
                    checkBounds()
                    applyMatrix()
                    lastTouchX = x
                    lastTouchY = y
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val pointerIndex = event.actionIndex
                if (event.getPointerId(pointerIndex) == activePointerId) {
                    val newIndex = if (pointerIndex == 0) 1 else 0
                    if (newIndex < event.pointerCount) {
                        activePointerId = event.getPointerId(newIndex)
                        lastTouchX = event.getX(newIndex)
                        lastTouchY = event.getY(newIndex)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activePointerId = MotionEvent.INVALID_POINTER_ID
            }
        }
        val shouldDisallow = currentScale > minScale || scaleDetector.isInProgress
        // 繁體中文註解：縮放或拖曳中避免 ViewPager2 攔截手勢
        parent?.requestDisallowInterceptTouchEvent(shouldDisallow)
        return true
    }

    fun resetZoom() {
        val drawable = drawable ?: return
        if (width == 0 || height == 0) return
        val drawableWidth = drawable.intrinsicWidth
        val drawableHeight = drawable.intrinsicHeight
        if (drawableWidth <= 0 || drawableHeight <= 0) return
        baseMatrix.reset()
        val scale = min(width / drawableWidth.toFloat(), height / drawableHeight.toFloat())
        baseMatrix.postScale(scale, scale)
        val dx = (width - drawableWidth * scale) / 2.0f
        val dy = (height - drawableHeight * scale) / 2.0f
        baseMatrix.postTranslate(dx, dy)
        supportMatrix.reset()
        currentScale = 1.0f
        applyMatrix()
        notifyZoomChanged()
    }

    private fun applyMatrix() {
        drawMatrix.set(baseMatrix)
        drawMatrix.postConcat(supportMatrix)
        imageMatrix = drawMatrix
    }

    private fun checkBounds() {
        val rect = getDisplayRect(getDrawMatrix()) ?: return
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        var deltaX = 0.0f
        var deltaY = 0.0f
        if (rect.width() <= viewWidth) {
            deltaX = (viewWidth - rect.width()) / 2.0f - rect.left
        } else {
            if (rect.left > 0.0f) deltaX = -rect.left
            if (rect.right < viewWidth) deltaX = viewWidth - rect.right
        }
        if (rect.height() <= viewHeight) {
            deltaY = (viewHeight - rect.height()) / 2.0f - rect.top
        } else {
            if (rect.top > 0.0f) deltaY = -rect.top
            if (rect.bottom < viewHeight) deltaY = viewHeight - rect.bottom
        }
        supportMatrix.postTranslate(deltaX, deltaY)
    }

    private fun getDrawMatrix(): Matrix {
        drawMatrix.set(baseMatrix)
        drawMatrix.postConcat(supportMatrix)
        return drawMatrix
    }

    private fun getDisplayRect(matrix: Matrix): RectF? {
        val drawable = drawable ?: return null
        val rect = RectF(
            0.0f,
            0.0f,
            drawable.intrinsicWidth.toFloat(),
            drawable.intrinsicHeight.toFloat()
        )
        matrix.mapRect(rect)
        return rect
    }

    private fun notifyZoomChanged() {
        val isZoomed = currentScale > minScale + 0.01f
        // 繁體中文註解：回報目前是否為放大狀態
        onZoomChanged?.invoke(isZoomed)
    }
}

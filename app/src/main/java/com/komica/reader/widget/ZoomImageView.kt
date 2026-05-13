package com.komica.reader.widget

import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.max
import kotlin.math.min

class ZoomImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {
    private val matrixValues = FloatArray(9)
    private val imageMatrixState = Matrix()
    private val lastPoint = PointF()
    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())
    private var minScale = 1f
    private var maxScale = 4f
    private var currentScale = 1f
    private var isDragging = false
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var navigationListener: ((ImageNavigation) -> Unit)? = null

    init {
        scaleType = ScaleType.MATRIX
    }

    fun SetMaxScale(value: Float) {
        maxScale = value.coerceAtLeast(2f)
    }

    fun SetNavigationListener(listener: (ImageNavigation) -> Unit) {
        navigationListener = listener
    }

    fun ResetZoom() {
        ConfigureBaseMatrix()
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        ConfigureBaseMatrix()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        ConfigureBaseMatrix()
    }

    private fun ConfigureBaseMatrix() {
        val source = drawable ?: return
        if (width == 0 || height == 0 || source.intrinsicWidth <= 0 || source.intrinsicHeight <= 0) return
        val scale = min(width.toFloat() / source.intrinsicWidth, height.toFloat() / source.intrinsicHeight)
        val dx = (width - source.intrinsicWidth * scale) / 2f
        val dy = (height - source.intrinsicHeight * scale) / 2f
        imageMatrixState.reset()
        imageMatrixState.postScale(scale, scale)
        imageMatrixState.postTranslate(dx, dy)
        currentScale = minScale
        imageMatrix = imageMatrixState
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                downTime = event.eventTime
                lastPoint.set(event.x, event.y)
                isDragging = true
                parent.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging && !scaleDetector.isInProgress && currentScale > minScale) {
                    imageMatrixState.postTranslate(event.x - lastPoint.x, event.y - lastPoint.y)
                    imageMatrix = imageMatrixState
                    lastPoint.set(event.x, event.y)
                    parent.requestDisallowInterceptTouchEvent(true)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (event.actionMasked == MotionEvent.ACTION_UP && !scaleDetector.isInProgress) {
                    HandleNavigationGesture(event)
                }
                isDragging = false
                parent.requestDisallowInterceptTouchEvent(currentScale > minScale)
            }
        }
        return true
    }

    private fun HandleNavigationGesture(event: MotionEvent) {
        val dx = event.x - downX
        val dy = event.y - downY
        val absDx = kotlin.math.abs(dx)
        val absDy = kotlin.math.abs(dy)
        val duration = event.eventTime - downTime
        val swipeThreshold = 72f * resources.displayMetrics.density
        val tapThreshold = 12f * resources.displayMetrics.density

        when {
            absDx < tapThreshold && absDy < tapThreshold -> {
                navigationListener?.invoke(if (event.x < width / 2f) ImageNavigation.Previous else ImageNavigation.Next)
            }
            absDx >= absDy && absDx > swipeThreshold -> {
                if (dx > 0 && duration >= LongRightSwipeMs) {
                    navigationListener?.invoke(ImageNavigation.Exit)
                } else {
                    navigationListener?.invoke(if (dx < 0) ImageNavigation.Next else ImageNavigation.RightSwipePrevious)
                }
            }
            absDy > swipeThreshold -> {
                navigationListener?.invoke(if (dy < 0) ImageNavigation.Next else ImageNavigation.Previous)
            }
        }
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val previousScale = currentScale
            currentScale = min(max(currentScale * detector.scaleFactor, minScale), maxScale)
            val factor = currentScale / previousScale
            imageMatrixState.postScale(factor, factor, detector.focusX, detector.focusY)
            imageMatrix = imageMatrixState
            return true
        }
    }

    enum class ImageNavigation {
        Previous,
        RightSwipePrevious,
        Next,
        Exit
    }

    companion object {
        private const val LongRightSwipeMs = 500L
    }
}

package com.komica.reader.util

import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.core.content.ContextCompat
import com.komica.reader.R

object WindowInsetsUtil {
    fun ApplyToolbarInsets(toolbar: View, baseHeightDp: Int = 64) {
        val density = toolbar.resources.displayMetrics.density
        val baseHeightPx = (baseHeightDp * density).toInt()
        val basePaddingTop = toolbar.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, insets ->
            val topInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.updatePadding(top = basePaddingTop + topInset)
            view.updateLayoutParams {
                height = baseHeightPx + topInset
            }
            insets
        }
        ViewCompat.requestApplyInsets(toolbar)
    }

    fun ApplyTopMarginInsets(view: View, extraTopDp: Int = 24) {
        val density = view.resources.displayMetrics.density
        val extraTopPx = (extraTopDp * density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(view) { target, insets ->
            val topInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            target.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = topInset + extraTopPx
            }
            insets
        }
        ViewCompat.requestApplyInsets(view)
    }

    fun ApplyBrandStatusBar(window: Window) {
        window.statusBarColor = ContextCompat.getColor(window.context, R.color.kr_primary)
        window.decorView.systemUiVisibility = window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
    }
}

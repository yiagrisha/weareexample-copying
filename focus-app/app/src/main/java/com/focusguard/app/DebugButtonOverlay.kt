package com.focusguard.app

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView

/** Красная кнопка DUMP поверх Инстаграма: сохраняет «снимок» устройства экрана. */
class DebugButtonOverlay(
    private val context: Context,
    private val onTap: () -> Unit,
) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private var view: View? = null

    fun show() {
        if (view != null) return
        val d = context.resources.displayMetrics.density
        val button = TextView(context).apply {
            text = "DUMP"
            setTextColor(Color.WHITE)
            textSize = 11f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xCCE53935.toInt())
            }
            setOnClickListener { onTap() }
        }
        val size = (52 * d).toInt()
        val lp = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = (8 * d).toInt()
            y = (context.resources.displayMetrics.heightPixels * 0.35).toInt()
            title = "FocusGuard debug"
        }
        try {
            wm.addView(button, lp)
            view = button
        } catch (e: Exception) {
            DebugLog.log("Debug button failed: $e")
        }
    }

    fun hide() {
        val v = view ?: return
        try {
            wm.removeViewImmediate(v)
        } catch (_: Exception) {
        }
        view = null
    }
}

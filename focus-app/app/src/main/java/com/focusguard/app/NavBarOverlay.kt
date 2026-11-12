package com.focusguard.app

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout

/**
 * Своя нижняя панель, которая рисуется ровно поверх панели Инстаграма.
 * На ней все вкладки, кроме Reels, расставлены равномерно. Нажатие на
 * иконку «нажимает» настоящую вкладку под ней.
 */
class NavBarOverlay(
    private val context: Context,
    private val onTap: (Item, Boolean) -> Unit,
) {
    /** Одна иконка: какая это вкладка, выбрана ли она и её номер в настоящей панели. */
    data class Item(val type: Ig.Tab, val selected: Boolean, val realIndex: Int)

    private val wm = context.getSystemService(WindowManager::class.java)
    private var view: LinearLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var contentKey = ""

    val isShowing: Boolean get() = view != null

    fun show(bounds: Rect, items: List<Item>, background: Int) {
        val key = "$items|$background"
        val current = view
        if (current != null && key == contentKey) {
            moveTo(bounds)
            return
        }

        val newView = build(items, background)
        try {
            if (current == null) {
                val lp = layoutParams(bounds)
                wm.addView(newView, lp)
                params = lp
            } else {
                // Подменяем содержимое без мигания: сначала новое, потом убираем старое.
                val lp = layoutParams(bounds)
                wm.addView(newView, lp)
                wm.removeViewImmediate(current)
                params = lp
            }
            view = newView
            contentKey = key
        } catch (e: Exception) {
            DebugLog.log("NavBar show failed: $e")
        }
    }

    fun hide() {
        val v = view ?: return
        try {
            wm.removeViewImmediate(v)
        } catch (_: Exception) {
        }
        view = null
        params = null
        contentKey = ""
    }

    private fun moveTo(bounds: Rect) {
        val v = view ?: return
        val lp = params ?: return
        if (lp.x == bounds.left && lp.y == bounds.top &&
            lp.width == bounds.width() && lp.height == bounds.height()
        ) return
        lp.x = bounds.left
        lp.y = bounds.top
        lp.width = bounds.width()
        lp.height = bounds.height()
        try {
            wm.updateViewLayout(v, lp)
        } catch (e: Exception) {
            DebugLog.log("NavBar move failed: $e")
        }
    }

    private fun layoutParams(bounds: Rect) = WindowManager.LayoutParams(
        bounds.width(),
        bounds.height(),
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = bounds.left
        y = bounds.top
        layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        fitInsetsTypes = 0
        title = "FocusGuard nav bar"
    }

    private fun build(items: List<Item>, bgColor: Int): LinearLayout {
        val fg = if (isLight(bgColor)) Color.BLACK else Color.WHITE
        val iconSize = dp(26)
        val bar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(bgColor)
            // Нажатия между иконками не должны «проваливаться» в Инстаграм.
            isClickable = true
        }
        for (item in items) {
            val cell = FrameLayout(context).apply {
                isClickable = true
                isLongClickable = true
                background = RippleDrawable(
                    ColorStateList.valueOf(Color.argb(60, Color.red(fg), Color.green(fg), Color.blue(fg))),
                    null,
                    null,
                )
                contentDescription = item.type.name
                setOnClickListener { onTap(item, false) }
                setOnLongClickListener { onTap(item, true); true }
            }
            val icon = ImageView(context).apply {
                setImageResource(iconFor(item))
                imageTintList = ColorStateList.valueOf(fg)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            cell.addView(icon, FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER))
            bar.addView(cell, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        }
        return bar
    }

    private fun iconFor(item: Item): Int = when (item.type) {
        Ig.Tab.HOME -> if (item.selected) R.drawable.ic_home_filled else R.drawable.ic_home
        Ig.Tab.SEARCH -> if (item.selected) R.drawable.ic_search_bold else R.drawable.ic_search
        Ig.Tab.MESSAGES -> if (item.selected) R.drawable.ic_send_filled else R.drawable.ic_send
        Ig.Tab.CREATE -> R.drawable.ic_create
        Ig.Tab.PROFILE -> if (item.selected) R.drawable.ic_profile_filled else R.drawable.ic_profile
        else -> R.drawable.ic_dot
    }

    private fun isLight(c: Int): Boolean {
        val lum = 0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)
        return lum > 140
    }

    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()
}

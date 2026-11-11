package com.focusguard.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import kotlin.math.abs

/**
 * Служба «Специальных возможностей». Видит, что происходит на экране
 * Инстаграма, и:
 *  1) рисует поверх нижнего меню своё — без вкладки Reels;
 *  2) если всё же открылась вкладка Reels — возвращает на главную;
 *  3) если рилс открыт из директа/ленты — даёт его посмотреть, но при
 *     попытке пролистнуть к следующему нажимает «Назад».
 */
class FocusService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var navBar: NavBarOverlay? = null
    private var debugButton: DebugButtonOverlay? = null

    private var evaluatePending = false
    private var lastActionAt = 0L
    private var reelsTabClickedAt = 0L

    // Состояние просмотрщика рилсов
    private var viewerOpen = false
    private var singleReelMode = false
    private var viewerOpenedAt = 0L
    private var baselineIndex: Int? = null

    // Цвет фона нижней панели Инстаграма (берём со скриншота)
    private var barColor: Int? = null
    private var barColorNight = -1
    private var sampling = false

    private val prefsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> handler.post { evaluate() } }

    private data class Screen(
        val igOnTop: Boolean,
        val igRoots: List<AccessibilityNodeInfo>,
        val keyboardVisible: Boolean,
        val shadeOpen: Boolean,
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        navBar = NavBarOverlay(this) { item, long -> onOverlayTap(item, long) }
        debugButton = DebugButtonOverlay(this) { dumpNow() }
        Prefs.sp(this).registerOnSharedPreferenceChangeListener(prefsListener)
        DebugLog.log("Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString()
        if (pkg == packageName) return
        if (pkg == Ig.PKG) {
            when (event.eventType) {
                AccessibilityEvent.TYPE_VIEW_CLICKED -> onInstagramClick(event)
                AccessibilityEvent.TYPE_VIEW_SCROLLED -> onInstagramScroll(event)
            }
        }
        scheduleEvaluate()
    }

    override fun onInterrupt() {}

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        scheduleEvaluate()
    }

    override fun onDestroy() {
        Prefs.sp(this).unregisterOnSharedPreferenceChangeListener(prefsListener)
        navBar?.hide()
        debugButton?.hide()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    // ---------------------------------------------------------------------
    // События

    private fun onInstagramClick(event: AccessibilityEvent) {
        val src = event.source ?: return
        if (Ig.isReelsTabNode(src)) {
            reelsTabClickedAt = now()
            DebugLog.log("Click on Reels tab detected")
            handler.postDelayed({ evaluate() }, 150)
        }
    }

    private fun onInstagramScroll(event: AccessibilityEvent) {
        if (!viewerOpen || !singleReelMode) return
        if (!Prefs.get(this, Prefs.SINGLE_REEL)) return

        val id = event.source?.viewIdResourceName ?: ""
        val cls = event.className?.toString() ?: ""
        val fromViewer = id.contains("clips_viewer") ||
            Ig.REELS_VIEWER_IDS.any { id.endsWith(":id/$it") } ||
            (id.isEmpty() && cls.contains("ViewPager"))
        if (!fromViewer) return

        val now = now()
        val idx = event.fromIndex
        if (now - viewerOpenedAt < GRACE_MS) {
            // Пока рилс только открывается, Инстаграм сам «докручивает» экран.
            if (idx >= 0) baselineIndex = idx
            return
        }
        val base = baselineIndex
        if (base == null && idx >= 0) baselineIndex = idx

        val dy = event.scrollDeltaY
        val moved = (dy != -1 && abs(dy) > SCROLL_THRESHOLD_PX) ||
            (base != null && idx >= 0 && idx != base)
        if (!moved) return
        if (now - lastActionAt < ACTION_COOLDOWN_MS) return
        lastActionAt = now

        DebugLog.log("Swipe in single-reel mode (index $base -> $idx, dy=$dy) -> Back")
        performGlobalAction(GLOBAL_ACTION_BACK)
        toast(getString(R.string.toast_single_reel))
    }

    // ---------------------------------------------------------------------
    // Основная проверка экрана

    private fun scheduleEvaluate() {
        if (evaluatePending) return
        evaluatePending = true
        handler.postDelayed({
            evaluatePending = false
            evaluate()
        }, EVALUATE_DELAY_MS)
    }

    private fun evaluate() {
        val nav = navBar ?: return
        val screen = readScreen()
        if (!screen.igOnTop || screen.igRoots.isEmpty()) {
            nav.hide()
            debugButton?.hide()
            if (viewerOpen) {
                viewerOpen = false
                singleReelMode = false
            }
            return
        }

        if (Prefs.get(this, Prefs.DEBUG_BUTTON)) debugButton?.show() else debugButton?.hide()

        val now = now()
        val tabBar = screen.igRoots.firstNotNullOfOrNull { Ig.findTabBar(it) }
        val tabs = tabBar?.let { Ig.readTabs(it) } ?: emptyList()
        val viewer = screen.igRoots.firstNotNullOfOrNull { Ig.findReelsViewer(it) }

        val reelsTabSelected = tabs.any { it.type == Ig.Tab.REELS && it.node.isSelected }
        val noSelectionInfo = tabs.isNotEmpty() && tabs.none { it.node.isSelected }
        val onReelsTab = reelsTabSelected ||
            (viewer != null && tabBar != null &&
                (noSelectionInfo || now - reelsTabClickedAt < 2000))

        if (viewer != null && !viewerOpen) {
            viewerOpen = true
            viewerOpenedAt = now
            baselineIndex = null
            singleReelMode = !onReelsTab
            DebugLog.log("Reels viewer opened (singleReel=$singleReelMode, tabBar=${tabBar != null})")
        } else if (viewer == null && viewerOpen) {
            viewerOpen = false
            singleReelMode = false
            DebugLog.log("Reels viewer closed")
        }

        if (onReelsTab && Prefs.get(this, Prefs.BLOCK_REELS_TAB)) {
            leaveReelsTab(tabs)
        }

        updateNavBar(screen, tabBar, tabs)
    }

    private fun readScreen(): Screen {
        val ws = try { windows } catch (e: Exception) { emptyList<AccessibilityWindowInfo>() }
        if (ws.isEmpty()) {
            val r = rootInActiveWindow
            return if (r != null && r.packageName?.toString() == Ig.PKG) {
                Screen(true, listOf(r), keyboardVisible = false, shadeOpen = false)
            } else {
                Screen(false, emptyList(), keyboardVisible = false, shadeOpen = false)
            }
        }

        var topApp: AccessibilityWindowInfo? = null
        var topAppRoot: AccessibilityNodeInfo? = null
        val igRoots = mutableListOf<AccessibilityNodeInfo>()
        var keyboard = false
        for (w in ws) {
            when (w.type) {
                AccessibilityWindowInfo.TYPE_APPLICATION -> {
                    val root = w.root
                    val current = topApp
                    if (current == null || w.layer > current.layer) {
                        topApp = w
                        topAppRoot = root
                    }
                    if (root != null && root.packageName?.toString() == Ig.PKG) igRoots += root
                }
                AccessibilityWindowInfo.TYPE_INPUT_METHOD -> keyboard = true
            }
        }

        val igOnTop = topAppRoot?.packageName?.toString() == Ig.PKG
        val screenH = resources.displayMetrics.heightPixels
        val topLayer = topApp?.layer ?: Int.MIN_VALUE
        val shade = ws.any {
            if (it.type != AccessibilityWindowInfo.TYPE_SYSTEM || it.layer <= topLayer) return@any false
            val r = Rect()
            it.getBoundsInScreen(r)
            r.height() > screenH / 2
        }
        return Screen(igOnTop, igRoots, keyboard, shade)
    }

    // ---------------------------------------------------------------------
    // Действия

    private fun leaveReelsTab(tabs: List<Ig.TabNode>) {
        val now = now()
        if (now - lastActionAt < ACTION_COOLDOWN_MS) return
        lastActionAt = now
        val home = tabs.firstOrNull { it.type == Ig.Tab.HOME }
        val ok = home?.let { Ig.clickTarget(it.node).performAction(AccessibilityNodeInfo.ACTION_CLICK) } == true
        if (!ok) performGlobalAction(GLOBAL_ACTION_BACK)
        DebugLog.log("Left Reels tab (home click ok=$ok)")
        toast(getString(R.string.toast_reels_blocked))
    }

    private fun updateNavBar(screen: Screen, tabBar: AccessibilityNodeInfo?, tabs: List<Ig.TabNode>) {
        val nav = navBar ?: return
        val shouldShow = Prefs.get(this, Prefs.CUSTOM_NAV) &&
            tabBar != null &&
            tabs.size >= 2 &&
            !screen.keyboardVisible &&
            !screen.shadeOpen &&
            screen.igRoots.none { Ig.hasBottomSheet(it) }
        if (!shouldShow || tabBar == null) {
            nav.hide()
            return
        }

        val bounds = Rect()
        tabBar.getBoundsInScreen(bounds)
        if (bounds.width() <= 0 || bounds.height() <= 0) {
            nav.hide()
            return
        }

        val items = tabs
            .filter { it.type != Ig.Tab.REELS }
            .map { NavBarOverlay.Item(it.type, it.node.isSelected, it.index) }

        val night = currentNightMode()
        val known = barColor?.takeIf { barColorNight == night }
        if (known == null && !nav.isShowing) {
            // Сначала узнаём цвет настоящей панели, потом показываем свою.
            sampleBarColor(bounds, night)
            return
        }
        nav.show(bounds, items, known ?: guessColor(night))
    }

    private fun onOverlayTap(item: NavBarOverlay.Item, long: Boolean) {
        val screen = readScreen()
        val tabBar = screen.igRoots.firstNotNullOfOrNull { Ig.findTabBar(it) }
        val tabs = tabBar?.let { Ig.readTabs(it) } ?: emptyList()
        val target = tabs.firstOrNull {
            it.type == item.type && (item.type != Ig.Tab.OTHER || it.index == item.realIndex)
        }
        if (target == null) {
            DebugLog.log("Overlay tap ${item.type}: real tab not found")
            navBar?.hide()
            return
        }
        val action = if (long) AccessibilityNodeInfo.ACTION_LONG_CLICK else AccessibilityNodeInfo.ACTION_CLICK
        val ok = Ig.clickTarget(target.node).performAction(action)
        DebugLog.log("Overlay tap ${item.type} long=$long ok=$ok")
        scheduleEvaluate()
    }

    private fun dumpNow() {
        val text = Diagnostics.dumpWindows(this)
        val f = Diagnostics.saveDump(this, text)
        DebugLog.log("Saved ${f.name}")
        toast("Снимок сохранён: ${f.name}")
    }

    // ---------------------------------------------------------------------
    // Цвет панели

    private fun sampleBarColor(bounds: Rect, night: Int) {
        if (sampling) return
        sampling = true
        val done = { color: Int ->
            barColor = color
            barColorNight = night
            sampling = false
            DebugLog.log("Bar color = #%08X".format(color))
            scheduleEvaluate()
        }
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    val buffer = result.hardwareBuffer
                    val color = try {
                        val hw = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                        val bmp = hw?.copy(Bitmap.Config.ARGB_8888, false)
                        hw?.recycle()
                        if (bmp == null) {
                            guessColor(night)
                        } else {
                            val x = (bounds.left + 4).coerceIn(0, bmp.width - 1)
                            val y = (bounds.top + bounds.height() / 2).coerceIn(0, bmp.height - 1)
                            val c = bmp.getPixel(x, y) or 0xFF000000.toInt()
                            bmp.recycle()
                            c
                        }
                    } catch (e: Exception) {
                        guessColor(night)
                    } finally {
                        buffer.close()
                    }
                    done(color)
                }

                override fun onFailure(errorCode: Int) {
                    DebugLog.log("Screenshot failed: $errorCode")
                    done(guessColor(night))
                }
            })
        } catch (e: Exception) {
            DebugLog.log("Screenshot error: $e")
            done(guessColor(night))
        }
    }

    private fun currentNightMode(): Int =
        resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK

    private fun guessColor(night: Int): Int =
        if (night == Configuration.UI_MODE_NIGHT_YES) Color.BLACK else Color.WHITE

    // ---------------------------------------------------------------------

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun now() = SystemClock.uptimeMillis()

    companion object {
        private const val EVALUATE_DELAY_MS = 100L
        private const val ACTION_COOLDOWN_MS = 1200L
        private const val GRACE_MS = 800L
        private const val SCROLL_THRESHOLD_PX = 20
    }
}

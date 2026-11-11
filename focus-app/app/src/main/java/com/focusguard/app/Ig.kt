package com.focusguard.app

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Всё, что мы знаем о том, как устроен интерфейс Инстаграма.
 *
 * Инстаграм периодически переименовывает свои элементы, поэтому все
 * «опознавательные знаки» собраны здесь — если что-то перестанет работать,
 * чинить нужно в первую очередь этот файл (по снимку из кнопки DUMP).
 */
object Ig {
    const val PKG = "com.instagram.android"
    private const val ID_PREFIX = "$PKG:id/"

    /** Нижняя панель с вкладками. */
    private val TAB_BAR_IDS = listOf("tab_bar", "bottom_tab_bar", "main_tab_bar")

    /** Вкладки, по которым можно найти панель, если у неё самой нет известного id. */
    private val TAB_IDS = listOf("feed_tab", "clips_tab", "profile_tab", "search_tab", "direct_tab")

    /** Полноэкранный просмотрщик рилсов (и вкладка Reels, и рилс, открытый из директа). */
    val REELS_VIEWER_IDS = listOf(
        "clips_viewer_view_pager",
        "clips_viewer_container",
        "clips_viewer_root",
        "clips_video_container",
    )

    /** Шторки снизу (поделиться, комментарии и т.п.) — поверх них своё меню не рисуем. */
    private val BOTTOM_SHEET_IDS = listOf(
        "bottom_sheet_container",
        "bottom_sheet_container_view",
        "layout_container_bottom_sheet",
        "bottom_sheet_drag_handle",
        "bottom_sheet_drag_handle_prism",
    )

    enum class Tab { HOME, SEARCH, REELS, MESSAGES, CREATE, PROFILE, OTHER }

    class TabNode(val type: Tab, val node: AccessibilityNodeInfo, val index: Int)

    fun shortId(n: AccessibilityNodeInfo): String =
        n.viewIdResourceName?.substringAfter(":id/", "") ?: ""

    private fun findById(root: AccessibilityNodeInfo, name: String): AccessibilityNodeInfo? =
        try {
            root.findAccessibilityNodeInfosByViewId(ID_PREFIX + name)
                ?.firstOrNull { it.isVisibleToUser }
        } catch (e: Exception) {
            null
        }

    fun findTabBar(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        for (id in TAB_BAR_IDS) findById(root, id)?.let { return it }
        for (id in TAB_IDS) {
            val parent = findById(root, id)?.parent
            if (parent != null && parent.isVisibleToUser) return parent
        }
        return null
    }

    fun findReelsViewer(root: AccessibilityNodeInfo): AccessibilityNodeInfo? =
        REELS_VIEWER_IDS.firstNotNullOfOrNull { findById(root, it) }

    fun hasBottomSheet(root: AccessibilityNodeInfo): Boolean =
        BOTTOM_SHEET_IDS.any { findById(root, it) != null }

    /** Вкладки нижней панели в том порядке, в котором они стоят на экране. */
    fun readTabs(bar: AccessibilityNodeInfo): List<TabNode> {
        var container = bar
        var guard = 0
        while (container.childCount == 1 && guard++ < 4) {
            container = container.getChild(0) ?: break
        }
        val children = (0 until container.childCount)
            .mapNotNull { container.getChild(it) }
            .filter { it.isVisibleToUser }
        return children.mapIndexed { i, c ->
            TabNode(classify(c, isLast = i == children.lastIndex), c, i)
        }
    }

    fun classify(n: AccessibilityNodeInfo, isLast: Boolean): Tab {
        byId(shortId(n).lowercase())?.let { return it }
        byText(describe(n).lowercase())?.let { return it }
        // Последняя вкладка — почти всегда профиль (там аватарка и имя пользователя).
        return if (isLast) Tab.PROFILE else Tab.OTHER
    }

    /** Это нажатие было по вкладке Reels в нижней панели? */
    fun isReelsTabNode(n: AccessibilityNodeInfo): Boolean {
        val id = shortId(n).lowercase()
        if (id == "clips_tab") return true
        if (byText(describe(n).lowercase()) != Tab.REELS) return false
        var p = n.parent
        var depth = 0
        while (p != null && depth++ < 3) {
            if (shortId(p) in TAB_BAR_IDS) return true
            p = p.parent
        }
        return false
    }

    private fun describe(n: AccessibilityNodeInfo): String {
        val sb = StringBuilder()
        n.contentDescription?.let { sb.append(it).append(' ') }
        n.text?.let { sb.append(it).append(' ') }
        for (i in 0 until n.childCount) {
            val c = n.getChild(i) ?: continue
            c.contentDescription?.let { sb.append(it).append(' ') }
            c.text?.let { sb.append(it).append(' ') }
        }
        return sb.toString()
    }

    private fun byId(id: String): Tab? = when {
        id.isEmpty() -> null
        "clips" in id || "reels" in id -> Tab.REELS
        "feed" in id || "home" in id -> Tab.HOME
        "search" in id || "explore" in id -> Tab.SEARCH
        "direct" in id || "inbox" in id || "message" in id -> Tab.MESSAGES
        "creation" in id || "camera" in id || "create" in id -> Tab.CREATE
        "profile" in id -> Tab.PROFILE
        else -> null
    }

    private fun byText(t: String): Tab? = when {
        t.isBlank() -> null
        "reels" in t || "рилс" in t -> Tab.REELS
        "home" in t || "главн" in t -> Tab.HOME
        "search" in t || "explore" in t || "поиск" in t || "интересн" in t -> Tab.SEARCH
        "messag" in t || "direct" in t || "inbox" in t || "сообщ" in t || "директ" in t -> Tab.MESSAGES
        "create" in t || "new post" in t || "созда" in t || "публикац" in t -> Tab.CREATE
        "profile" in t || "профил" in t -> Tab.PROFILE
        else -> null
    }

    /** Если сама вкладка не «кликабельна», ищем внутри неё то, что можно нажать. */
    fun clickTarget(n: AccessibilityNodeInfo): AccessibilityNodeInfo {
        if (n.isClickable) return n
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(n)
        var seen = 0
        while (queue.isNotEmpty() && seen++ < 30) {
            val cur = queue.removeFirst()
            if (cur.isClickable) return cur
            for (i in 0 until cur.childCount) cur.getChild(i)?.let { queue.add(it) }
        }
        return n
    }
}

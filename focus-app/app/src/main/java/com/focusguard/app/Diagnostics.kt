package com.focusguard.app

import android.accessibilityservice.AccessibilityService
import android.content.ContentValues
import android.content.Context
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Снимки устройства экрана и отчёт для отладки. */
object Diagnostics {
    private const val MAX_DUMPS = 6
    private const val MAX_NODES = 5000

    private fun stamp() = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

    fun dumpWindows(service: AccessibilityService): String {
        val sb = StringBuilder()
        sb.append("# Dump ").append(stamp()).append('\n')
        val count = intArrayOf(0)
        val windows = try { service.windows } catch (e: Exception) { emptyList() }
        for (w in windows) {
            val r = Rect()
            w.getBoundsInScreen(r)
            sb.append("=== window type=").append(windowType(w.type))
                .append(" layer=").append(w.layer)
                .append(" active=").append(w.isActive)
                .append(" bounds=").append(r.toShortString())
                .append(" title=").append(w.title)
                .append('\n')
            val root = w.root ?: continue
            sb.append("    package=").append(root.packageName).append('\n')
            dumpNode(root, 1, sb, count)
        }
        return sb.toString()
    }

    private fun dumpNode(n: AccessibilityNodeInfo, depth: Int, sb: StringBuilder, count: IntArray) {
        if (count[0]++ > MAX_NODES || depth > 80) return
        val r = Rect()
        n.getBoundsInScreen(r)
        sb.append("  ".repeat(depth))
        sb.append(n.className?.toString()?.substringAfterLast('.') ?: "?")
        val id = Ig.shortId(n)
        if (id.isNotEmpty()) sb.append(" id=").append(id)
        n.contentDescription?.let { sb.append(" desc=\"").append(it.toString().take(40)).append('"') }
        n.text?.let {
            // Длинные тексты (например, сообщения) не сохраняем — только длину.
            if (it.length <= 24) sb.append(" text=\"").append(it).append('"')
            else sb.append(" text=[").append(it.length).append(" chars]")
        }
        sb.append(' ').append(r.toShortString())
        if (n.isClickable) sb.append(" CLICK")
        if (n.isLongClickable) sb.append(" LONG")
        if (n.isSelected) sb.append(" SELECTED")
        if (n.isScrollable) sb.append(" SCROLL")
        if (!n.isVisibleToUser) sb.append(" hidden")
        sb.append('\n')
        for (i in 0 until n.childCount) {
            n.getChild(i)?.let { dumpNode(it, depth + 1, sb, count) }
        }
    }

    private fun windowType(t: Int) = when (t) {
        AccessibilityWindowInfo.TYPE_APPLICATION -> "APP"
        AccessibilityWindowInfo.TYPE_INPUT_METHOD -> "KEYBOARD"
        AccessibilityWindowInfo.TYPE_SYSTEM -> "SYSTEM"
        AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY -> "OVERLAY"
        else -> "TYPE_$t"
    }

    private fun dumpDir(context: Context) = File(context.filesDir, "dumps").apply { mkdirs() }

    fun saveDump(context: Context, text: String): File {
        val dir = dumpDir(context)
        val f = File(dir, "dump-${stamp()}.txt")
        f.writeText(text)
        dir.listFiles()?.sortedBy { it.name }?.dropLast(MAX_DUMPS)?.forEach { it.delete() }
        return f
    }

    fun buildReport(context: Context): String {
        val sb = StringBuilder()
        sb.append("FocusGuard diagnostics ").append(stamp()).append('\n')
        sb.append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
            .append(", Android SDK ").append(Build.VERSION.SDK_INT).append('\n')
        val appVersion = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (e: Exception) { "?" }
        val igVersion = try {
            context.packageManager.getPackageInfo(Ig.PKG, 0).versionName
        } catch (e: Exception) { "not installed" }
        sb.append("FocusGuard: ").append(appVersion).append(", Instagram: ").append(igVersion).append('\n')
        sb.append("Settings: ")
        for (k in listOf(Prefs.CUSTOM_NAV, Prefs.BLOCK_REELS_TAB, Prefs.SINGLE_REEL, Prefs.DEBUG_BUTTON)) {
            sb.append(k).append('=').append(Prefs.get(context, k)).append(' ')
        }
        sb.append("\n\n## Log\n")
        DebugLog.snapshot().forEach { sb.append(it).append('\n') }
        val dumps = dumpDir(context).listFiles()?.sortedByDescending { it.name } ?: emptyList()
        for (f in dumps) {
            sb.append("\n\n## ").append(f.name).append('\n').append(f.readText())
        }
        if (dumps.isEmpty()) sb.append("\n(no screen dumps yet)\n")
        return sb.toString()
    }

    /** Сохраняет отчёт в «Загрузки/FocusGuard» и возвращает ссылку на файл. */
    fun exportToDownloads(context: Context, text: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "FocusGuard-diagnostics-${stamp()}.txt")
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/FocusGuard")
        }
        return try {
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return null
            context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
            uri
        } catch (e: Exception) {
            DebugLog.log("Export failed: $e")
            null
        }
    }
}

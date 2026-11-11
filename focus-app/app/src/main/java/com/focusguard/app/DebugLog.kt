package com.focusguard.app

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Короткий журнал последних решений службы — попадает в «диагностику». */
object DebugLog {
    private const val MAX = 300
    private val lines = ArrayDeque<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun log(msg: String) {
        Log.d("FocusGuard", msg)
        lines.addLast("${fmt.format(Date())}  $msg")
        while (lines.size > MAX) lines.removeFirst()
    }

    @Synchronized
    fun snapshot(): List<String> = lines.toList()
}

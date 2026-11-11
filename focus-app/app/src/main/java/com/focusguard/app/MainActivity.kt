package com.focusguard.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var setupCard: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.status)
        setupCard = findViewById(R.id.setup_card)

        findViewById<Button>(R.id.btn_accessibility).setOnClickListener { openAccessibilitySettings() }
        findViewById<Button>(R.id.btn_restricted).setOnClickListener { openAppInfo() }
        findViewById<Button>(R.id.btn_share).setOnClickListener { shareDiagnostics() }
        findViewById<Button>(R.id.btn_open_ig).setOnClickListener {
            val intent = packageManager.getLaunchIntentForPackage(Ig.PKG)
            if (intent != null) startActivity(intent) else toast("Инстаграм не установлен")
        }

        bindSwitch(R.id.sw_custom_nav, Prefs.CUSTOM_NAV)
        bindSwitch(R.id.sw_block_tab, Prefs.BLOCK_REELS_TAB)
        bindSwitch(R.id.sw_single_reel, Prefs.SINGLE_REEL)
        bindSwitch(R.id.sw_debug, Prefs.DEBUG_BUTTON)

        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) { "?" }
        findViewById<TextView>(R.id.version).text = "Версия $version"
    }

    override fun onResume() {
        super.onResume()
        val enabled = isServiceEnabled()
        status.setText(if (enabled) R.string.status_on else R.string.status_off)
        setupCard.visibility = if (enabled) View.GONE else View.VISIBLE
    }

    private fun bindSwitch(id: Int, key: String) {
        val sw = findViewById<Switch>(id)
        sw.isChecked = Prefs.get(this, key)
        sw.setOnCheckedChangeListener { _, checked -> Prefs.set(this, key, checked) }
    }

    private fun isServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val me = ComponentName(this, FocusService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    private fun openAccessibilitySettings() {
        val me = ComponentName(this, FocusService::class.java).flattenToString()
        try {
            // Сразу страница нашей службы (работает на Pixel)
            startActivity(
                Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
                    .putExtra(Intent.EXTRA_COMPONENT_NAME, me),
            )
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (e2: ActivityNotFoundException) {
                toast("Не получилось открыть настройки")
            }
        }
    }

    private fun openAppInfo() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")),
        )
    }

    private fun shareDiagnostics() {
        val report = Diagnostics.buildReport(this)
        val uri = Diagnostics.exportToDownloads(this, report)
        if (uri == null) {
            toast("Не получилось сохранить файл")
            return
        }
        toast("Сохранено в «Загрузки/FocusGuard»")
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(Intent.createChooser(send, getString(R.string.btn_share)))
        } catch (_: Exception) {
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}

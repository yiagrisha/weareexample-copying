package com.focusguard.feed

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject

/**
 * Инстаграм «без лишнего»: внутри открыт instagram.com, а скрипт и стили
 * из assets/ (clean.js и clean.css) вырезают Reels, рекламу и рекомендации.
 */
class FeedActivity : Activity() {

    private lateinit var root: FrameLayout
    private lateinit var web: WebView
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
    private lateinit var cleanScript: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val bg = if (night) Color.BLACK else Color.WHITE

        root = FrameLayout(this).apply { setBackgroundColor(bg) }
        web = WebView(this).apply { setBackgroundColor(bg) }
        root.addView(web, FrameLayout.LayoutParams(MATCH, MATCH))
        setContentView(root)

        // Отступы под строку состояния, жестовую панель и клавиатуру
        root.setOnApplyWindowInsetsListener { v, insets ->
            val b = insets.getInsets(
                WindowInsets.Type.systemBars() or WindowInsets.Type.ime() or WindowInsets.Type.displayCutout(),
            )
            v.setPadding(b.left, b.top, b.right, b.bottom)
            WindowInsets.CONSUMED
        }
        val light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        window.insetsController?.setSystemBarsAppearance(if (night) 0 else light, light)

        cleanScript = buildCleanScript()
        setupWebView()

        if (savedInstanceState == null || web.restoreState(savedInstanceState) == null) {
            web.loadUrl(START_URL)
        }
    }

    private fun buildCleanScript(): String {
        val css = assets.open("clean.css").bufferedReader().use { it.readText() }
        val js = assets.open("clean.js").bufferedReader().use { it.readText() }
        return "window.__FG_CSS = ${JSONObject.quote(css)};\n$js"
    }

    private fun setupWebView() {
        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            // Притворяемся обычным Chrome, иначе Инстаграм иногда урезает сайт.
            userAgentString = userAgentString
                .replace("; wv", "")
                .replace(Regex("Version/\\d+(\\.\\d+)* "), "")
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)

        // Скрипт чистки запускается ещё до того, как страница начнёт рисоваться.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(
                web, cleanScript, setOf("https://www.instagram.com", "https://instagram.com"),
            )
        }

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                val scheme = uri.scheme ?: return true
                // Не даём сайту перекинуть нас в официальное приложение Инстаграма.
                if (scheme != "http" && scheme != "https") return true
                val host = uri.host ?: return true
                if (!isAllowedHost(host)) {
                    openExternally(uri)
                    return true
                }
                val path = uri.path ?: ""
                if (path == "/reels" || path == "/reels/") {
                    view.loadUrl(START_URL)
                    return true
                }
                return false
            }

            override fun onPageFinished(view: WebView, url: String) {
                // Запасной вариант, если старый WebView не умеет document-start скрипты.
                if (isAllowedHost(Uri.parse(url).host ?: "")) {
                    view.evaluateJavascript(cleanScript, null)
                }
                CookieManager.getInstance().flush()
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams,
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                return try {
                    startActivityForResult(params.createIntent(), REQ_FILE)
                    true
                } catch (e: ActivityNotFoundException) {
                    fileCallback = null
                    false
                }
            }

            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                fullscreenView?.let { root.removeView(it) }
                fullscreenView = view
                fullscreenCallback = callback
                root.addView(view, FrameLayout.LayoutParams(MATCH, MATCH))
            }

            override fun onHideCustomView() {
                fullscreenView?.let { root.removeView(it) }
                fullscreenView = null
                fullscreenCallback = null
            }
        }
    }

    private fun isAllowedHost(host: String): Boolean {
        val h = host.lowercase()
        return ALLOWED_HOSTS.any { h == it || h.endsWith(".$it") }
    }

    private fun openExternally(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE))
        } catch (_: Exception) {
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQ_FILE) {
            fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data))
            fileCallback = null
            return
        }
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            fullscreenView != null -> fullscreenCallback?.onCustomViewHidden()
            web.canGoBack() -> web.goBack()
            else -> {
                @Suppress("DEPRECATION")
                super.onBackPressed()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
    }

    override fun onPause() {
        web.onPause()
        CookieManager.getInstance().flush()
        super.onPause()
    }

    override fun onDestroy() {
        (web.parent as? ViewGroup)?.removeView(web)
        web.destroy()
        super.onDestroy()
    }

    companion object {
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val REQ_FILE = 42
        private const val START_URL = "https://www.instagram.com/?variant=following"
        private val ALLOWED_HOSTS = listOf(
            "instagram.com",
            "cdninstagram.com",
            "facebook.com",
            "fbcdn.net",
            "fb.com",
            "fbsbx.com",
        )
    }
}

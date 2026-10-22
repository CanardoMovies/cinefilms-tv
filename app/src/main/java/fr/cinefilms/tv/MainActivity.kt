package fr.cinefilms.tv

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast

class MainActivity : Activity() {

    // === À MODIFIER SI TON DOMAINE CHANGE (puis recompiler l'APK) ===
    private val START_URL = "https://cinefilms.free.nf/tv_code.php"

    private lateinit var root: FrameLayout
    private lateinit var web: WebView
    private var customView: View? = null
    private var customCb: WebChromeClient.CustomViewCallback? = null
    private val handler = Handler(Looper.getMainLooper())
    private var lastBack = 0L

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUi()

        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        web = WebView(this)
        root.addView(web, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        setContentView(root)

        val s = web.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.mediaPlaybackRequiresUserGesture = false
        s.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        s.useWideViewPort = true
        s.loadWithOverviewMode = true
        s.userAgentString = s.userAgentString + " CineFilmsTV"

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(web, true)
        }

        web.setBackgroundColor(Color.BLACK)
        web.isFocusable = true
        web.isFocusableInTouchMode = true

        web.webViewClient = object : WebViewClient() {
            override fun onReceivedError(v: WebView, r: WebResourceRequest, e: WebResourceError) {
                if (r.isForMainFrame) showOffline()
            }
            override fun onPageFinished(v: WebView, url: String) {
                CookieManager.getInstance().flush()
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View, cb: CustomViewCallback) {
                if (customView != null) { cb.onCustomViewHidden(); return }
                customView = view; customCb = cb
                root.addView(view, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                web.visibility = View.GONE
            }
            override fun onHideCustomView() { exitCustomView() }
        }

        if (savedInstanceState == null) web.loadUrl(START_URL) else web.restoreState(savedInstanceState)
    }

    private fun exitCustomView() {
        customView?.let { root.removeView(it) }
        customView = null
        customCb?.onCustomViewHidden()
        customCb = null
        web.visibility = View.VISIBLE
    }

    private fun showOffline() {
        val html = "<html><body style='background:#0d0f1a;color:#fff;font-family:sans-serif;" +
            "display:flex;align-items:center;justify-content:center;height:100vh;margin:0;text-align:center'>" +
            "<div><h1>Pas de connexion</h1><p>Nouvelle tentative dans quelques secondes…</p></div></body></html>"
        web.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
        handler.postDelayed({ web.loadUrl(START_URL) }, 6000)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (customView != null) { exitCustomView(); return true }
            // Écran du code : un seul « Retour » ne quitte pas (le code serait perdu)
            if ((web.url ?: "").contains("tv_code")) {
                val now = System.currentTimeMillis()
                if (now - lastBack > 2500) {
                    lastBack = now
                    Toast.makeText(this, "Appuie encore sur Retour pour quitter", Toast.LENGTH_SHORT).show()
                    return true
                }
                return super.onKeyDown(keyCode, event)
            }
            if (web.canGoBack()) { web.goBack(); return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun hideSystemUi() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onPause() { super.onPause(); web.onPause(); CookieManager.getInstance().flush() }
    override fun onResume() { super.onResume(); web.onResume() }
    override fun onDestroy() { handler.removeCallbacksAndMessages(null); web.destroy(); super.onDestroy() }
}

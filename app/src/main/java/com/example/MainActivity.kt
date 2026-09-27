package com.example

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.view.Gravity
import android.graphics.Typeface
import android.content.res.ColorStateList
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File

class MainActivity : ComponentActivity() {

    private var webView: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Enable full hardware acceleration for 60/120fps smooth rendering
        window.setFlags(
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )

        val rootLayout = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(0xFFF8FAFC.toInt())
        }

        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            windowInsets
        }

        // Native instant branded splash placeholder (prevents any black or blank screen on cold start)
        val splashOverlay = LinearLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xFFF8FAFC.toInt())

            val iconView = TextView(context).apply {
                text = "📘"
                textSize = 58f
                gravity = Gravity.CENTER
            }
            addView(iconView)

            val titleView = TextView(context).apply {
                text = "Smart Timetable"
                textSize = 24f
                setTextColor(0xFF0F172A.toInt())
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(0, (14 * resources.displayMetrics.density).toInt(), 0, (4 * resources.displayMetrics.density).toInt())
            }
            addView(titleView)

            val subtitleView = TextView(context).apply {
                text = "Loading workspace..."
                textSize = 14f
                setTextColor(0xFF64748B.toInt())
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, (20 * resources.displayMetrics.density).toInt())
            }
            addView(subtitleView)

            val progressBar = ProgressBar(context).apply {
                indeterminateTintList = ColorStateList.valueOf(0xFF4F46E5.toInt())
                val pSize = (36 * resources.displayMetrics.density).toInt()
                layoutParams = LinearLayout.LayoutParams(pSize, pSize).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                }
            }
            addView(progressBar)
        }

        var splashDismissed = false
        fun dismissSplash() {
            if (!splashDismissed) {
                splashDismissed = true
                splashOverlay.animate()
                    .alpha(0f)
                    .setDuration(250)
                    .withEndAction {
                        (splashOverlay.parent as? ViewGroup)?.removeView(splashOverlay)
                    }
                    .start()
            }
        }

        val wv = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(0xFFF8FAFC.toInt())
            // Use hardware layer for smooth CSS animations and 60fps scrolling
            setLayerType(View.LAYER_TYPE_HARDWARE, null)

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                cacheMode = WebSettings.LOAD_DEFAULT
                loadsImagesAutomatically = true
                offscreenPreRaster = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }

            addJavascriptInterface(AndroidBridge(this@MainActivity), "AndroidBridge")

            webChromeClient = object : WebChromeClient() {
                override fun onJsAlert(
                    view: WebView?,
                    url: String?,
                    message: String?,
                    result: JsResult?
                ): Boolean {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Smart Timetable")
                        .setMessage(message ?: "")
                        .setPositiveButton(android.R.string.ok) { dialog, _ ->
                            dialog.dismiss()
                            result?.confirm()
                        }
                        .setOnCancelListener { result?.cancel() }
                        .show()
                    return true
                }

                override fun onJsConfirm(
                    view: WebView?,
                    url: String?,
                    message: String?,
                    result: JsResult?
                ): Boolean {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Confirm")
                        .setMessage(message ?: "")
                        .setPositiveButton(android.R.string.ok) { dialog, _ ->
                            dialog.dismiss()
                            result?.confirm()
                        }
                        .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                            dialog.dismiss()
                            result?.cancel()
                        }
                        .setOnCancelListener { result?.cancel() }
                        .show()
                    return true
                }

                override fun onJsPrompt(
                    view: WebView?,
                    url: String?,
                    message: String?,
                    defaultValue: String?,
                    result: JsPromptResult?
                ): Boolean {
                    val input = EditText(this@MainActivity).apply {
                        setText(defaultValue ?: "")
                        setSelection(text.length)
                    }
                    val container = FrameLayout(this@MainActivity).apply {
                        val padding = (18 * resources.displayMetrics.density).toInt()
                        setPadding(padding, 0, padding, 0)
                        addView(input)
                    }

                    AlertDialog.Builder(this@MainActivity)
                        .setTitle(message ?: "Enter Value")
                        .setView(container)
                        .setPositiveButton(android.R.string.ok) { dialog, _ ->
                            dialog.dismiss()
                            result?.confirm(input.text.toString())
                        }
                        .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                            dialog.dismiss()
                            result?.cancel()
                        }
                        .setOnCancelListener { result?.cancel() }
                        .show()
                    return true
                }

                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    Log.d("WebViewConsole", "${consoleMessage?.message()} [line ${consoleMessage?.lineNumber()}]")
                    return true
                }
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean = false

                override fun onPageCommitVisible(view: WebView?, url: String?) {
                    super.onPageCommitVisible(view, url)
                    dismissSplash()
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    dismissSplash()
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    Log.w("MainActivity", "WebView resource error: ${error?.description}")
                    dismissSplash()
                }

                override fun onRenderProcessGone(
                    view: WebView?,
                    detail: RenderProcessGoneDetail?
                ): Boolean {
                    view?.post {
                        view.loadUrl("file:///android_asset/index.html")
                    }
                    return true
                }
            }

            loadUrl("file:///android_asset/index.html")
        }

        webView = wv
        rootLayout.addView(wv)
        rootLayout.addView(splashOverlay)
        setContentView(rootLayout)

        // Safety fallback: ensure splash is never stuck if page loads unusually fast or slow
        splashOverlay.postDelayed({ dismissSplash() }, 1800)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val currentWv = webView ?: run {
                    finish()
                    return
                }

                currentWv.evaluateJavascript(
                    "(function() { " +
                            "var drawer = document.getElementById('mobile-drawer'); " +
                            "if (drawer && drawer.classList.contains('open')) { " +
                            "closeMobileMenu(); return true; " +
                            "} return false; " +
                            "})()"
                ) { result ->
                    if (result != "true") {
                        if (currentWv.canGoBack()) {
                            currentWv.goBack()
                        } else {
                            finish()
                        }
                    }
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        webView?.onResume()
    }

    override fun onPause() {
        webView?.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        webView?.let { wv ->
            (wv.parent as? ViewGroup)?.removeView(wv)
            wv.stopLoading()
            wv.destroy()
        }
        webView = null
        super.onDestroy()
    }

    inner class AndroidBridge(private val context: Context) {

        @JavascriptInterface
        fun printHtml(title: String, htmlContent: String) {
            runOnUiThread {
                val printWebView = WebView(context).apply {
                    setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                    settings.apply {
                        javaScriptEnabled = false
                        domStorageEnabled = false
                        allowFileAccess = true
                        cacheMode = WebSettings.LOAD_NO_CACHE
                    }
                }

                printWebView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                        val safeJobName = (if (title.isBlank()) "Timetable" else title)
                            .replace(Regex("[^a-zA-Z0-9_]"), "_")
                        val printAdapter = view.createPrintDocumentAdapter(safeJobName)

                        val printAttributes = PrintAttributes.Builder()
                            .setMediaSize(PrintAttributes.MediaSize.ISO_A4.asLandscape())
                            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                            .build()

                        printManager?.print(safeJobName, printAdapter, printAttributes)
                    }

                    override fun onRenderProcessGone(
                        view: WebView?,
                        detail: RenderProcessGoneDetail?
                    ): Boolean = true
                }

                printWebView.loadDataWithBaseURL(
                    "file:///android_asset/",
                    htmlContent,
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        }
    }
}

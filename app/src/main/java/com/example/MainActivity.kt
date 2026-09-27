package com.example

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : ComponentActivity() {

    private var webView: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val rootLayout = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            windowInsets
        }

        val wv = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setLayerType(View.LAYER_TYPE_SOFTWARE, null)

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
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean = false

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
        setContentView(rootLayout)

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

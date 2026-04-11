package com.lifelogger.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.lifelogger.R
import com.lifelogger.config.AppConfig

/**
 * Full-screen WebView that loads the speaker-naming UI served by the backend.
 * A [JavascriptInterface] named "WebBridge" lets the page notify the app when
 * naming is complete, which triggers a back-stack pop.
 *
 * Security note: JS is only enabled here to render our own trusted backend page.
 */
class SpeakerNamingFragment : Fragment() {

    private var webView: WebView? = null
    private var errorLayout: LinearLayout? = null
    private var errorText: TextView? = null

    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            val wv = webView
            if (wv != null && wv.canGoBack()) {
                wv.goBack()
            } else {
                isEnabled = false
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_speaker_naming, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        errorLayout = view.findViewById(R.id.layout_error)
        errorText = view.findViewById(R.id.text_error)
        view.findViewById<Button>(R.id.btn_retry).setOnClickListener { loadPage() }

        webView = view.findViewById<WebView>(R.id.webview_speakers).apply {
            settings.javaScriptEnabled = true  // Required: renders our backend UI
            settings.domStorageEnabled = true  // Required for fetch API / localStorage
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            webViewClient = object : WebViewClient() {
                override fun onReceivedError(
                    wv: WebView,
                    errorCode: Int,
                    description: String,
                    failingUrl: String,
                ) {
                    showError("Could not load page (error $errorCode: $description)\n\nURL: $failingUrl")
                }

                override fun onReceivedError(
                    wv: WebView,
                    request: WebResourceRequest,
                    error: android.webkit.WebResourceError,
                ) {
                    if (request.isForMainFrame) {
                        showError(
                            "Could not connect to server\n\n" +
                            "${error.description}\n\n" +
                            "URL: ${request.url}\n\n" +
                            "Make sure the backend is running and your phone is on the same WiFi."
                        )
                    }
                }

                override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                    errorLayout?.visibility = View.GONE
                }
            }
            addJavascriptInterface(WebBridge(), "WebBridge")
        }
        loadPage()

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)
    }

    private fun loadPage() {
        errorLayout?.visibility = View.GONE
        webView?.loadUrl(AppConfig.SPEAKERS_UI_URL)
    }

    private fun showError(message: String) {
        webView?.post {
            errorText?.text = message
            errorLayout?.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        webView?.apply {
            removeJavascriptInterface("WebBridge")
            destroy()
        }
        webView = null
        errorLayout = null
        errorText = null
        super.onDestroyView()
    }

    /** Exposed to the web page as window.WebBridge.onNamingComplete(). */
    inner class WebBridge {
        @JavascriptInterface
        fun onNamingComplete() {
            // Post to main thread — JS interface callbacks arrive on a background thread
            webView?.post {
                findNavController().popBackStack()
            }
        }
    }
}

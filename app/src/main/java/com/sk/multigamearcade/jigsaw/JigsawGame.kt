package com.sk.multigamearcade.jigsaw

import android.annotation.SuppressLint
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun JigsawGameScreen(
    onExitGame: () -> Unit = {}
) {
    AndroidView(
        factory = { context ->
            val assetLoader = WebViewAssetLoader.Builder()
                .setDomain("appassets.androidplatform.net")
                .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                .addPathHandler("/", WebViewAssetLoader.AssetsPathHandler(context))
                .build()

            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                
                setLayerType(View.LAYER_TYPE_HARDWARE, null)

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = true
                    allowContentAccess = true
                    allowFileAccessFromFileURLs = true
                    allowUniversalAccessFromFileURLs = true
                    mediaPlaybackRequiresUserGesture = false
                    
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    setSupportZoom(false)
                    builtInZoomControls = false
                }

                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        return try {
                            request?.url?.let { assetLoader.shouldInterceptRequest(it) }
                        } catch (_: Exception) {
                            null
                        }
                    }
                }
                
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        Log.d(
                            "JigsawGameJS",
                            "${consoleMessage?.message()} -- Line ${consoleMessage?.lineNumber()} of ${consoleMessage?.sourceId()}"
                        )
                        return true
                    }
                }

                loadUrl("https://appassets.androidplatform.net/assets/jigsaw_game/index.html")
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

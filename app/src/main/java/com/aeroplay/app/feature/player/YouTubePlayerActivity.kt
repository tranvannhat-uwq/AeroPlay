package com.aeroplay.app.feature.player

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.aeroplay.app.data.youtube.YouTubePolicyFeatureFlags

/** Official embedded player only. No OAuth credentials, stream extraction or background playback. */
@dagger.hilt.android.AndroidEntryPoint
class YouTubePlayerActivity : androidx.activity.ComponentActivity() {
    @javax.inject.Inject lateinit var playback: com.aeroplay.app.playback.controller.PlaybackControllerManager
    private lateinit var webView: WebView
    private var videoId = ""

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        videoId = intent.getStringExtra("videoId").orEmpty()
        if (!YouTubePolicyFeatureFlags.apiOnlyMode || !VIDEO_ID.matches(videoId)) {
            finish()
            return
        }
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xff101014.toInt()) }
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(layout) { view, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        layout.addView(Button(this).apply { text = "‹ Quay lại AeroPlay"; setOnClickListener { finish() } })
        layout.addView(TextView(this).apply {
            text = "YouTube • Trình phát chính thức\nChạm nút phát trong khung video. Video sẽ dừng khi chuyển ứng dụng."
            setTextColor(0xffffffff.toInt()); setPadding(20, 12, 20, 12)
        })
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    if (!request.isForMainFrame) return false
                    val uri = request.url
                    if (uri.scheme == "https" && uri.host == "www.youtube.com" && uri.path == "/embed/$videoId") return false
                    // Links from the official player's UI open only on an explicit user gesture.
                    if (request.hasGesture() && uri.scheme in listOf("https", "http")) openExternal(uri)
                    return true
                }
            }
        }
        layout.addView(webView, LinearLayout.LayoutParams(-1, 0, 1f))
        layout.addView(Button(this).apply {
            text = "Video không cho nhúng? Mở bằng YouTube"
            setOnClickListener { openExternal(Uri.parse("https://www.youtube.com/watch?v=$videoId")) }
        })
        setContentView(layout)
    }

    override fun onStart() {
        super.onStart()
        playback.playerFlow.value?.pause()
    }

    override fun onPause() {
        // Unload rather than leaving a hidden player audible after screen lock or app switching.
        if (::webView.isInitialized) { webView.loadUrl("about:blank"); webView.onPause() }
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized) {
            webView.onResume()
            webView.loadUrl("https://www.youtube.com/embed/$videoId?playsinline=1&autoplay=0",
                mapOf("Referer" to "https://${packageName.lowercase()}/"))
        }
    }

    override fun onDestroy() {
        if (::webView.isInitialized) { (webView.parent as? android.view.ViewGroup)?.removeView(webView); webView.destroy() }
        super.onDestroy()
    }

    private fun openExternal(uri: Uri) {
        try { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
        catch (_: android.content.ActivityNotFoundException) { Toast.makeText(this, "Không có trình duyệt để mở liên kết.", Toast.LENGTH_LONG).show() }
    }

    companion object {
        private val VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")

        fun open(context: Context, urlOrId: String): Boolean {
            val uri = Uri.parse(urlOrId)
            val id = if (VIDEO_ID.matches(urlOrId)) urlOrId else if (YouTubePolicyFeatureFlags.isYouTubeUrl(urlOrId)) {
                if (uri.host == "youtu.be") uri.pathSegments.firstOrNull()
                else uri.getQueryParameter("v") ?: uri.pathSegments.takeIf { it.firstOrNull() in listOf("shorts", "embed", "live") }?.getOrNull(1)
            } else null
            if (id == null || !VIDEO_ID.matches(id)) return false
            context.startActivity(Intent(context, YouTubePlayerActivity::class.java)
                .putExtra("videoId", id).addFlags(if (context is Activity) 0 else Intent.FLAG_ACTIVITY_NEW_TASK))
            return true
        }
    }
}

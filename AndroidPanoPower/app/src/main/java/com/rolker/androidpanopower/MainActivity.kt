package com.rolker.androidpanopower

import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    // JSBridge: 提供给网页调用的接口
    inner class JsBridge {
        // 获取最近分享的5张图片（返回 base64 列表，主线程调用）
        @android.webkit.JavascriptInterface
        fun getRecentImages(): String {
            val images = mutableListOf<String>()
            try {
                val projection = arrayOf(
                    android.provider.MediaStore.Images.Media._ID,
                    android.provider.MediaStore.Images.Media.DATE_ADDED
                )
                val sortOrder = "${android.provider.MediaStore.Images.Media.DATE_ADDED} DESC LIMIT 5"
                val cursor = this@MainActivity.contentResolver.query(
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection, null, null, sortOrder
                )
                cursor?.use {
                    val idCol = it.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media._ID)
                    while (it.moveToNext()) {
                        val id = it.getLong(idCol)
                        val uri = android.content.ContentUris.withAppendedId(
                            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                        val input = this@MainActivity.contentResolver.openInputStream(uri)
                        val bytes = input?.readBytes()
                        input?.close()
                        if (bytes != null) {
                            val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                            images.add("data:image/jpeg;base64,$base64")
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return android.util.Log.d("JSBridge", "Recent images: ${images.size}").let {
                org.json.JSONArray(images).toString()
            }
        }
        @android.webkit.JavascriptInterface
        fun shareImageFromWeb(imageUrl: String?, fileName: String?) {
            runOnUiThread {
                if (imageUrl.isNullOrBlank()) {
                    android.widget.Toast.makeText(this@MainActivity, "图片地址无效", android.widget.Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                if (fileName.isNullOrBlank()) {
                    android.widget.Toast.makeText(this@MainActivity, "文件名无效", android.widget.Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                // 下载图片到 cache 或保存 base64
                Thread {
                    try {
                        val file = java.io.File(cacheDir, fileName)
                        if (imageUrl.startsWith("data:image")) {
                            // base64 data url
                            val base64Data = imageUrl.substringAfter(",")
                            val bytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                            java.io.FileOutputStream(file).use { output ->
                                output.write(bytes)
                            }
                        } else {
                            // 普通 url
                            val url = java.net.URL(imageUrl)
                            val conn = url.openConnection()
                            conn.connect()
                            val input = conn.getInputStream()
                            java.io.FileOutputStream(file).use { output ->
                                input.copyTo(output)
                            }
                        }
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            this@MainActivity,
                            "$packageName.fileprovider",
                            file
                        )
                        runOnUiThread {
                            shareImageToWeChat(uri)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        runOnUiThread {
                            android.widget.Toast.makeText(this@MainActivity, "图片下载失败", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }.start()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 设置状态栏为黑色，适配全面屏
        window.statusBarColor = android.graphics.Color.parseColor("#0A0814")
        window.navigationBarColor = android.graphics.Color.parseColor("#0A0814")
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val webView = findViewById<WebView>(R.id.webView)

        // === 1. 配置 WebView 设置 ===
        val settings = webView.settings
        // 开启 JavaScript（必须，否则远程脚本不执行）
        settings.javaScriptEnabled = true
        // 开启 DOM Storage（很多现代网页库依赖这个）
        settings.domStorageEnabled = true
        // === 关键点：解决本地文件访问远程资源的跨域问题 ===
        // 允许 file:// 协议的页面访问其他 file:// 资源
        settings.allowFileAccessFromFileURLs = true
        // 允许 file:// 协议的页面访问 http/https 资源 (这是最重要的)
        settings.allowUniversalAccessFromFileURLs = true
        // 保持在 App 内打开链接，而不是跳到浏览器
        webView.webViewClient = WebViewClient()
        // 注入 JSBridge
        webView.addJavascriptInterface(JsBridge(), "AndroidBridge")
        // === 2. 加载本地 HTML ===
        // 注意写法：file:///android_asset/文件名
        // (注意 asset 是单数，尽管文件夹叫 assets)
        webView.loadUrl("file:///android_asset/index.html")
    }

    // 将 assets 目录下图片复制到 cache 并返回 content:// uri
    private fun copyAssetToCacheAndGetUri(assetFileName: String): android.net.Uri? {
        return try {
            val file = java.io.File(cacheDir, assetFileName)
            if (!file.exists()) {
                assets.open(assetFileName).use { input ->
                    java.io.FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            androidx.core.content.FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                file
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // 分享图片到微信
    private fun shareImageToWeChat(imageUri: android.net.Uri) {
        val intent = android.content.Intent().apply {
            action = android.content.Intent.ACTION_SEND
            type = "image/*"
            putExtra(android.content.Intent.EXTRA_STREAM, imageUri)
            setPackage("com.tencent.mm") // 只显示微信
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "未检测到微信或分享失败", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
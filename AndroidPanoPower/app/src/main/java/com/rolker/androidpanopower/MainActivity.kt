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
                val cacheFiles = cacheDir.listFiles { file ->
                    file.isFile && (file.name.endsWith(".jpg") || file.name.endsWith(".jpeg") || file.name.endsWith(".png"))
                }?.sortedByDescending { it.lastModified() }?.take(5)
                cacheFiles?.forEach { file ->
                    val bytes = file.readBytes()
                    val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                    val mimeType = when {
                        file.name.endsWith(".png") -> "image/png"
                        else -> "image/jpeg"
                    }
                    images.add("data:$mimeType;base64,$base64")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return android.util.Log.d("JSBridge", "Recent images: ${images.size}").let {
                org.json.JSONArray(images).toString()
            }
        }
        @android.webkit.JavascriptInterface
        fun deleteAllGeneratedResources(): Boolean {
            return try {
                val cacheFiles = cacheDir.listFiles()
                cacheFiles?.forEach { file ->
                    // 只删除本 app 生成的图片（可根据命名规则过滤）
                    if (file.isFile && (file.name.endsWith(".jpg") || file.name.endsWith(".jpeg") || file.name.endsWith(".png"))) {
                        file.delete()
                    }
                }
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
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
                        var needWrite = true
                        if (imageUrl.startsWith("data:image")) {
                            // base64 data url
                            val base64Data = imageUrl.substringAfter(",")
                            if (file.exists()) {
                                needWrite = false
                                // // 校验缓存内容
                                // val oldBytes = file.readBytes()
                                // val newBytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                                // if (oldBytes.contentEquals(newBytes)) {
                                //     needWrite = false
                                // }
                            }
                            if (needWrite) {
                                val bytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                                java.io.FileOutputStream(file).use { output ->
                                    output.write(bytes)
                                }
                            }
                        } else {
                            // 普通 url，不缓存内容
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
        // @android.webkit.JavascriptInterface
        // // TODO: 暂时有问题： webview 的 img src 是 base64 的时候截图出来是空白
        // fun captureStageAndShare(left: String, top: String, width: String, height: String, fileName: String?) {
        //     runOnUiThread {
        //         val webView = findViewById<WebView>(R.id.webView)
        //         // 1. 获取元素位置和大小
        //         try {
        //             val leftFloat = left.toFloat()
        //             val topFloat = top.toFloat()
        //             val widthFloat = width.toFloat()
        //             val heightFloat = height.toFloat()
        //             val dpr = 1; // resources.displayMetrics.density
        //             // 2. 截图整个 WebView
        //             val bitmap = android.graphics.Bitmap.createBitmap(
        //                 webView.width, webView.height, android.graphics.Bitmap.Config.ARGB_8888
        //             )
        //             val canvas = android.graphics.Canvas(bitmap)
        //             webView.draw(canvas)
        //             // 3. 裁剪目标区域
        //             val cropLeft = (leftFloat * dpr).toInt()
        //             val cropTop = (topFloat * dpr).toInt()
        //             val cropWidth = (widthFloat * dpr).toInt()
        //             val cropHeight = (heightFloat * dpr).toInt()
        //             // Ensure crop area is within bitmap bounds
        //             val safeCropLeft = cropLeft.coerceIn(0, bitmap.width - 1)
        //             val safeCropTop = cropTop.coerceIn(0, bitmap.height - 1)
        //             val safeCropWidth = cropWidth.coerceAtMost(bitmap.width - safeCropLeft)
        //             val safeCropHeight = cropHeight.coerceAtMost(bitmap.height - safeCropTop)
        //             val cropped = android.graphics.Bitmap.createBitmap(
        //                 bitmap, safeCropLeft, safeCropTop, safeCropWidth, safeCropHeight
        //             )
        //             // 4. 保存/分享/显示
        //             // ...保存到 cache、分享、弹窗等
        //             // 例如保存到 cache 并分享
        //             val file = java.io.File(cacheDir, fileName ?: "stage_capture_${System.currentTimeMillis()}.png")
        //             java.io.FileOutputStream(file).use { out ->
        //                 cropped.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
        //             }
        //             // 可调用 shareImageToWeChat 或弹窗预览
        //             val uri = androidx.core.content.FileProvider.getUriForFile(
        //                 this@MainActivity, "$packageName.fileprovider", file
        //             )
        //             shareImageToWeChat(uri)
        //         } catch (e: Exception) {
        //             e.printStackTrace()
        //                 runOnUiThread {
        //                     android.widget.Toast.makeText(this@MainActivity, "图片分享失败", android.widget.Toast.LENGTH_SHORT).show()
        //                 }
        //         }
        //     }
        // }
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

    // // 将 assets 目录下图片复制到 cache 并返回 content:// uri
    // private fun copyAssetToCacheAndGetUri(assetFileName: String): android.net.Uri? {
    //     return try {
    //         val file = java.io.File(cacheDir, assetFileName)
    //         if (!file.exists()) {
    //             assets.open(assetFileName).use { input ->
    //                 java.io.FileOutputStream(file).use { output ->
    //                     input.copyTo(output)
    //                 }
    //             }
    //         }
    //         androidx.core.content.FileProvider.getUriForFile(
    //             this,
    //             "$packageName.fileprovider",
    //             file
    //         )
    //     } catch (e: Exception) {
    //         e.printStackTrace()
    //         null
    //     }
    // }

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
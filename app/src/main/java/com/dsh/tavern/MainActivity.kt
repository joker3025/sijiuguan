package com.dsh.tavern

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.dsh.tavern.data.Prefs
import com.dsh.tavern.ui.AppRoot
import com.dsh.tavern.ui.MainViewModel
import com.dsh.tavern.ui.TavernTheme

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Prefs(this).load().hideInRecents) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        handleIntent(intent)
        setContent {
            val ui by vm.ui.collectAsState()
            TavernTheme(themeMode = ui.settings.themeMode) {
                AppRoot(ui = ui, vm = vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val uri: Uri? = when (intent.action) {
            Intent.ACTION_SEND -> {
                val extra: Uri? = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    val u: Uri? = intent.getParcelableExtra(Intent.EXTRA_STREAM)
                    u
                }
                extra ?: intent.data
            }
            Intent.ACTION_VIEW -> intent.data
            else -> null
        }
        if (uri != null) {
            if (isSourceText(uri, intent)) {
                vm.importSourceFromUri(uri)
            } else {
                vm.importFromUri(uri)
            }
        }
    }

    /** TXT/EPUB 走工坊原文分析；图片/JSON 走角色卡导入（图片不是卡文件时自动改用视觉模型读图）。 */
    private fun isSourceText(uri: Uri, intent: Intent): Boolean {
        val name = displayName(uri).lowercase()
        // 明确是卡文件/图片的，先交给角色卡导入流程
        val cardLike = name.endsWith(".json") || name.endsWith(".png") ||
            name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".webp")
        if (cardLike) return false
        val mime = intent.type ?: contentResolver.getType(uri)
        if (mime == "text/plain" || mime == "application/epub+zip") {
            return true
        }
        if (mime == "application/octet-stream") {
            return name.endsWith(".txt") || name.endsWith(".epub")
        }
        return name.endsWith(".txt") || name.endsWith(".epub")
    }

    private fun displayName(uri: Uri): String {
        return try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: uri.lastPathSegment ?: ""
        } catch (e: Exception) {
            uri.lastPathSegment ?: ""
        }
    }
}

package com.projectathena.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.projectathena.app.data.Book

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleSharedText(intent)
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            AthenaTheme(darkTheme = state.darkMode) {
                AthenaApp(
                    viewModel = viewModel,
                    onOpenBook = ::openBook,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSharedText(intent)
    }

    private fun handleSharedText(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val source = intent.getStringExtra(Intent.EXTRA_REFERRER_NAME)
            ?: intent.`package`
        viewModel.captureSharedText(text, source)
    }

    private fun openBook(book: Book) {
        val uri = book.uri.toUri()
        val baseIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, book.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val preferredPackage = MOON_PACKAGES.firstOrNull { packageName ->
            packageManager.getLaunchIntentForPackage(packageName) != null
        }

        val opened = runCatching {
            startActivity(
                if (preferredPackage != null) {
                    Intent(baseIntent).setPackage(preferredPackage)
                } else {
                    Intent.createChooser(baseIntent, "Read with Moon+ Reader")
                },
            )
        }.recoverCatching {
            startActivity(Intent.createChooser(baseIntent, "Open ebook"))
        }.isSuccess

        if (opened) {
            viewModel.markOpened(book)
        } else {
            showToast("No ebook reader can open this file")
        }
    }

    private fun Context.showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    companion object {
        private val MOON_PACKAGES = listOf(
            "com.flyersoft.moonreaderp",
            "com.flyersoft.moonreader",
        )
    }
}

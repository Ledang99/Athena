package com.projectathena.app

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.projectathena.app.data.EbookRepository

data class ViewerApp(
    val packageName: String,
    val label: String,
)

fun PackageManager.installedEbookViewers(): List<ViewerApp> {
    val candidates = linkedMapOf<String, ViewerApp>()
    listOf(EbookRepository.PDF_MIME, EbookRepository.EPUB_MIME).forEach { mimeType ->
        val probe = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse("content://project.athena.probe/book"), mimeType)
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY).forEach { resolve ->
            val packageName = resolve.activityInfo.packageName
            if (packageName == "com.projectathena.app") return@forEach
            candidates.putIfAbsent(
                packageName,
                ViewerApp(
                    packageName = packageName,
                    label = resolve.loadLabel(this).toString(),
                ),
            )
        }
    }
    return candidates.values.sortedBy { it.label.lowercase() }
}

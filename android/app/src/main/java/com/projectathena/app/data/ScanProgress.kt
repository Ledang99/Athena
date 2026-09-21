package com.projectathena.app.data

data class ScanProgress(
    val phase: String = "Preparing",
    val current: Int = 0,
    val total: Int = 0,
    val currentFile: String? = null,
    val currentFolder: String? = null,
) {
    val fraction: Float
        get() = if (total > 0) {
            (current.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

    val isDeterminate: Boolean
        get() = total > 0
}

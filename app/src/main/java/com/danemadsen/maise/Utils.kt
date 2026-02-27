package com.danemadsen.maise

import android.content.Context
import java.io.File

/** Copy an asset to the app's files directory (cached; not re-copied on subsequent runs). */
fun copyAssetToFile(context: Context, assetPath: String): File {
    val dest = File(context.filesDir, assetPath)
    if (!dest.exists()) {
        dest.parentFile?.mkdirs()
        context.assets.open(assetPath).use { input ->
            dest.outputStream().use { input.copyTo(it) }
        }
    }
    return dest
}

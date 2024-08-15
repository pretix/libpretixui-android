package eu.pretix.libpretixui.android.questions

import android.content.Context
import android.os.Build
import java.io.File

fun getTmpDir(ctx: Context): File {
    val mediaDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        ctx.externalMediaDirs.firstOrNull()
    } else {
        ctx.filesDir
    }
    val baseDir = if (mediaDir != null && mediaDir.exists()) mediaDir else ctx.filesDir

    val outDir = File(baseDir, "libpretixui-tmp")
    if (!outDir.exists()) {
        outDir.mkdirs()
    }
    return outDir
}

fun cleanOldFiles(ctx: Context) {
    val files = getTmpDir(ctx).listFiles { file, s -> s.startsWith("20") } ?: emptyArray()
    for (file in files) {
        if (System.currentTimeMillis() - file.lastModified() > 3600 * 1000 * 24 * 7) {  // 7 days
            file.delete()
        }
    }
}
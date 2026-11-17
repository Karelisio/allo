package com.karelisio.allo.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/** Fonds d'écran d'appel par contact, copiés dans le stockage privé de l'appli (redimensionnés). */
object CallWallpapers {
    private fun dir(ctx: Context) = File(ctx.filesDir, "call_backgrounds").apply { mkdirs() }

    private fun fileFor(ctx: Context, lookupKey: String): File {
        val hash = MessageDigest.getInstance("SHA-1").digest(lookupKey.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(dir(ctx), "$hash.jpg")
    }

    /** URI locale (avec horodatage pour invalider le cache d'images) ou null. */
    fun uriFor(ctx: Context, lookupKey: String): String? {
        val f = fileFor(ctx, lookupKey)
        return if (f.exists()) Uri.fromFile(f).buildUpon().appendQueryParameter("v", f.lastModified().toString()).build().toString() else null
    }

    suspend fun set(ctx: Context, lookupKey: String, source: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, o) }
            var sample = 1
            while (maxOf(o.outWidth, o.outHeight) / (sample * 2) >= 1600) sample *= 2
            val bmp = ctx.contentResolver.openInputStream(source)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@runCatching false
            fileFor(ctx, lookupKey).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            true
        }.getOrDefault(false)
    }

    fun remove(ctx: Context, lookupKey: String) { fileFor(ctx, lookupKey).delete() }
}

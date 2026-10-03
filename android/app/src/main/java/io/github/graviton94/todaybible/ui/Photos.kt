package io.github.graviton94.todaybible.ui

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.File

/**
 * 노트 사진 간직하기: 긴 변 2000px JPEG 로 줄여 앱 안에 (장마다 한 장),
 * 그리고 사진 앱의 ‘하루의 성경’ 앨범에도 사본 (앱을 지워도 남고, 구글 포토가 쓰면 함께 백업). 권한 없이 (안드로이드 10+).
 */
object Photos {
    private const val MAX = 2000

    fun keep(ctx: Context, uri: Uri, file: File, title: String) {
        val bmp = decode(ctx, uri) ?: return
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 86, it) }
        if (Build.VERSION.SDK_INT >= 29) runCatching {
            val v = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "${title.replace(' ', '_')}_${System.currentTimeMillis()}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/HaruBible")
            }
            ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v)?.let { out ->
                ctx.contentResolver.openOutputStream(out)?.use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            }
        }
        bmp.recycle()
    }

    private fun decode(ctx: Context, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX) sample *= 2
        val raw = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
        val long = maxOf(raw.width, raw.height)
        if (long <= MAX) return raw
        val k = MAX.toFloat() / long
        return Bitmap.createScaledBitmap(raw, (raw.width * k).toInt(), (raw.height * k).toInt(), true).also { if (it != raw) raw.recycle() }
    }

    /** 이 장의 노트 사진 (없으면 null). */
    fun of(ctx: Context, trId: String, book: Int, chapter: Int): File? = File(ctx.filesDir, "photos/${trId}_${book + 1}_$chapter.jpg").takeIf { it.exists() }
}

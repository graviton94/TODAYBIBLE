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
 * 노트 사진 간직하기: 긴 변 2000px 로 줄여 앱 안에 (장마다 한 장),
 * 그리고 사진 앱의 ‘하루의 성경’ 앨범에도 사본 (앱을 지워도 남고, 구글 포토가 쓰면 함께 백업). 권한 없이 (안드로이드 10+).
 */
object Photos {
    private const val MAX = 2000

    /**
     * 노트 사진 한 장 간직: 긴 변 2000px · 글씨가 또렷하게 (밝기 고르기) · 안드로이드 11+ 는 WebP (같은 화질에 더 작게).
     * 돌려주는 값: 앱 안에 저장한 파일.
     */
    fun keep(ctx: Context, uri: Uri, trId: String, book: Int, chapter: Int, title: String): File? {
        val raw = decode(ctx, uri) ?: return null
        val bmp = clarify(raw)
        of(ctx, trId, book, chapter)?.delete()
        val file = target(ctx, trId, book, chapter); file.parentFile?.mkdirs()
        file.outputStream().use { if (Build.VERSION.SDK_INT >= 30) bmp.compress(Bitmap.CompressFormat.WEBP_LOSSY, 82, it) else bmp.compress(Bitmap.CompressFormat.JPEG, 84, it) }
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
        return file
    }

    private fun target(ctx: Context, trId: String, book: Int, chapter: Int) =
        File(ctx.filesDir, "photos/${trId}_${book + 1}_$chapter.${if (Build.VERSION.SDK_INT >= 30) "webp" else "jpg"}")

    /**
     * 글씨가 또렷하게: 밝기 분포의 아래 1% · 위 1% 를 검정 · 흰색으로 늘림 (색은 그대로).
     * 누런 종이는 희게, 흐린 연필은 진하게.
     */
    private fun clarify(src: Bitmap): Bitmap {
        val w = src.width; val h = src.height
        val px = IntArray(w * h); src.getPixels(px, 0, w, 0, 0, w, h)
        val hist = IntArray(256)
        for (p in px) hist[((p shr 16 and 255) * 77 + (p shr 8 and 255) * 150 + (p and 255) * 29) shr 8]++
        fun at(q: Double): Int { var acc = 0; val goal = (px.size * q).toInt(); for (i in 0..255) { acc += hist[i]; if (acc >= goal) return i }; return 255 }
        val lo = at(0.01); val hi = at(0.99).coerceAtLeast(lo + 32)
        val k = 255f / (hi - lo)
        fun f(v: Int) = ((v - lo) * k).toInt().coerceIn(0, 255)
        for (i in px.indices) { val p = px[i]; px[i] = (0xFF shl 24) or (f(p shr 16 and 255) shl 16) or (f(p shr 8 and 255) shl 8) or f(p and 255) }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); out.setPixels(px, 0, w, 0, 0, w, h)
        if (out != src) src.recycle()
        return out
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
    fun of(ctx: Context, trId: String, book: Int, chapter: Int): File? =
        listOf("webp", "jpg").map { File(ctx.filesDir, "photos/${trId}_${book + 1}_$chapter.$it") }.firstOrNull { it.exists() }
}

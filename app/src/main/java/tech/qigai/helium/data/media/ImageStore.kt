package tech.qigai.helium.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tech.qigai.helium.chat.ImageScale
import tech.qigai.helium.data.llm.ImageData
import tech.qigai.helium.data.llm.ImageLoader
import java.io.File
import java.util.Base64
import java.util.UUID

/**
 * Chat image attachments. Every picked or captured photo is decoded, EXIF-rotated, downscaled so its longest edge
 * is at most 1568 px (the size Anthropic and Gemini recommend; larger images are only resized server-side) and
 * re-encoded as JPEG into `files/attachments/`. Events store only the file name.
 */
class ImageStore(private val context: Context) : ImageLoader {
    private val dir: File get() = File(context.filesDir, "attachments").apply { mkdirs() }

    fun file(ref: String): File = File(dir, File(ref).name)

    /** A fresh content:// target for ActivityResultContracts.TakePicture. */
    fun newCameraUri(): Uri {
        val shots = File(context.cacheDir, "camera").apply { mkdirs() }
        val f = File(shots, "shot-${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.files", f)
    }

    /** Imports [uri] (gallery pick or camera shot); returns the attachment ref, or null if it isn't a readable image. */
    suspend fun import(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds); true } ?: return@runCatching null
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
            val opts = BitmapFactory.Options().apply { inSampleSize = ImageScale.sampleSize(bounds.outWidth, bounds.outHeight) }
            var bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return@runCatching null
            val rotation = resolver.openInputStream(uri)?.use { s ->
                when (ExifInterface(s).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
            val (w, h) = ImageScale.target(bmp.width, bmp.height)
            if (w != bmp.width || h != bmp.height || rotation != 0f) {
                val m = Matrix().apply {
                    postScale(w.toFloat() / bmp.width, h.toFloat() / bmp.height)
                    if (rotation != 0f) postRotate(rotation)
                }
                val scaled = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                if (scaled !== bmp) bmp.recycle()
                bmp = scaled
            }
            val name = "img-${UUID.randomUUID()}.jpg"
            File(dir, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            bmp.recycle()
            name
        }.getOrNull()
    }

    fun delete(refs: List<String>) {
        refs.forEach { runCatching { file(it).delete() } }
    }

    /** Deletes a camera shot once it has been imported (the attachment copy is what the chat keeps). */
    fun deleteCameraShot(uri: Uri) {
        val name = uri.lastPathSegment?.let { File(it).name } ?: return
        runCatching { File(File(context.cacheDir, "camera"), name).delete() }
    }

    override fun load(ref: String): ImageData? {
        val f = file(ref)
        if (!f.isFile) return null
        return ImageData("image/jpeg", Base64.getEncoder().encodeToString(f.readBytes()))
    }
}

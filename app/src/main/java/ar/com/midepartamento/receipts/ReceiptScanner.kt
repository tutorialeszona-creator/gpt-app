package ar.com.midepartamento.receipts

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import ar.com.midepartamento.domain.ReceiptDraft
import ar.com.midepartamento.domain.ReceiptParser
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.text.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Bundled Latin model: no network, account or server upload. */
class ReceiptScanner {
    suspend fun scan(file: File): ReceiptDraft = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "No se pudo leer la imagen. Elegí una foto JPG, PNG o WebP." }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2400) sample *= 2
        val original = requireNotNull(BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }))
        val orientation = ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { postRotate(90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_TRANSVERSE -> { postRotate(270f); postScale(-1f, 1f) }
            }
        }
        val bitmap = if (matrix.isIdentity) original else Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
        if (bitmap !== original) original.recycle()
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val result = withContext(NonCancellable) { suspendCancellableCoroutine<Text> { continuation ->
                recognizer.process(InputImage.fromBitmap(bitmap, 0))
                    .addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
                    .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
                    .addOnCanceledListener { continuation.cancel() }
            }
            }
            // Join adjacent columns on the same visual row, e.g. description on the left and price on the right.
            val rows = mutableListOf<MutableList<Text.Line>>()
            result.textBlocks.flatMap { it.lines }.sortedBy { it.boundingBox?.centerY() ?: 0 }.forEach { line ->
                val box = line.boundingBox
                val last = rows.lastOrNull()
                val previous = last?.firstOrNull()?.boundingBox
                if (box != null && previous != null && kotlin.math.abs(box.centerY() - previous.centerY()) <= minOf(box.height(), previous.height()) / 2)
                    last?.add(line)
                else rows.add(mutableListOf(line))
            }
            val text = rows.joinToString("\n") { row -> row.sortedBy { it.boundingBox?.left ?: 0 }.joinToString(" ") { it.text } }
            ReceiptParser.parse(text)
        } finally {
            recognizer.close()
            bitmap.recycle()
        }
    }
}

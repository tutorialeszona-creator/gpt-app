package ar.com.midepartamento.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

class ReceiptFiles(private val context: Context) {
    suspend fun copy(uri: Uri): File = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "receipts").apply { mkdirs() }
        val temp = File(dir, "pending-${newId()}.image")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            context.contentResolver.openInputStream(uri).use { source ->
                requireNotNull(source) { "No se pudo abrir la foto" }
                temp.outputStream().use { out ->
                    val bytes = ByteArray(8192); var total = 0L
                    while (true) {
                        val n = source.read(bytes); if (n == -1) break
                        total += n; require(total <= 20 * 1024 * 1024) { "La foto supera los 20 MB" }
                        digest.update(bytes, 0, n); out.write(bytes, 0, n)
                    }
                    require(total > 0) { "La foto está vacía" }
                }
            }
            val header = temp.inputStream().use { input -> ByteArray(12).also { input.read(it) } }
            val ext = when {
                header[0] == 0xff.toByte() && header[1] == 0xd8.toByte() && header[2] == 0xff.toByte() -> "jpg"
                header.take(8).toByteArray().contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)) -> "png"
                String(header, 0, 4, Charsets.US_ASCII) == "RIFF" && String(header, 8, 4, Charsets.US_ASCII) == "WEBP" -> "webp"
                else -> error("Elegí una foto JPG, PNG o WebP; el escaneo no admite PDF")
            }
            val name = digest.digest().joinToString("") { "%02x".format(it) }
            val target = File(dir, "ticket-$name.$ext")
            if (!target.exists()) check(temp.renameTo(target)) { "No se pudo conservar el ticket" }
            target
        } finally { temp.delete() }
    }
}

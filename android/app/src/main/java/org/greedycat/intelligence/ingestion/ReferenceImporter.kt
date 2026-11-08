package org.greedycat.intelligence.ingestion

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.greedycat.intelligence.data.ReferenceFile
import org.greedycat.intelligence.domain.Provenance
import org.greedycat.intelligence.domain.utcNow
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import kotlin.coroutines.coroutineContext

/** Copies only a user-selected stream. Never enumerates storage or keeps source URIs/names. */
class ReferenceImporter(context: Context, private val provenance: Provenance) {
    private val resolver = context.applicationContext.contentResolver
    private val directory = File(context.filesDir, "references-${provenance.name.lowercase()}").apply { mkdirs() }
    fun file(reference: ReferenceFile): File {
        require(Regex("[a-f0-9]{64}").matches(reference.sha256))
        return File(directory, reference.sha256)
    }

    suspend fun importUri(uri: Uri): ReferenceFile = withContext(Dispatchers.IO) {
        require(uri.scheme == "content") { "Choose a file through the system document picker." }
        resolver.openInputStream(uri)?.use { importStream(it) } ?: error("Selected file could not be opened.")
    }

    // Also exercised with explicit synthetic assets, without access to a gallery.
    suspend fun importStream(input: InputStream): ReferenceFile = withContext(Dispatchers.IO) {
        val temporary = File.createTempFile("import-", ".part", directory)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val count = copyBounded(input, temporary, digest)
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            val (mime, duration) = inspect(temporary)
            val result = ReferenceFile(UUID.randomUUID().toString(), hash, mime, count, utcNow(),
                provenance.name, duration)
            val destination = file(result)
            if (destination.exists()) {
                require(destination.length() == count && sha256(destination) == hash) { "Stored reference integrity check failed." }
            } else check(temporary.renameTo(destination)) { "Reference could not be saved privately." }
            result
        } finally { temporary.delete() }
    }

    suspend fun verify(reference: ReferenceFile) = withContext(Dispatchers.IO) {
        val stored = file(reference)
        require(stored.isFile && stored.length() == reference.byteCount && sha256(stored) == reference.sha256) {
            "Reference is missing or has changed. Confirmation was blocked."
        }
    }

    private suspend fun copyBounded(input: InputStream, output: File, digest: MessageDigest): Long {
        var count = 0L
        output.outputStream().use { stream ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                coroutineContext.ensureActive()
                val size = input.read(buffer)
                if (size < 0) break
                if (size == 0) continue
                count += size
                require(count <= MAX_BYTES) { "Reference exceeds the 128 MiB import limit." }
                digest.update(buffer, 0, size); stream.write(buffer, 0, size)
            }
        }
        require(count > 0) { "The selected file is empty." }
        return count
    }

    private fun inspect(file: File): Pair<String, Long?> {
        val bytes = ByteArray(12)
        file.inputStream().use { require(it.read(bytes) >= 8) { "Invalid media file." } }
        val ascii = bytes.toString(Charsets.ISO_8859_1)
        val mime = when {
            bytes.take(8) == listOf(137,80,78,71,13,10,26,10).map { it.toByte() } -> "image/png"
            bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() -> "image/jpeg"
            ascii.startsWith("RIFF") && ascii.substring(8,12) == "WEBP" -> "image/webp"
            ascii.substring(4,8) == "ftyp" -> "video/mp4"
            bytes.take(4) == listOf(0x1a,0x45,0xdf,0xa3).map { it.toByte() } -> "video/webm"
            else -> error("Supported references: PNG, JPEG, WebP, MP4 or WebM.")
        }
        if (mime.startsWith("image")) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong()*bounds.outHeight <= 40_000_000) { "Image is unreadable or exceeds 40 megapixels." }
            return mime to null
        }
        val reader = MediaMetadataRetriever()
        try {
            reader.setDataSource(file.path)
            val duration = reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            require(duration != null && duration in 1..7_200_000) { "Video is unreadable or exceeds two hours." }
            require(reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes") { "Select a video with a visual track." }
            return mime to duration
        } finally { reader.release() }
    }

    private suspend fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64*1024)
            while (true) { coroutineContext.ensureActive(); val n=input.read(buffer); if(n<0) break; digest.update(buffer,0,n) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object { const val MAX_BYTES = 128L * 1024 * 1024 }
}

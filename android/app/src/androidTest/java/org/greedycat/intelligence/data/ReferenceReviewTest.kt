package org.greedycat.intelligence.data

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.greedycat.intelligence.domain.*
import org.greedycat.intelligence.ingestion.ReferenceImporter
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ReferenceReviewTest {
    private lateinit var database: ResearchDatabase
    private lateinit var importer: ReferenceImporter
    private lateinit var repository: ReviewRepository
    private lateinit var root: File
    private lateinit var png: ByteArray
    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        root = File(context.cacheDir, "test-reference-${UUID.randomUUID()}").apply { mkdirs() }
        val isolated = object : ContextWrapper(context) { override fun getFilesDir(): File = root }
        importer = ReferenceImporter(isolated, Provenance.SYNTHETIC)
        database = Room.inMemoryDatabaseBuilder(context, ResearchDatabase::class.java).build()
        repository = ReviewRepository(database, Provenance.SYNTHETIC, importer)
        png = syntheticPng()
    }
    @After fun cleanup() { database.close(); root.deleteRecursively() }

    private suspend fun reference(): ReferenceFile = repository.register(importer.importStream(ByteArrayInputStream(png)))
    private fun input(file: ReferenceFile, roundId: String = UUID.randomUUID().toString()) = ManualReview(roundId, file.id,
        GameMode.BASIC, "100", OutcomeValue.CORN, "2026-01-01T12:00:00.000Z", null, "Explicit synthetic test image", null)

    @Test fun imageImportHashesExactBytesAndDeduplicates() = runBlocking {
        val first = reference(); val second = reference()
        assertEquals(first.id, second.id)
        assertEquals("image/png", first.mime)
        assertEquals(png.size.toLong(), first.byteCount)
        assertEquals(MessageDigest.getInstance("SHA-256").digest(png).joinToString("") { "%02x".format(it) }, first.sha256)
        assertArrayEquals(png, importer.file(first).readBytes())
        assertEquals(1, repository.references.first().size)
        assertTrue(root.walk().none { it.extension == "part" })
    }

    @Test fun imageToHistoryCorrectionRetainsOriginalAndRejectsStaleEdit() = runBlocking {
        val file = reference(); val identity = repository.identity(); val request = input(file)
        val original = repository.confirm(request, identity.contextId)
        val correction = repository.confirm(request.copy(outcome = OutcomeValue.TOMATO, expectedPreviousId = original.id,
            reason = "Correction after rechecking the synthetic image"), identity.contextId)
        val row = repository.history.first().single()
        assertEquals(2, row.reviews.size)
        assertEquals(OutcomeValue.CORN.name, row.reviews.single { it.id == original.id }.value)
        assertEquals(original.id, correction.previousId)
        assertEquals(2, correction.revision)
        assertEquals(RoundPhase.RESULT_PENDING, row.round.phase)
        var rejected = false
        try { repository.confirm(request.copy(expectedPreviousId = original.id), identity.contextId) }
        catch (_: IllegalStateException) { rejected = true }
        assertTrue(rejected); assertEquals(2, repository.history.first().single().reviews.size)
    }

    @Test fun tamperedReferenceCannotConfirmAndImportCannotReplaceIt() = runBlocking {
        val file = reference(); importer.file(file).appendBytes(byteArrayOf(0))
        var rejected = 0
        try { repository.confirm(input(file), repository.identity().contextId) } catch (_: IllegalArgumentException) { rejected++ }
        try { reference() } catch (_: IllegalArgumentException) { rejected++ }
        assertEquals(2, rejected)
        assertTrue(repository.history.first().isEmpty())
    }

    @Test fun duplicateRoundRollsBackAndContextResetAllowsNewHistory() = runBlocking {
        val file = reference(); val identity = repository.identity()
        repository.confirm(input(file), identity.contextId)
        var rejected = false
        try { repository.confirm(input(file), identity.contextId) } catch (_: android.database.sqlite.SQLiteConstraintException) { rejected = true }
        assertTrue(rejected); assertEquals(1, repository.history.first().size)
        val newIdentity = repository.newContext()
        assertEquals(identity.reviewerId, newIdentity.reviewerId)
        assertNotEquals(identity.contextId, newIdentity.contextId)
        repository.confirm(input(file), newIdentity.contextId)
        assertEquals(2, repository.history.first().size)
    }

    @Test fun invalidOversizedAndInterruptedStreamsLeaveNoPartialFiles() = runBlocking {
        val streams = listOf(ByteArrayInputStream(byteArrayOf()), ByteArrayInputStream("not media".toByteArray()),
            object : InputStream() {
                var emitted = 0L
                override fun read(): Int = 0
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    if (emitted > ReferenceImporter.MAX_BYTES) return -1
                    b.fill(0, off, off+len); emitted += len; return len
                }
            }, object : InputStream() { override fun read(): Int = throw java.io.IOException("Synthetic stream failure") })
        var rejected = 0
        streams.forEach { stream -> try { importer.importStream(stream) } catch (_: Exception) { rejected++ } }
        assertEquals(4, rejected)
        assertTrue(root.walk().none { it.isFile })
        assertTrue(repository.references.first().isEmpty())
    }

    @Test fun realRepositoryRejectsSyntheticReference() = runBlocking {
        val file = reference()
        var rejected = false
        try { ReviewRepository(database, Provenance.REAL, importer).register(file) } catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
    }

    @Test fun generatedVideoImportAndTimestampedConfirmation() = runBlocking {
        val video = File(root, "generated-gray.mp4")
        syntheticVideo(video)
        val file = video.inputStream().use { repository.register(importer.importStream(it)) }
        assertEquals("video/mp4", file.mime)
        assertTrue(requireNotNull(file.durationMs) > 0)
        val review = repository.confirm(input(file).copy(videoOffsetMs = 0), repository.identity().contextId)
        assertEquals(0L, review.videoOffsetMs)
        assertEquals(file.id, repository.history.first().single().reviews.single().evidenceId)
    }

    companion object {
        fun syntheticPng(): ByteArray {
            val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.MAGENTA) }
            return ByteArrayOutputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); bitmap.recycle(); out.toByteArray() }
        }
    }
}

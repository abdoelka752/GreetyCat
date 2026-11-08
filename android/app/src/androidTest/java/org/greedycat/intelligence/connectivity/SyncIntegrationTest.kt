package org.greedycat.intelligence.connectivity

import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.greedycat.intelligence.data.*
import org.greedycat.intelligence.domain.*
import org.greedycat.intelligence.ingestion.ReferenceImporter
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class SyncIntegrationTest {
    @Test fun actualPcLostAckRestartCorrectionAndRevocation(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fixture = File(context.noBackupFilesDir, "t008-test.json")
        assumeTrue("No explicitly prepared cross-device sync fixture.", fixture.exists())
        val json = Json.parseToJsonElement(fixture.readText()).jsonObject
        fun value(key: String) = json.getValue(key).jsonPrimitive.content
        val client = PairingClient()
        val record = client.pair(value("address"), value("fingerprint"), value("invitation"))
        val name = "test-sync-wire-${UUID.randomUUID()}.db"
        val root = File(context.cacheDir, "test-sync-wire-${UUID.randomUUID()}").apply { mkdirs() }
        val isolated = object : ContextWrapper(context) { override fun getFilesDir(): File = root }
        var db = ResearchDatabase.open(context, name)
        try {
            val importer = ReferenceImporter(isolated, Provenance.SYNTHETIC)
            val reviews = ReviewRepository(db, Provenance.SYNTHETIC, importer)
            val reference = reviews.register(importer.importStream(ByteArrayInputStream(ReferenceReviewTest.syntheticPng())))
            val identity = reviews.identity()
            val input = ManualReview(UUID.randomUUID().toString(), reference.id, GameMode.BASIC, "100", OutcomeValue.CORN,
                "2026-01-01T12:00:00.000Z", null, "Synthetic physical-phone sync fixture; not game data", null)
            val original = reviews.confirm(input, identity.contextId)
            reviews.confirm(input.copy(expectedPreviousId = original.id, outcome = OutcomeValue.TOMATO,
                reason = "Synthetic correction retained across sync restart"), identity.contextId)
            val first = ReviewSync(db) { c, source, event ->
                client.sendReview(c, source, event)
                throw java.io.IOException("Synthetic lost acknowledgment after actual PC commit")
            }.drain(record)
            assertEquals(0, first.acknowledged); assertEquals(2, first.pending)
            db.close(); db = ResearchDatabase.open(context, name)
            val peer = SyncOutbox.sha(record.fingerprint + ":" + record.pairingId)
            db.syncDao().receipt(db.syncDao().receipt(peer, original.id)!!.copy(nextAttemptAt = 0))
            val finished = ReviewSync(db).drain(record)
            assertEquals(2, finished.acknowledged); assertEquals(0, finished.pending)
            assertEquals(2, db.reviewDao().revisions(original.roundId).size)
            client.revoke(record)
            var rejected = false
            try { client.sendReview(record, db.syncDao().identity()!!.sourceId, db.syncDao().event(original.id)!!) }
            catch (_: PairingRejected) { rejected = true }
            assertTrue(rejected)
        } finally { db.close(); context.deleteDatabase(name); root.deleteRecursively() }
    }
}

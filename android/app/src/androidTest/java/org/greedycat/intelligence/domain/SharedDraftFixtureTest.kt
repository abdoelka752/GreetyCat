package org.greedycat.intelligence.domain

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedDraftFixtureTest {
    @Test fun initialSyntheticDraftMatchesSharedV1FixtureIdentifiersAndUnknownOutcome() {
        val text = InstrumentationRegistry.getInstrumentation().context.assets.open("round-open.json").bufferedReader().use { it.readText() }
        val record = JSONObject(text)
        assertEquals("1.0", record.getString("schema_version"))
        assertEquals(DemoSample.round.id, record.getString("round_id"))
        assertEquals(DemoSample.session.id, record.getString("capture_session_id"))
        assertEquals(DemoSample.round.contextId, record.getString("context_id"))
        assertEquals(DemoSample.round.provenance.name, record.getString("provenance"))
        assertEquals(DemoSample.round.phase.name, record.getString("phase"))
        assertTrue(record.isNull("outcome"))
        DraftValidator.validate(DemoSample.session, DemoSample.round, Provenance.SYNTHETIC)
    }
}

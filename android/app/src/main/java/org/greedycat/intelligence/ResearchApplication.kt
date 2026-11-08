package org.greedycat.intelligence

import android.app.Application
import org.greedycat.intelligence.data.ResearchDatabase
import org.greedycat.intelligence.data.ResearchRepository
import org.greedycat.intelligence.domain.Provenance

/** Small constructor-injected container; separate files for real and demo data. */
class ResearchApplication : Application() {
    val realDatabase by lazy { ResearchDatabase.open(this, "greedycat-research.db") }
    val demoDatabase by lazy { ResearchDatabase.open(this, "greedycat-demo.db") }
    val realRepository by lazy { ResearchRepository(realDatabase, Provenance.REAL) }
    val demoRepository by lazy { ResearchRepository(demoDatabase, Provenance.SYNTHETIC) }
}

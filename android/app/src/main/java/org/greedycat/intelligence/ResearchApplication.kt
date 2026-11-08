package org.greedycat.intelligence

import android.app.Application
import org.greedycat.intelligence.data.ResearchDatabase
import org.greedycat.intelligence.data.ResearchRepository
import org.greedycat.intelligence.domain.Provenance
import org.greedycat.intelligence.data.ReviewRepository
import org.greedycat.intelligence.ingestion.ReferenceImporter

/** Small constructor-injected container; separate files for real and demo data. */
class ResearchApplication : Application() {
    val realDatabase by lazy { ResearchDatabase.open(this, "greedycat-research.db") }
    val demoDatabase by lazy { ResearchDatabase.open(this, "greedycat-demo.db") }
    val realRepository by lazy { ResearchRepository(realDatabase, Provenance.REAL) }
    val demoRepository by lazy { ResearchRepository(demoDatabase, Provenance.SYNTHETIC) }
    val realReviews by lazy { ReviewRepository(realDatabase, Provenance.REAL, ReferenceImporter(this, Provenance.REAL)) }
    val demoReviews by lazy { ReviewRepository(demoDatabase, Provenance.SYNTHETIC, ReferenceImporter(this, Provenance.SYNTHETIC)) }
}

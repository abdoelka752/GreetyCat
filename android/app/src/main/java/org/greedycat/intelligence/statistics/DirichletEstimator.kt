package org.greedycat.intelligence.statistics

import org.greedycat.intelligence.domain.OutcomeValue
import kotlin.math.ln
import kotlin.math.sqrt

data class CategoryEstimate(val outcome: OutcomeValue, val count: Int, val empirical: Double?, val posteriorMean: Double, val parameterSd: Double)
data class ResearchEstimate(val sampleCount: Int, val categories: List<CategoryEstimate>, val normalizedPredictiveEntropy: Double)

/** Conditional on a stationary iid categorical model. Not evidence of skill. */
object DirichletEstimator {
    const val MODEL_VERSION = "symmetric-dirichlet-half-v1"
    const val ALPHA = 0.5
    fun estimate(counts: Map<OutcomeValue, Int>): ResearchEstimate {
        require(counts.values.all { it >= 0 }) { "Counts cannot be negative." }
        val n = counts.values.sumOf { it.toLong() }
        require(n <= 1_000_000) { "Count limit exceeded." }
        val total = n + ALPHA * OutcomeValue.entries.size
        val rows = OutcomeValue.entries.map { outcome ->
            val count = counts[outcome] ?: 0
            val alpha = count + ALPHA
            val mean = alpha / total
            val variance = alpha * (total - alpha) / (total * total * (total + 1))
            CategoryEstimate(outcome, count, if (n == 0L) null else count.toDouble()/n, mean, sqrt(variance))
        }
        return ResearchEstimate(n.toInt(), rows, -rows.sumOf { it.posteriorMean * ln(it.posteriorMean) } / ln(rows.size.toDouble()))
    }
}

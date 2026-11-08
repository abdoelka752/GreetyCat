package org.greedycat.intelligence.statistics

import org.greedycat.intelligence.domain.OutcomeValue
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

class DirichletEstimatorTest {
    @Test fun emptyHistoryIsPriorOnlyWithNoEmpiricalFrequency() {
        val estimate = DirichletEstimator.estimate(emptyMap())
        assertEquals(0, estimate.sampleCount)
        estimate.categories.forEach { assertNull(it.empirical); assertEquals(0.1, it.posteriorMean, 1e-12); assertEquals(sqrt(0.015), it.parameterSd, 1e-12) }
        assertEquals(1.0, estimate.normalizedPredictiveEntropy, 1e-12)
    }
    @Test fun posteriorMatchesConjugateUpdateAndAllCategoriesRemainPresent() {
        val estimate = DirichletEstimator.estimate(mapOf(OutcomeValue.CORN to 3))
        assertEquals(10, estimate.categories.size)
        val corn = estimate.categories.first()
        assertEquals(1.0, requireNotNull(corn.empirical), 1e-12)
        assertEquals(3.5/8, corn.posteriorMean, 1e-12)
        assertEquals(sqrt(3.5*4.5/(64*9)), corn.parameterSd, 1e-12)
        assertTrue(estimate.categories.drop(1).all { it.posteriorMean > 0 })
    }
    @Test fun empiricalAndPosteriorAreNormalizedForUnbalancedData() {
        val estimate = DirichletEstimator.estimate(OutcomeValue.entries.zip(listOf(12,8,6,4,3,2,2,1,1,1)).toMap())
        assertEquals(40, estimate.sampleCount)
        assertEquals(1.0, estimate.categories.sumOf { requireNotNull(it.empirical) }, 1e-12)
        assertEquals(1.0, estimate.categories.sumOf { it.posteriorMean }, 1e-12)
        assertTrue(estimate.normalizedPredictiveEntropy in 0.0..1.0)
    }
    @Test fun moreDataReducesParameterUncertaintyWithoutEliminatingOutcomeEntropy() {
        val small = DirichletEstimator.estimate(OutcomeValue.entries.associateWith { 2 })
        val large = DirichletEstimator.estimate(OutcomeValue.entries.associateWith { 100 })
        assertTrue(large.categories.first().parameterSd < small.categories.first().parameterSd)
        assertEquals(1.0, large.normalizedPredictiveEntropy, 1e-12)
    }
    @Test fun negativeAndExcessiveCountsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { DirichletEstimator.estimate(mapOf(OutcomeValue.CORN to -1)) }
        assertThrows(IllegalArgumentException::class.java) { DirichletEstimator.estimate(mapOf(OutcomeValue.CORN to Int.MAX_VALUE, OutcomeValue.TOMATO to Int.MAX_VALUE)) }
    }
}

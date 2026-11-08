package org.greedycat.intelligence.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.greedycat.intelligence.domain.*
import org.greedycat.intelligence.statistics.*
import java.util.Locale
import java.util.UUID

@Composable
fun AnalysisScreen(model: AnalysisViewModel, onBack: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    var number by rememberSaveable { mutableStateOf("") }
    var seconds by rememberSaveable { mutableStateOf("30") }
    var unseen by rememberSaveable { mutableStateOf(false) }
    var roundId by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    var showExample by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        TextButton(onClick = onBack) { Text("Back to workspace") }
        Text("Research estimates", style = MaterialTheme.typography.headlineMedium)
        Text("${model.repository.provenance} · ANDROID · NO PROVEN EDGE", color = WorkspaceAmber)
        Text("Summaries describe reviewed history in the current context. A stationary, independent-outcome model is an assumption, not an established game mechanic.")
        Row {
            listOf(GameMode.BASIC, GameMode.PROFESSIONAL).forEach { mode ->
                FilterChip(selected = state.mode == mode, enabled = !state.busy, onClick = { model.mode(mode); unseen = false }, label = { Text(mode.name) })
                Spacer(Modifier.width(8.dp))
            }
        }
        Text("Context ${state.contextId?.take(8) ?: "opening"}", color = WorkspaceMuted)
        Button(enabled = !state.loading && !state.busy, onClick = model::refresh) { Text("Compute historical summary") }
        if(state.loading || state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state.notice?.let { Text(it, color = WorkspaceAmber) }
        state.summary?.let { summary ->
            summary.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            summary.estimate?.let { estimate ->
                Text("${estimate.sampleCount} reviewed rounds before ${summary.cutoff}")
                if(estimate.sampleCount == 0) Text("No eligible observations. The prior alone is not evidence for a prediction.")
                else EstimateTable(estimate)
            }
            Text("Snapshot SHA-256 ${summary.sha256}", style = MaterialTheme.typography.bodySmall)
        }
        if(model.repository.provenance == Provenance.SYNTHETIC) {
            OutlinedButton(onClick = { showExample = !showExample }) { Text("Show fixed-count example") }
            if(showExample) {
                Text("SYNTHETIC mathematical example · 40 assumed outcomes. No observations or predictions are stored.", color = WorkspaceAmber)
                EstimateTable(DirichletEstimator.estimate(OutcomeValue.entries.zip(listOf(12,8,6,4,3,2,2,1,1,1)).toMap()))
            }
        }
        HorizontalDivider()
        Text("Prospective checkpoint", style = MaterialTheme.typography.titleLarge)
        Text("Manual targets are unverified, so checkpoints abstain. This is a local research deadline, not a verified game countdown. Imported or already-known rounds are rejected.")
        OutlinedTextField(value = number, onValueChange = { number = it.take(32); unseen = false }, enabled = !state.busy,
            label = { Text("Current round number") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = seconds, onValueChange = { seconds = it.take(3); unseen = false }, enabled = !state.busy,
            label = { Text("Local window in seconds (5–120)") }, modifier = Modifier.fillMaxWidth())
        Row {
            Checkbox(checked = unseen, enabled = !state.busy, onCheckedChange = { unseen = it }, modifier = Modifier.testTag("unseenTarget"))
            Text("I am observing this current round and do not know its result.")
        }
        Button(enabled = unseen && number.isNotBlank() && !state.loading && !state.busy && state.contextId != null,
            onClick = { model.record(roundId, number, seconds.toIntOrNull() ?: 0, unseen) { number = ""; unseen = false; roundId = UUID.randomUUID().toString() } }) {
            Text("Record abstention checkpoint")
        }
        Text("Use History to import evidence and attach its result to the pending round. Existing checkpoints keep their original dataset, times and status.", color = WorkspaceMuted)
        HorizontalDivider()
        Text("Immutable ledger · recent 100", style = MaterialTheme.typography.titleLarge)
        if(!state.loading && state.history.isEmpty()) Text("No checkpoints recorded.")
        state.history.forEach { record ->
            Card(Modifier.fillMaxWidth().testTag("predictionRow")) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Round ${record.roundNumber} · ${record.mode}", style = MaterialTheme.typography.titleMedium)
                    Text("${record.status} · ${record.abstentionReason.replace('_',' ')}", color = WorkspaceAmber)
                    Text("${record.provenance} · ${record.source} · context ${record.contextId.take(8)} · ${record.sampleCount} snapshot observations")
                    Text("Issued ${record.issuedAt}\nExpires ${record.expiresAt}", style = MaterialTheme.typography.bodySmall)
                    Text("Model ${record.modelVersion}\nDataset ${record.datasetId}\nSHA-256 ${record.datasetSha256}", style = MaterialTheme.typography.bodySmall)
                    Text("No probabilities issued. Manual acquisition and predictive calibration are unverified.", color = WorkspaceMuted)
                }
            }
        }
    }
}

@Composable
private fun EstimateTable(estimate: ResearchEstimate) {
    Text("Symmetric Dirichlet prior: α = 0.5 per outcome. Mean and parameter SD are conditional on the model; SD is not measured predictive accuracy.", color = WorkspaceMuted)
    Text("Outcome / Count / Frequency / Posterior mean / Parameter SD", style = MaterialTheme.typography.bodySmall)
    estimate.categories.forEach { row ->
        Text("${row.outcome} · ${row.count} · ${percent(row.empirical ?: 0.0)} · ${percent(row.posteriorMean)} · ${percent(row.parameterSd)}", style = MaterialTheme.typography.bodySmall)
    }
    Text("Predictive entropy (0–1): ${String.format(Locale.ROOT, "%.3f", estimate.normalizedPredictiveEntropy)}. Outcome randomness and parameter uncertainty are different quantities.", style = MaterialTheme.typography.bodySmall)
    Text("No payout or wagering recommendation is calculated.", color = WorkspaceMuted)
}

private fun percent(value: Double) = String.format(Locale.ROOT, "%.2f%%", value*100)

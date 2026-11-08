package org.greedycat.intelligence.ui

import android.graphics.BitmapFactory
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.greedycat.intelligence.data.*
import org.greedycat.intelligence.domain.*
import java.util.UUID

@Composable
fun ReferenceLibrary(model: ReferenceViewModel, onReview: (String, String) -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    var confirmContext by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(model::import) }
    ReferenceColumn {
        Text("Reference library", style = MaterialTheme.typography.headlineMedium)
        Text("Select only recordings you are authorized to use. Files stay in this workspace on this device. No screen is captured.")
        Button(enabled = !state.loading && !state.busy && state.identity != null,
            onClick = { picker.launch(arrayOf("image/png", "image/jpeg", "image/webp", "video/mp4", "video/webm")) }) { Text("Import image or video") }
        Text("PNG / JPEG / WebP / MP4 / WebM · up to 128 MiB", color = WorkspaceMuted)
        if (state.busy || state.loading) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Working with local references…") }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state.notice?.let { Text(it, color = WorkspaceAmber) }
        state.identity?.let { identity ->
            Text("Context ${identity.contextId.take(8)} · keep this context for the same game history.", color = WorkspaceMuted)
            OutlinedButton(enabled = !state.busy, onClick = { confirmContext = true }) { Text("Start a new context") }
        }
        Text("Up to 100 recent references", style = MaterialTheme.typography.titleMedium)
        if (!state.loading && state.references.isEmpty()) Text("No references imported yet.")
        state.references.forEach { file ->
            ReferenceCard {
                Text(if (file.durationMs == null) "Image reference" else "Video reference · ${file.durationMs / 1000}s")
                Text("SHA-256 ${file.sha256}", style = MaterialTheme.typography.bodySmall)
                Text("${file.provenance} · ${file.byteCount} bytes", color = WorkspaceMuted)
                Button(enabled = !state.busy, onClick = { onReview(file.id, UUID.randomUUID().toString()) }) { Text("Review reference") }
            }
        }
        Text("Confirmed history", style = MaterialTheme.typography.titleLarge)
        val reviewed = state.history.filter { it.reviews.isNotEmpty() }
        if (!state.loading && state.history.isEmpty()) Text("No real rounds yet")
        if (!state.loading && reviewed.isEmpty()) Text("No human-confirmed results yet.")
        state.history.filter { it.reviews.isEmpty() }.forEach { row ->
            Text("Unverified draft · round ${row.round.roundNumber ?: "unknown"} · ${row.round.mode}", color = WorkspaceMuted)
        }
        reviewed.forEach { row ->
            val latest = row.reviews.maxBy { it.revision }
            ReferenceCard {
                Text("Round ${row.round.roundNumber} · ${latest.value}", style = MaterialTheme.typography.titleMedium)
                Text("HUMAN CONFIRMED · ${row.round.provenance} · ${row.round.mode}", color = WorkspaceAmber)
                Text("Context ${row.round.contextId.take(8)} · revision ${latest.revision}")
                Text("Observed ${latest.observedAt}", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { onReview(latest.evidenceId, row.round.id) }) { Text("Review or correct") }
            }
        }
    }
    if (confirmContext) AlertDialog(onDismissRequest = { confirmContext = false }, title = { Text("Start a new context?") },
        text = { Text("Use this only when the game's round numbering resets or you begin a distinct history. Existing rounds remain in their original context.") },
        confirmButton = { TextButton(onClick = { model.newContext(); confirmContext = false }) { Text("Start new context") } },
        dismissButton = { TextButton(onClick = { confirmContext = false }) { Text("Cancel") } })
}

@Composable
fun ManualReviewScreen(repository: ReviewRepository, evidenceId: String, roundId: String, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var reference by remember { mutableStateOf<ReferenceFile?>(null) }
    var old by remember { mutableStateOf<ReviewHistory?>(null) }
    var contextId by rememberSaveable { mutableStateOf<String?>(null) }
    var expectedRevisionId by rememberSaveable { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var saved by rememberSaveable { mutableStateOf(false) }
    var mode by rememberSaveable { mutableStateOf(GameMode.UNKNOWN.name) }
    var number by rememberSaveable { mutableStateOf("") }
    var outcome by rememberSaveable { mutableStateOf("") }
    var observedAt by rememberSaveable { mutableStateOf("") }
    var offset by rememberSaveable { mutableStateOf("0") }
    var reason by rememberSaveable { mutableStateOf("") }
    var attested by rememberSaveable { mutableStateOf(false) }
    var previewReady by remember { mutableStateOf(false) }
    var fieldsInitialized by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(evidenceId, roundId) {
        try {
            val file = requireNotNull(repository.reference(evidenceId))
            repository.files.verify(file)
            val row = repository.roundHistory(roundId)
            old = row
            if (contextId == null) contextId = row?.round?.contextId ?: repository.identity().contextId
            if (!fieldsInitialized) expectedRevisionId = row?.reviews?.maxByOrNull { it.revision }?.id
            if (row != null && !fieldsInitialized) {
                mode = row.round.mode.name; number = row.round.roundNumber ?: ""
                val latest = row.reviews.maxByOrNull { it.revision }
                outcome = latest?.value ?: ""; observedAt = latest?.observedAt ?: ""
                offset = latest?.videoOffsetMs?.toString() ?: "0"
            }
            fieldsInitialized = true
            reference = file
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = "Reference could not be opened or its integrity check failed." }
    }
    ReferenceColumn {
        TextButton(onClick = onBack) { Text("Back to references") }
        Text(if (old == null) "Confirm observed result" else "Correct with an audit trail", style = MaterialTheme.typography.headlineMedium)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        reference?.let { file ->
            Text("${file.provenance} · selected reference", color = WorkspaceAmber)
            ReferencePreview(repository, file, onReady = { previewReady = it }, onPosition = { offset = it.toString() })
            Text("SHA-256 ${file.sha256}", style = MaterialTheme.typography.bodySmall)
            Text("Context ${contextId ?: "loading"}", style = MaterialTheme.typography.bodySmall)
            Text("Enter what the reference shows. Observation time is not inferred from its import time.", color = WorkspaceMuted)
            if (!saved) {
                Row {
                    listOf(GameMode.BASIC, GameMode.PROFESSIONAL).forEach { choice ->
                        FilterChip(selected = mode == choice.name, onClick = { if(old == null) mode = choice.name },
                            enabled = old == null && !busy, label = { Text(choice.name.lowercase().replaceFirstChar(Char::uppercase)) })
                        Spacer(Modifier.width(8.dp))
                    }
                }
                OutlinedTextField(value = number, onValueChange = { number = it.take(32) }, enabled = old == null && !busy,
                    label = { Text("Observed round number") }, modifier = Modifier.fillMaxWidth())
                var expanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(enabled = !busy, onClick = { expanded = true }) { Text(outcome.ifEmpty { "Choose observed outcome" }) }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        OutcomeValue.entries.forEach { value -> DropdownMenuItem(text = { Text(value.name) }, onClick = { outcome = value.name; expanded = false }) }
                    }
                }
                OutlinedTextField(value = observedAt, onValueChange = { observedAt = it.take(24) }, enabled = !busy,
                    label = { Text("Observed UTC time") }, placeholder = { Text("2026-11-08T12:00:00.000Z") }, modifier = Modifier.fillMaxWidth())
                if (file.durationMs != null) OutlinedTextField(value = offset, onValueChange = { offset = it.take(10) }, enabled = !busy,
                    label = { Text("Video position in milliseconds") }, supportingText = { Text("Pause the video and use its position, or enter a position before ${file.durationMs} ms.") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = reason, onValueChange = { reason = it.take(256) }, enabled = !busy,
                    label = { Text(if(old == null) "What evidence confirms this result?" else "Reason for correction") }, modifier = Modifier.fillMaxWidth())
                Row {
                    Checkbox(checked = attested, enabled = !busy, onCheckedChange = { attested = it })
                    Text("I reviewed this reference and confirm the mode, round, result, time and video position where applicable.")
                }
                Button(enabled = previewReady && attested && !busy && contextId != null && outcome.isNotEmpty(), onClick = {
                    busy = true; error = null
                    scope.launch {
                        try {
                            repository.confirm(ManualReview(roundId, file.id, GameMode.valueOf(mode), number,
                                OutcomeValue.valueOf(outcome), observedAt, if(file.durationMs != null) offset.toLongOrNull() else null,
                                reason, expectedRevisionId), requireNotNull(contextId))
                            saved = true
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (e: Exception) { error = when(e) {
                            is IllegalArgumentException, is IllegalStateException -> e.message ?: "Review validation failed."
                            else -> "Review could not be saved. A duplicate round may already exist in this context."
                        } } finally { busy = false }
                    }
                }) { Text(if(busy) "Saving…" else "Confirm and save") }
            } else Text("Human-confirmed review saved. Previous revisions were retained.", color = WorkspaceAmber)
            old?.reviews?.sortedByDescending { it.revision }?.forEach { revision ->
                ReferenceCard {
                    Text("Revision ${revision.revision}: ${revision.value}")
                    Text(revision.reason)
                    Text("Confirmed ${revision.confirmedAt}", style = MaterialTheme.typography.bodySmall)
                    Text("Reviewer ${revision.reviewerId.take(8)} · evidence ${revision.evidenceId.take(8)}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun ReferencePreview(repository: ReviewRepository, reference: ReferenceFile, onReady: (Boolean) -> Unit, onPosition: (Long) -> Unit) {
    val file = remember(reference.id) { repository.files.file(reference) }
    var failed by remember { mutableStateOf(false) }
    if (reference.durationMs == null) {
        val bitmap by produceState<android.graphics.Bitmap?>(null, file) {
            value = withContext(Dispatchers.IO) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                var sample = 1
                while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) sample *= 2
                BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            }
            failed = value == null
            onReady(value != null)
        }
        bitmap?.let { Image(it.asImageBitmap(), contentDescription = "Selected reference image", modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)) }
    } else {
        var player by remember { mutableStateOf<VideoView?>(null) }
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        AndroidView(modifier = Modifier.fillMaxWidth().height(240.dp), factory = { context ->
            VideoView(context).apply {
                player = this
                val controls = MediaController(context)
                controls.setAnchorView(this); setMediaController(controls)
                setOnPreparedListener { seekTo(1); onReady(true) }
                setOnErrorListener { _, _, _ -> failed = true; onReady(false); true }
                setVideoPath(file.path)
            }
        })
        OutlinedButton(onClick = { player?.let { it.pause(); onPosition(it.currentPosition.toLong()) } }) { Text("Use paused video position") }
        DisposableEffect(lifecycle) {
            val observer = LifecycleEventObserver { _, event -> if(event == Lifecycle.Event.ON_PAUSE) player?.pause() }
            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer); player?.stopPlayback() }
        }
    }
    if(failed) Text("This reference cannot be previewed. Confirmation is blocked.", color = MaterialTheme.colorScheme.error)
}

@Composable
private fun ReferenceColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
}

@Composable
private fun ReferenceCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content) }
}

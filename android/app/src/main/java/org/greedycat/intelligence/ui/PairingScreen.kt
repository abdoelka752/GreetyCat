package org.greedycat.intelligence.ui

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun PairingScreen(model: PairingViewModel, back: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    var address by remember { mutableStateOf("https://127.0.0.1:8767") }
    var fingerprint by remember { mutableStateOf("") }
    var invitation by remember { mutableStateOf("") }
    var confirmed by remember { mutableStateOf(false) }
    var forgetDialog by remember { mutableStateOf(false) }
    var syncDialog by remember { mutableStateOf(false) }
    val activity = LocalActivity.current
    DisposableEffect(activity) {
        val alreadySecure = activity?.window?.attributes?.flags?.and(WindowManager.LayoutParams.FLAG_SECURE) != 0
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (!alreadySecure) activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    val enabled = !state.loading && !state.busy
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        TextButton(onClick = back) { Text("Back to settings") }
        Text("Connect your PC", style = MaterialTheme.typography.headlineMedium)
        Text("Connect this app and explicitly sync reviewed results. Game capture and observation are not available yet.", color = WorkspaceMuted)
        Text(state.status, modifier = Modifier.testTag("pairingStatus"))
        if (state.busy) CircularProgressIndicator(Modifier.size(24.dp))
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.saved) {
            Text("Saved PC: ${state.address}")
            Text("Trusted SHA-256: ${state.fingerprint}")
            Text("Credential expiry (PC time): ${state.expires}", color = WorkspaceMuted)
            Button(onClick = model::probe, enabled = enabled) { Text("Check connection") }
            if (model.syncAvailable) {
                state.synchronization?.let { Text("Acknowledged: ${it.acknowledged} / ${it.archived} · Pending: ${it.pending} · Conflicts: ${it.conflicts} · Not queued: ${it.missing}") }
                OutlinedButton(onClick = { syncDialog = true }, enabled = enabled) { Text("Sync reviewed history") }
            }
            OutlinedButton(onClick = model::revoke, enabled = enabled) { Text("Revoke on PC and forget") }
        } else if (state.storageBlocked) {
            Text("Saved pairing could not be opened safely. Use Forget local pairing before trying again, and revoke the old pairing on the PC.", color = MaterialTheme.colorScheme.error)
        } else {
            Text("Enter the HTTPS address and full certificate SHA-256 from your PC. Compare it directly on the PC before confirming. Never accept a fingerprint from an unexpected message.", color = WorkspaceMuted)
            OutlinedTextField(address, { address = it; confirmed = false }, label = { Text("PC HTTPS address") },
                singleLine = true, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag("pairingAddress"))
            Text("Use a private IPv4 address, or loopback for an explicitly configured USB connection. The PC service must be running.", color = WorkspaceMuted)
            OutlinedTextField(fingerprint, { fingerprint = it; confirmed = false }, label = { Text("Certificate SHA-256 (64 hex characters)") },
                enabled = enabled, modifier = Modifier.fillMaxWidth().testTag("pairingFingerprint"))
            OutlinedTextField(invitation, { invitation = it }, label = { Text("One-use invitation") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), enabled = enabled, modifier = Modifier.fillMaxWidth().testTag("pairingInvitation"))
            Row {
                Checkbox(confirmed, { confirmed = it }, enabled = enabled, modifier = Modifier.testTag("pairingConfirmed"))
                Text("I compared this fingerprint with my PC and want to pair this device.", Modifier.padding(top = 10.dp))
            }
            Button(onClick = { model.pair(address, fingerprint, invitation, confirmed) { invitation = "" } },
                enabled = enabled && confirmed && fingerprint.isNotBlank() && invitation.isNotBlank(),
                modifier = Modifier.testTag("pairingSubmit")) { Text("Pair with PC") }
        }
        OutlinedButton(onClick = { forgetDialog = true }, enabled = enabled) { Text("Forget local pairing") }
        Text("If the PC is offline, forgetting only removes this device's stored token. Revoke the old pairing on the PC before creating another invitation.", color = WorkspaceMuted)
        Text("Research history remains available offline. Sync sends only reviewed results, corrections, notes and evidence hashes. Original media stays on your phone.", color = WorkspaceMuted)
    }
    if (syncDialog) AlertDialog(onDismissRequest = { syncDialog = false }, title = { Text("Send reviewed history to this PC?") },
        text = { Text("Send up to 25 real reviewed results or corrections, including review notes and evidence hashes, to the saved PC. No images or videos are sent. Re-pairing starts a separate PC copy; it does not merge prior pairings.") },
        confirmButton = { TextButton(onClick = { syncDialog = false; model.synchronize() }) { Text("Sync reviews") } },
        dismissButton = { TextButton(onClick = { syncDialog = false }) { Text("Cancel") } })
    if (forgetDialog) AlertDialog(onDismissRequest = { forgetDialog = false }, title = { Text("Forget this PC locally?") },
        text = { Text("The PC credential remains valid until revoked or expired. Your research history is kept.") },
        confirmButton = { TextButton(onClick = { forgetDialog = false; model.forget() }) { Text("Forget locally") } },
        dismissButton = { TextButton(onClick = { forgetDialog = false }) { Text("Cancel") } })
}

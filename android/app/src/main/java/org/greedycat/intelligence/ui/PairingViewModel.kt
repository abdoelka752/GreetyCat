package org.greedycat.intelligence.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.greedycat.intelligence.connectivity.*
import java.time.Instant

data class PairingState(val loading: Boolean = true, val busy: Boolean = false, val saved: Boolean = false,
    val storageBlocked: Boolean = false,
    val address: String = "", val fingerprint: String = "", val expires: String = "",
    val status: String = "No PC paired.", val error: String? = null, val synchronization: SyncSummary? = null)

class PairingViewModel(private val vault: PairingVault, private val client: PairingClient = PairingClient(), private val sync: ReviewSync? = null) : ViewModel() {
    val syncAvailable get() = sync != null
    private val mutable = MutableStateFlow(PairingState())
    val state = mutable.asStateFlow()
    private var credential: PairingCredential? = null
    init { operation {
        try { credential = withContext(Dispatchers.IO) { vault.load() } }
        catch (error: Exception) { mutable.update { it.copy(storageBlocked = true) }; throw error }
        publish("Saved pairing loaded; connection has not been checked.")
    } }
    private suspend fun publish(status: String) {
        val record = credential
        val progress = record?.let { sync?.summary(it) }
        mutable.update { it.copy(saved = record != null, address = record?.address ?: "",
            fingerprint = record?.fingerprint ?: "", expires = record?.let { Instant.ofEpochSecond(it.expiresAt.toLong()).toString() } ?: "",
            status = if (record == null) "No PC paired." else status, synchronization = progress) }
    }
    fun pair(address: String, fingerprint: String, invitation: String, confirmed: Boolean, finished: () -> Unit) = operation {
        try {
            require(confirmed && credential == null && !mutable.value.storageBlocked)
            // Do not abandon a newly issued token between receipt and durable storage.
            withContext(NonCancellable) {
                val record = client.pair(address, fingerprint, invitation.trim())
                try { withContext(Dispatchers.IO) { vault.save(record) } }
                catch (error: Exception) { mutable.update { it.copy(storageBlocked = true) }; throw error }
                credential = record
            }
            publish("Paired with PC. Use Check connection to verify it now.")
        } finally { finished() }
    }
    fun probe() = operation {
        client.probe(requireNotNull(credential))
        publish("PC accepted this credential at ${Instant.now()}. This is a one-time check.")
    }
    fun synchronize() = operation {
        val service = requireNotNull(sync)
        val destination = requireNotNull(credential)
        val progress = try { service.drain(destination) }
        finally { val counters = service.summary(destination); mutable.update { it.copy(synchronization = counters) } }
        mutable.update { it.copy(synchronization = progress, status = when {
            progress.conflicts > 0 -> "Some reviews conflict with the PC copy. Neither copy was replaced."
            progress.missing > 0 -> "Some local reviews still need queuing. Sync again; a full archive needs future retention controls."
            progress.pending > 0 -> "Reviews remain pending. Sync again after the retry delay; check the PC connection if needed."
            else -> "All queued real reviews are acknowledged by this PC pairing."
        }) }
    }
    fun revoke() = operation {
        val record = requireNotNull(credential)
        try { client.revoke(record) }
        catch (_: PairingRejected) { /* Already expired/revoked: no active authority remains. */ }
        withContext(Dispatchers.IO) { vault.forget() }
        credential = null
        publish("")
    }
    fun forget() = operation {
        withContext(Dispatchers.IO) { vault.forget() }
        credential = null
        mutable.update { it.copy(storageBlocked = false) }
        publish("")
    }
    private fun operation(action: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: PairingRejected) { mutable.update { it.copy(error = "PC rejected the invitation or credential. It may be expired, used or revoked.", status = "PC did not accept this request.") } }
            catch (_: Exception) { mutable.update { it.copy(error = "Pairing operation failed. Check the HTTPS address, PC fingerprint, invitation, clocks and connection. A newly issued PC session may need operator revocation if saving failed.", status = "Connection is unverified.") } }
            finally { mutable.update { it.copy(loading = false, busy = false) } }
        }
    }
}

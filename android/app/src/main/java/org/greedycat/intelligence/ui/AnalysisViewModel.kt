package org.greedycat.intelligence.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.greedycat.intelligence.data.*
import org.greedycat.intelligence.domain.GameMode

data class AnalysisState(val contextId: String? = null, val mode: GameMode = GameMode.BASIC,
    val summary: ResearchSnapshot? = null, val history: List<LockedPrediction> = emptyList(),
    val loading: Boolean = true, val busy: Boolean = false, val error: String? = null, val notice: String? = null)

class AnalysisViewModel(val repository: PredictionRepository) : ViewModel() {
    private val _state = MutableStateFlow(AnalysisState())
    val state = _state.asStateFlow()
    init {
        viewModelScope.launch {
            try {
                val context = repository.contextId()
                repository.history.collect { records -> _state.update { it.copy(contextId = it.contextId ?: context, history = records, loading = false) } }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _state.update { it.copy(loading = false, error = "The local prediction ledger could not be opened.") } }
        }
    }
    fun mode(mode: GameMode) { if(!_state.value.busy) _state.update { it.copy(mode = mode, summary = null, notice = null) } }
    fun refresh() = operation {
        val summary = repository.research(_state.value.mode)
        _state.update { it.copy(contextId = summary.contextId, summary = summary) }
    }
    fun record(roundId: String, number: String, seconds: Int, attested: Boolean, onSaved: () -> Unit) = operation {
        val current = _state.value
        val prediction = repository.recordManual(roundId, requireNotNull(current.contextId), current.mode, number, seconds, attested)
        _state.update { it.copy(notice = "Checkpoint saved: ${prediction.status}. Its record cannot be changed.") }
        onSaved()
    }
    private fun operation(action: suspend () -> Unit) {
        if(_state.value.busy || _state.value.loading) return
        _state.update { it.copy(busy = true, error = null, notice = null) }
        viewModelScope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (e: Exception) { _state.update { it.copy(error = if(e is IllegalArgumentException || e is IllegalStateException) e.message else "Local research operation failed. Records were not replaced.") } }
            finally { _state.update { it.copy(busy = false) } }
        }
    }
}

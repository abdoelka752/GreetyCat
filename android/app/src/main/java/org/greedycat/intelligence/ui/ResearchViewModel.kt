package org.greedycat.intelligence.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import org.greedycat.intelligence.data.InsertResult
import org.greedycat.intelligence.data.ResearchRepository
import org.greedycat.intelligence.data.StoredRound
import org.greedycat.intelligence.domain.DemoSample

sealed interface RoundLoad {
    data object Loading : RoundLoad
    data class Ready(val rows: List<StoredRound>) : RoundLoad
    data object Failed : RoundLoad
}

class ResearchViewModel(val real: ResearchRepository, val demo: ResearchRepository) : ViewModel() {
    private fun records(repository: ResearchRepository): StateFlow<RoundLoad> = repository.observeRounds()
        .map<List<StoredRound>, RoundLoad> { RoundLoad.Ready(it) }
        .catch { emit(RoundLoad.Failed) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoundLoad.Loading)

    val realRounds = records(real)
    val demoRounds = records(demo)
    private val _preferenceError = MutableStateFlow<String?>(null)
    val preferenceError = _preferenceError.asStateFlow()
    val compactSpacing = real.observeCompactSpacing()
        .catch { _preferenceError.value = "Display preferences could not be opened."; emit(false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _notice = MutableStateFlow<String?>(null)
    val notice = _notice.asStateFlow()

    fun addSample() = demoAction {
        val result = demo.addDraft(DemoSample.session, DemoSample.round)
        _notice.value = if (result == InsertResult.REPLAY) "Sample draft already exists. No duplicate was added." else "Synthetic sample saved on this device."
    }

    fun clearSamples() = demoAction {
        demo.clearDemo()
        _notice.value = "Demo records cleared. Real records were not changed."
    }

    fun setCompactSpacing(compact: Boolean) {
        viewModelScope.launch {
            try { real.setCompactSpacing(compact); _preferenceError.value = null }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _preferenceError.value = "Display preference could not be saved." }
        }
    }

    private fun demoAction(action: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _notice.value = "Storage operation failed. Records were not replaced." }
            finally { _busy.value = false }
        }
    }
}

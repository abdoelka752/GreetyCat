package org.greedycat.intelligence.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.greedycat.intelligence.data.*
import org.greedycat.intelligence.domain.*

data class LibraryState(val references: List<ReferenceFile> = emptyList(), val history: List<ReviewHistory> = emptyList(),
    val identity: ReviewIdentity? = null, val loading: Boolean = true, val busy: Boolean = false, val error: String? = null, val notice: String? = null)

class ReferenceViewModel(val repository: ReviewRepository) : ViewModel() {
    private val _state = MutableStateFlow(LibraryState())
    val state = _state.asStateFlow()
    init {
        viewModelScope.launch {
            try {
                val identity = repository.identity()
                combine(repository.references, repository.history) { files, rounds -> files to rounds }.collect { (files, rounds) ->
                    _state.update { it.copy(references = files, history = rounds, identity = it.identity ?: identity, loading = false) }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _state.update { it.copy(loading = false, error = "Local references could not be opened. Restart to retry.") } }
        }
    }

    fun import(uri: Uri) = operation {
        repository.importUri(uri)
        _state.update { it.copy(notice = "Reference saved privately. Identical files are kept once.") }
    }

    fun newContext() = operation {
        val identity = repository.newContext()
        _state.update { it.copy(identity = identity, notice = "New context started. Previous records are unchanged.") }
    }

    private fun operation(action: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null, notice = null) }
        viewModelScope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (e: Exception) { _state.update { it.copy(error = when(e) {
                is IllegalArgumentException, is IllegalStateException -> e.message ?: "Reference operation failed."
                else -> "Reference could not be imported. Check the file and available storage."
            }) } }
            finally { _state.update { it.copy(busy = false) } }
        }
    }
}

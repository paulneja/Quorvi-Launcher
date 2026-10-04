package com.paulneja.quorvi.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paulneja.quorvi.connections.Hosts
import com.paulneja.quorvi.connections.services.ServicesRepo
import com.paulneja.quorvi.runtime.RuntimeAvailability
import com.paulneja.quorvi.runtime.RuntimeClient
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsCounts(val hosts: Int = 0, val services: Int = 0)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val runtimeClient: RuntimeClient,
    hosts: Hosts,
    services: ServicesRepo,
) : ViewModel() {
    private val _runtime = MutableStateFlow<RuntimeAvailability?>(null)
    val runtime: StateFlow<RuntimeAvailability?> = _runtime.asStateFlow()

    val counts: StateFlow<SettingsCounts> = combine(hosts.all, services.all) { h, s -> SettingsCounts(h.size, s.size) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsCounts())

    fun refreshRuntime() = viewModelScope.launch { _runtime.value = runCatching { runtimeClient.availability() }.getOrNull() }

    fun prepareRuntime() = viewModelScope.launch {
        runCatching { runtimeClient.install() }
        refreshRuntime()
    }
}

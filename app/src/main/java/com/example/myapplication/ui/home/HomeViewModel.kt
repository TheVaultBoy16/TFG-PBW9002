package com.example.myapplication.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.HomeItem
import com.example.myapplication.data.SessionManager
import com.example.myapplication.data.SshService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(
    private val sshService: SshService,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _vmList = MutableStateFlow<List<HomeItem>>(emptyList())
    val vmList: StateFlow<List<HomeItem>> = _vmList.asStateFlow()

    private val _selectedItem = MutableStateFlow<HomeItem?>(null)
    val selectedItem: StateFlow<HomeItem?> = _selectedItem.asStateFlow()

    private var pollingJob: Job? = null
    private val virshCommand = "export LC_ALL=C; virsh --connect qemu:///system"

    fun selectItem(item: HomeItem) {
        _selectedItem.value = item
    }

    fun clearData() {
        _vmList.value = emptyList()
        _selectedItem.value = null
        stopPolling()
    }

    fun startPolling() {
        if (pollingJob?.isActive == true) return
        pollingJob = viewModelScope.launch {
            while (true) {
                refreshOnce()
                delay(10000)
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
    }

    suspend fun login(user: String, host: String, key: String, port: Int): Boolean {
        val result = sshService.executeCommand(user, host, key, "$virshCommand list --all", port)
        return if (!result.startsWith("ERROR_SSH:")) {
            sessionManager.saveSession(user, host, key, port)
            _vmList.value = parseVirshOutput(result)
            true
        } else {
            false
        }
    }

    fun refreshOnce(onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val session = sessionManager.getSession()
            if (session != null) {
                val result = sshService.executeCommand(session.user, session.host, session.rsaKey, "$virshCommand list --all", session.port)
                if (!result.startsWith("ERROR_SSH:")) {
                    _vmList.value = parseVirshOutput(result)
                    onResult(true)
                } else {
                    onResult(false)
                }
            } else {
                onResult(false)
            }
        }
    }

    fun toggleVm(item: HomeItem) {
        viewModelScope.launch {
            val session = sessionManager.getSession() ?: return@launch
            val isRunning = item.state.lowercase().contains("running")
            val action = if (isRunning) "shutdown" else "start"
            val targetState = if (isRunning) "shut off" else "running"

            _vmList.value = _vmList.value.map { if (it.name == item.name) it.copy(state = "procesando...") else it }
            
            sshService.executeCommand(session.user, session.host, session.rsaKey, "$virshCommand $action ${item.name}", session.port)
            
            repeat(10) {
                delay(4000)
                val r = sshService.executeCommand(session.user, session.host, session.rsaKey, "$virshCommand list --all", session.port)
                if (!r.startsWith("ERROR_SSH:")) {
                    val newList = parseVirshOutput(r)
                    _vmList.value = newList
                    if (newList.find { it.name == item.name }?.state?.lowercase()?.contains(targetState) == true) return@launch
                }
            }
        }
    }

    suspend fun takeSnapshot(item: HomeItem, name: String): String {
        val session = sessionManager.getSession() ?: return "Error de sesión"
        return sshService.executeCommand(session.user, session.host, session.rsaKey, "$virshCommand snapshot-create-as ${item.name} $name", session.port)
    }

    suspend fun restoreSnapshot(item: HomeItem, name: String): String {
        val session = sessionManager.getSession() ?: return "Error de sesión"
        return sshService.executeCommand(session.user, session.host, session.rsaKey, "$virshCommand snapshot-revert ${item.name} $name", session.port)
    }

    private fun parseVirshOutput(output: String): List<HomeItem> {
        val lines = output.lines()
        val vms = mutableListOf<HomeItem>()
        for (line in lines) {
            val t = line.trim()
            if (t.isEmpty() || t.lowercase().startsWith("id") || t.startsWith("---")) continue
            val parts = t.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (parts.size >= 3) {
                vms.add(HomeItem(id = parts[0], name = parts[1], state = parts.subList(2, parts.size).joinToString(" "), imageRes = 0))
            }
        }
        return vms
    }
}

class HomeViewModelFactory(
    private val sshService: SshService,
    private val sessionManager: SessionManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return HomeViewModel(sshService, sessionManager) as T
    }
}

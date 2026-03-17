package com.example.myapplication.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myapplication.R
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

    private val _snapshotList = MutableStateFlow<List<String>>(emptyList())
    val snapshotList: StateFlow<List<String>> = _snapshotList.asStateFlow()

    private var pollingJob: Job? = null
    private val virshCommand = "export LC_ALL=C; virsh --connect qemu:///system"

    fun selectItem(item: HomeItem) {
        _selectedItem.value = item
        refreshSnapshots(item)
    }

    private fun syncSelectedItem() {
        val current = _selectedItem.value ?: return
        val updated = _vmList.value.find { it.name == current.name }
        if (updated != null && (updated.state != current.state || updated.id != current.id)) {
            _selectedItem.value = updated
        }
    }

    fun clearData() {
        _vmList.value = emptyList()
        _selectedItem.value = null
        _snapshotList.value = emptyList()
        stopPolling()
    }

    fun startPolling() {
        if (pollingJob?.isActive == true) return
        pollingJob = viewModelScope.launch {
            while (true) {
                refreshOnceSync()
                delay(10000)
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
    }

    suspend fun login(user: String, host: String, key: String, port: Int): Boolean {
        val cleanKey = key.trim()
        val result = sshService.executeCommand(user, host, cleanKey, "$virshCommand list --all", port)
        
        return if (!result.startsWith("ERROR_SSH:")) {
            sessionManager.saveSession(user, host, cleanKey, port)
            _vmList.value = parseVirshOutput(result)
            syncSelectedItem()
            true
        } else {
            false
        }
    }

    // Versión para llamadas desde UI con callback (recargar una vez)
    fun refreshOnce(onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            onResult(refreshOnceSync())
        }
    }

    // Versión interna de suspensión para el polling (mantener recargas periódicas)
    private suspend fun refreshOnceSync(): Boolean {
        val session = sessionManager.getSession() ?: return false
        val result = sshService.executeCommand(session.user, session.host, session.rsaKey, "$virshCommand list --all", session.port)
        return if (!result.startsWith("ERROR_SSH:")) {
            _vmList.value = parseVirshOutput(result)
            syncSelectedItem()
            true
        } else {
            false
        }
    }

    fun refreshSnapshots(item: HomeItem) {
        viewModelScope.launch {
            val session = sessionManager.getSession() ?: return@launch
            val result = sshService.executeCommand(
                session.user, session.host, session.rsaKey,
                "$virshCommand snapshot-list --name \"${item.name}\"",
                session.port
            )
            if (!result.startsWith("ERROR_SSH:")) {
                _snapshotList.value = result.lines().filter { it.isNotBlank() }
            } else {
                _snapshotList.value = emptyList()
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
            syncSelectedItem()

            sshService.executeCommand(session.user, session.host, session.rsaKey, "$virshCommand $action \"${item.name}\"", session.port)
            
            repeat(15) {
                delay(3000)
                refreshOnceSync()
                if (_vmList.value.find { it.name == item.name }?.state?.lowercase()?.contains(targetState) == true) return@launch
            }
        }
    }

    suspend fun takeSnapshot(item: HomeItem, name: String): String {
        val session = sessionManager.getSession() ?: return "Error de sesión"
        val result = sshService.executeCommand(session.user, session.host, session.rsaKey, "$virshCommand snapshot-create-as \"${item.name}\" \"$name\"", session.port)
        if (!result.startsWith("ERROR_SSH:")) {
            refreshSnapshots(item)
        }
        return result
    }

    suspend fun restoreSnapshot(item: HomeItem, name: String): String {
        val session = sessionManager.getSession() ?: return "Error de sesión"
        val res = sshService.executeCommand(session.user, session.host, session.rsaKey, "$virshCommand snapshot-revert \"${item.name}\" \"$name\"", session.port)
        refreshOnceSync()
        return res
    }

    suspend fun deleteSnapshot(item: HomeItem, name: String): String {
        val session = sessionManager.getSession() ?: return "Error de sesión"
        val result = sshService.executeCommand(session.user, session.host, session.rsaKey, "$virshCommand snapshot-delete \"${item.name}\" \"$name\"", session.port)
        if (!result.startsWith("ERROR_SSH:")) {
            refreshSnapshots(item)
        }
        return result
    }

    suspend fun saveVm(item: HomeItem): String {
        val session = sessionManager.getSession() ?: return "Error de sesión"
        val path = "/var/lib/libvirt/images/${item.name}.save"
        val result = sshService.executeCommand(session.user, session.host, session.rsaKey, "sudo $virshCommand save \"${item.name}\" \"$path\"", session.port)
        refreshOnceSync()
        return result
    }

    suspend fun restoreVm(item: HomeItem): String {
        val session = sessionManager.getSession() ?: return "Error de sesión"
        val path = "/var/lib/libvirt/images/${item.name}.save"
        val result = sshService.executeCommand(session.user, session.host, session.rsaKey, "sudo $virshCommand restore \"$path\"", session.port)
        refreshOnceSync()
        return result
    }

    private fun parseVirshOutput(output: String): List<HomeItem> {
        val lines = output.lines()
        val vms = mutableListOf<HomeItem>()
        for (line in lines) {
            val t = line.trim()
            if (t.isEmpty() || t.lowercase().startsWith("id") || t.startsWith("---")) continue
            val parts = t.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (parts.size >= 3) {
                val state = parts.subList(2, parts.size).joinToString(" ")
                val isRunning = state.lowercase().contains("running")
                val img = if (isRunning) R.drawable.ejecutandose else R.drawable.apagada
                vms.add(HomeItem(id = parts[0], name = parts[1], state = state, imageRes = img))
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

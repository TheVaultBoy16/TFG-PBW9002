package com.example.myapplication.ui.home

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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

    private val _currentScreenshot = MutableStateFlow<Bitmap?>(null)
    val currentScreenshot: StateFlow<Bitmap?> = _currentScreenshot.asStateFlow()

    private var pollingJob: Job? = null
    private val virshCommand = "virsh --connect qemu:///system"

    private val detailedListCommand = "for vm in \$($virshCommand list --all --name); do if [ -n \"\$vm\" ]; then echo \"---VM:\$vm---\"; $virshCommand dominfo \"\$vm\" | grep -E \"Id:|State:|Managed save:\"; fi; done"

    fun selectItem(item: HomeItem) {
        _selectedItem.value = item
        _currentScreenshot.value = null
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
        _currentScreenshot.value = null
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
        val result = sshService.executeCommand(user, host, cleanKey, detailedListCommand, port)
        
        return if (!result.startsWith("ERROR_SSH:")) {
            sessionManager.saveSession(user, host, cleanKey, port)
            _vmList.value = parseDetailedOutput(result)
            syncSelectedItem()
            true
        } else {
            false
        }
    }

    fun refreshOnce(onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            onResult(refreshOnceSync())
        }
    }

    private suspend fun refreshOnceSync(): Boolean {
        val session = sessionManager.getSession() ?: return false
        val result = sshService.executeCommand(session.user, session.host, session.rsaKey, detailedListCommand, session.port)
        return if (!result.startsWith("ERROR_SSH:")) {
            _vmList.value = parseDetailedOutput(result)
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

    private suspend fun executeVirshAction(item: HomeItem, actionCommand: String): String {
        val session = sessionManager.getSession() ?: return "Error de sesión"
        var res = sshService.executeCommand(session.user, session.host, session.rsaKey, "$virshCommand $actionCommand", session.port)
        if (res.startsWith("ERROR_SSH:") && (res.contains("permission denied", ignoreCase = true) || res.contains("authentication failed", ignoreCase = true))) {
            res = sshService.executeCommand(session.user, session.host, session.rsaKey, "sudo -n $virshCommand $actionCommand", session.port)
        }
        return res
    }

    fun toggleVm(item: HomeItem) {
        viewModelScope.launch {
            val session = sessionManager.getSession() ?: return@launch
            val isRunning = item.state.lowercase().contains("running")
            val action = if (isRunning) "shutdown" else "start"
            val targetState = if (isRunning) "shut off" else "running"

            _vmList.value = _vmList.value.map { if (it.name == item.name) it.copy(state = "procesando...") else it }
            syncSelectedItem()

            executeVirshAction(item, "$action \"${item.name}\"")
            
            repeat(15) {
                delay(3000)
                refreshOnceSync()
                val current = _vmList.value.find { it.name == item.name }
                if (current?.state?.lowercase()?.contains(targetState) == true) return@launch
            }
        }
    }

    suspend fun takeScreenshot(item: HomeItem): String? {
        val session = sessionManager.getSession() ?: return "Error de sesión"
        val timestamp = System.currentTimeMillis()
        val remotePath = "/tmp/${item.name}_$timestamp.ppm"
        val jpgPath = "/tmp/${item.name}_$timestamp.jpg"
        
        var res = executeVirshAction(item, "screenshot \"${item.name}\" \"$remotePath\"")
        if (res.startsWith("ERROR_SSH:")) return "Error al capturar (virsh): $res"

        sshService.executeCommand(session.user, session.host, session.rsaKey, "chmod 666 \"$remotePath\" || sudo -n chmod 666 \"$remotePath\"", session.port)

        val convRes = sshService.executeCommand(session.user, session.host, session.rsaKey, "convert \"$remotePath\" \"$jpgPath\"", session.port)
        val downloadPath = if (!convRes.startsWith("ERROR_SSH:")) jpgPath else remotePath
        val bytes = sshService.downloadBytes(session.user, session.host, session.rsaKey, downloadPath, session.port)
        
        var errorMsg: String? = null
        if (bytes != null && bytes.isNotEmpty()) {
            var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bitmap == null && downloadPath == remotePath) bitmap = decodePPM(bytes)

            if (bitmap != null) {
                _currentScreenshot.value = bitmap
            } else {
                errorMsg = "Error al procesar la imagen."
            }
        } else {
            errorMsg = "No se pudo descargar la captura."
        }

        sshService.executeCommand(session.user, session.host, session.rsaKey, "rm \"$remotePath\" \"$jpgPath\" || sudo -n rm \"$remotePath\" \"$jpgPath\"", session.port)
        return errorMsg
    }

    private fun decodePPM(bytes: ByteArray): Bitmap? {
        try {
            var offset = 0
            fun readNext(): String {
                val sb = StringBuilder()
                while (offset < bytes.size && bytes[offset].toInt().toChar().isWhitespace()) offset++
                while (offset < bytes.size && !bytes[offset].toInt().toChar().isWhitespace()) {
                    if (bytes[offset].toInt().toChar() == '#') {
                        while (offset < bytes.size && bytes[offset].toInt().toChar() != '\n') offset++
                        while (offset < bytes.size && bytes[offset].toInt().toChar().isWhitespace()) offset++
                    } else {
                        sb.append(bytes[offset].toInt().toChar())
                        offset++
                    }
                }
                return sb.toString()
            }
            if (readNext() != "P6") return null
            val width = readNext().toInt()
            val height = readNext().toInt()
            val maxVal = readNext().toInt()
            if (offset < bytes.size && bytes[offset].toInt().toChar().isWhitespace()) offset++
            val pixels = IntArray(width * height)
            for (i in 0 until width * height) {
                if (offset + 2 >= bytes.size) break
                val r = bytes[offset++].toInt() and 0xFF
                val g = bytes[offset++].toInt() and 0xFF
                val b = bytes[offset++].toInt() and 0xFF
                pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
            return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        } catch (e: Exception) { return null }
    }

    suspend fun takeSnapshot(item: HomeItem, name: String): String {
        val res = executeVirshAction(item, "snapshot-create-as \"${item.name}\" \"$name\"")
        if (!res.startsWith("ERROR_SSH:")) refreshSnapshots(item)
        return res
    }

    suspend fun restoreSnapshot(item: HomeItem, name: String): String {
        val res = executeVirshAction(item, "snapshot-revert \"${item.name}\" \"$name\"")
        if (!res.startsWith("ERROR_SSH:")) refreshOnceSync()
        return res
    }

    suspend fun deleteSnapshot(item: HomeItem, name: String): String {
        val res = executeVirshAction(item, "snapshot-delete \"${item.name}\" \"$name\"")
        if (!res.startsWith("ERROR_SSH:")) refreshSnapshots(item)
        return res
    }

    suspend fun saveVm(item: HomeItem): String {
        val res = executeVirshAction(item, "managedsave \"${item.name}\"")
        refreshOnceSync()
        return res
    }

    suspend fun restoreVm(item: HomeItem): String {
        val res = executeVirshAction(item, "start \"${item.name}\"")
        refreshOnceSync()
        return res
    }

    private fun parseDetailedOutput(output: String): List<HomeItem> {
        val vms = mutableListOf<HomeItem>()
        val blocks = output.split("---VM:").filter { it.isNotBlank() }
        
        for (block in blocks) {
            val lines = block.lines()
            if (lines.isEmpty()) continue
            val name = lines[0].substringBefore("---").trim()
            var id = "-"
            var state = "shut off"
            var hasManagedSave = false
            
            for (line in lines) {
                val trimmed = line.trim().lowercase()
                when {
                    trimmed.startsWith("id:") -> id = line.substringAfter(":").trim()
                    trimmed.startsWith("state:") -> state = line.substringAfter(":").trim().lowercase()
                    trimmed.startsWith("managed save:") -> hasManagedSave = line.substringAfter(":").trim().lowercase() == "yes"
                }
            }
            
            if (name.isNotEmpty()) {
                val isRunning = state.contains("running")
                val finalState = if (hasManagedSave && state.contains("shut off")) "saved" else state
                val img = if (isRunning) R.drawable.ejecutandose else R.drawable.apagada
                vms.add(HomeItem(id = id, name = name, state = finalState, imageRes = img))
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

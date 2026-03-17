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

    fun refreshOnce(onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            onResult(refreshOnceSync())
        }
    }

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

            sshService.executeCommand(session.user, session.host, session.rsaKey, "sudo -n $virshCommand $action \"${item.name}\"", session.port)
            
            repeat(15) {
                delay(3000)
                refreshOnceSync()
                if (_vmList.value.find { it.name == item.name }?.state?.lowercase()?.contains(targetState) == true) return@launch
            }
        }
    }

    suspend fun takeScreenshot(item: HomeItem): String? {
        val session = sessionManager.getSession() ?: return "Error de sesión"
        val timestamp = System.currentTimeMillis()
        val remotePath = "/tmp/${item.name}_$timestamp.ppm"
        val jpgPath = "/tmp/${item.name}_$timestamp.jpg"
        
        //Tomar captura.
        var res = sshService.executeCommand(session.user, session.host, session.rsaKey, "$virshCommand screenshot \"${item.name}\" \"$remotePath\"", session.port)
        if (res.startsWith("ERROR_SSH:")) {
            res = sshService.executeCommand(session.user, session.host, session.rsaKey, "sudo -n $virshCommand screenshot \"${item.name}\" \"$remotePath\"", session.port)
        }

        if (res.startsWith("ERROR_SSH:")) {
            return "Error virsh: ${res.removePrefix("ERROR_SSH: ")}. Revisa permisos de sudo."
        }

        //Ajustar permisos
        sshService.executeCommand(session.user, session.host, session.rsaKey, "chmod 666 \"$remotePath\" || sudo -n chmod 666 \"$remotePath\"", session.port)

        //Intentar convertir a JPG (Opcional, si falla bajamos el PPM original)
        var convRes = sshService.executeCommand(session.user, session.host, session.rsaKey, "convert \"$remotePath\" \"$jpgPath\"", session.port)
        if (convRes.startsWith("ERROR_SSH:")) {
            convRes = sshService.executeCommand(session.user, session.host, session.rsaKey, "ffmpeg -i \"$remotePath\" -y \"$jpgPath\"", session.port)
        }
        
        val downloadPath = if (!convRes.startsWith("ERROR_SSH:")) jpgPath else remotePath
        
        //Descargar
        var bytes = sshService.downloadBytes(session.user, session.host, session.rsaKey, downloadPath, session.port)
        if (bytes == null || bytes.isEmpty()) {
            bytes = sshService.downloadBytes(session.user, session.host, session.rsaKey, downloadPath, session.port, useSudo = true)
        }
        
        var errorMsg: String? = null
        if (bytes != null && bytes.isNotEmpty()) {
            //Intentar decodificar normalmente (JPG si hubo conversión)
            var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            
            //Si falla y es el PPM, usamos decodificador manual
            if (bitmap == null && downloadPath == remotePath) {
                bitmap = decodePPM(bytes)
            }

            if (bitmap != null) {
                _currentScreenshot.value = bitmap
            } else {
                errorMsg = "Error al procesar la imagen capturada."
            }
        } else {
            errorMsg = "No se pudo descargar la captura."
        }

        //Limpieza
        sshService.executeCommand(session.user, session.host, session.rsaKey, "rm \"$remotePath\" \"$jpgPath\" || sudo -n rm \"$remotePath\" \"$jpgPath\"", session.port)
        
        return errorMsg
    }

    // Función para decodificar una imagen PPM
    private fun decodePPM(bytes: ByteArray): Bitmap? {
        try {
            var offset = 0
            fun readNext(): String {
                val sb = StringBuilder()
                // Skip whitespace
                while (offset < bytes.size && bytes[offset].toInt().toChar().isWhitespace()) offset++
                // Read field
                while (offset < bytes.size && !bytes[offset].toInt().toChar().isWhitespace()) {
                    if (bytes[offset].toInt().toChar() == '#') { // Skip comments
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
            
            // Skip the single whitespace character after MaxVal
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
        } catch (e: Exception) {
            return null
        }
    }

    suspend fun takeSnapshot(item: HomeItem, name: String): String {
        val session = sessionManager.getSession() ?: return "Error de sesión"
        val result = sshService.executeCommand(session.user, session.host, session.rsaKey, "sudo -n $virshCommand snapshot-create-as \"${item.name}\" \"$name\"", session.port)
        if (!result.startsWith("ERROR_SSH:")) {
            refreshSnapshots(item)
        }
        return result
    }

    suspend fun restoreSnapshot(item: HomeItem, name: String): String {
        val session = sessionManager.getSession() ?: return "Error de sesión"
        val res = sshService.executeCommand(session.user, session.host, session.rsaKey, "sudo -n $virshCommand snapshot-revert \"${item.name}\" \"$name\"", session.port)
        refreshOnceSync()
        return res
    }

    suspend fun deleteSnapshot(item: HomeItem, name: String): String {
        val session = sessionManager.getSession() ?: return "Error de sesión"
        val result = sshService.executeCommand(session.user, session.host, session.rsaKey, "sudo -n $virshCommand snapshot-delete \"${item.name}\" \"$name\"", session.port)
        if (!result.startsWith("ERROR_SSH:")) {
            refreshSnapshots(item)
        }
        return result
    }

    suspend fun saveVm(item: HomeItem): String {
        val session = sessionManager.getSession() ?: return "Error de sesión"
        val path = "/var/lib/libvirt/images/${item.name}.save"
        val result = sshService.executeCommand(session.user, session.host, session.rsaKey, "sudo -n $virshCommand save \"${item.name}\" \"$path\"", session.port)
        refreshOnceSync()
        return result
    }

    suspend fun restoreVm(item: HomeItem): String {
        val session = sessionManager.getSession() ?: return "Error de sesión"
        val path = "/var/lib/libvirt/images/${item.name}.save"
        val result = sshService.executeCommand(session.user, session.host, session.rsaKey, "sudo -n $virshCommand restore \"$path\"", session.port)
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

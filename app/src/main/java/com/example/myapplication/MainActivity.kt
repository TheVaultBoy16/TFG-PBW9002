package com.example.myapplication

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.myapplication.data.HomeItem
import com.example.myapplication.data.SessionManager
import com.example.myapplication.data.SshService
import com.example.myapplication.ui.home.HomeDefaultScreen
import com.example.myapplication.ui.navigation.ApplicationNavGraph
import com.example.myapplication.ui.navigation.Screen
import com.example.myapplication.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val sshService = SshService()
    private lateinit var sessionManager: SessionManager

    private var currentHost = ""
    private var currentUser = ""
    private var currentPass = ""
    private var currentPort = 2222

    private val virshCommand = "export LC_ALL=C; virsh --connect qemu:///system"

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionManager = SessionManager(this)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                val scope = rememberCoroutineScope()

                var selectedItem by remember { mutableStateOf<HomeItem?>(null) }
                var vmList by remember { mutableStateOf<List<HomeItem>>(emptyList()) }

                LaunchedEffect(Unit) {
                    val savedSession = sessionManager.getSession()
                    if (savedSession != null) {
                        currentUser = savedSession.user
                        currentHost = savedSession.host
                        currentPass = savedSession.rsaKey
                        currentPort = savedSession.port
                        
                        val result = sshService.executeCommand(currentUser, currentHost, currentPass, "$virshCommand list --all", currentPort)
                        if (!result.startsWith("ERROR_SSH:")) {
                            val parsed = parseVirshOutput(result)
                            vmList = parsed
                            if (parsed.isNotEmpty()) {
                                navController.navigate(Screen.Home.route)
                            }
                        }
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        MyTopAppBar(
                            title = when (currentRoute) {
                                Screen.VmDetail.route -> selectedItem?.name ?: "Detalle"
                                Screen.Login.route -> "Añadir conexión"
                                Screen.Home.route -> "Servidor: $currentHost"
                                else -> stringResource(id = R.string.app_name)
                            },
                            canNavigateBack = currentRoute != Screen.Default.route,
                            onBackClick = { navController.popBackStack() },
                            showLogout = currentRoute == Screen.Home.route,
                            onLogoutClick = {
                                sessionManager.clearSession()
                                vmList = emptyList()
                                navController.navigate(Screen.Default.route) {
                                    popUpTo(Screen.Default.route) { inclusive = true }
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    ApplicationNavGraph(
                        navController = navController,
                        selectedItem = selectedItem,
                        onSelectItem = { selectedItem = it },
                        vmList = vmList,
                        onLogin = { username, hostname, password, port ->
                            currentUser = username; currentHost = hostname; currentPass = password; currentPort = port
                            scope.launch {
                                val command = "$virshCommand list --all"
                                val result = sshService.executeCommand(username, hostname, password, command, port)
                                
                                if (!result.startsWith("ERROR_SSH:")) {
                                    val parsedVms = parseVirshOutput(result)
                                    vmList = parsedVms
                                    sessionManager.saveSession(username, hostname, password, port)
                                    
                                    if (parsedVms.isEmpty()) {
                                        val preview = if (result.length > 40) result.take(40) + "..." else result
                                        Toast.makeText(this@MainActivity, "Conectado, pero lista vacía. Respuesta: $preview", Toast.LENGTH_LONG).show()
                                    }
                                    navController.navigate(Screen.Home.route)
                                } else {
                                    Toast.makeText(this@MainActivity, "Fallo: $result", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        onToggleVm = { item ->
                            scope.launch {
                                val isRunning = item.state.lowercase().contains("running")
                                val action = if (isRunning) "shutdown" else "start"
                                val targetState = if (isRunning) "shut off" else "running"
                                vmList = vmList.map { if (it.name == item.name) it.copy(state = "procesando...") else it }
                                sshService.executeCommand(currentUser, currentHost, currentPass, "$virshCommand $action ${item.name}", currentPort)
                                repeat(15) { 
                                    delay(4000)
                                    val r = sshService.executeCommand(currentUser, currentHost, currentPass, "$virshCommand list --all", currentPort)
                                    if (!r.startsWith("ERROR_SSH:")) {
                                        vmList = parseVirshOutput(r)
                                        if (vmList.find { it.name == item.name }?.state?.lowercase()?.contains(targetState) == true) return@launch
                                    }
                                }
                            }
                        },
                        onTakeSnapshot = { item, snapshotName ->
                            scope.launch {
                                val result = sshService.executeCommand(currentUser, currentHost, currentPass, "$virshCommand snapshot-create-as ${item.name} $snapshotName", currentPort)
                                Toast.makeText(this@MainActivity, if (result.startsWith("ERROR_SSH:")) "Error al crear snapshot" else "Snapshot OK", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onRestoreSnapshot = { item, snapshotName ->
                            scope.launch {
                                val result = sshService.executeCommand(currentUser, currentHost, currentPass, "$virshCommand snapshot-revert ${item.name} $snapshotName", currentPort)
                                Toast.makeText(this@MainActivity, if (result.startsWith("ERROR_SSH:")) "Error al restaurar" else "Restaurado OK", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    private fun parseVirshOutput(output: String): List<HomeItem> {
        Log.d("VIRSH_PARSER", "Iniciando parseo de la salida del servidor.")
        Log.d("VIRSH_PARSER", "Salida recibida:\n$output")
        val lines = output.lines()
        val vms = mutableListOf<HomeItem>()
        for (line in lines) {
            val t = line.trim()
            Log.d("VIRSH_PARSER", "Procesando línea: '$t'")

            if (t.isEmpty() || t.lowercase().startsWith("id") || t.startsWith("---")) {
                Log.d("VIRSH_PARSER", "Línea ignorada (cabecera o vacía).")
                continue
            }
            
            val parts = t.split(Regex("\\s+")).filter { it.isNotBlank() }
            Log.d("VIRSH_PARSER", "Partes obtenidas: $parts")
            
            if (parts.size >= 3) {
                val id = parts[0]
                val name = parts[1]
                val state = parts.subList(2, parts.size).joinToString(" ")
                
                Log.d("VIRSH_PARSER", "VM Detectada -> ID: $id, Nombre: $name, Estado: $state")
                vms.add(HomeItem(id = id, name = name, state = state, imageRes = R.drawable.apagada))
            } else {
                Log.w("VIRSH_PARSER", "Línea no reconocida (no tiene 3+ partes): '$t'")
            }
        }
        Log.d("VIRSH_PARSER", "Parseo finalizado. Total de VMs encontradas: ${vms.size}")
        return vms
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyTopAppBar(
    title: String,
    canNavigateBack: Boolean,
    onBackClick: () -> Unit = {},
    showLogout: Boolean = false,
    onLogoutClick: () -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
    modifier: Modifier = Modifier
){
    CenterAlignedTopAppBar(
        title = { Text(title) },
        modifier = modifier,
        scrollBehavior = scrollBehavior,
        navigationIcon = {
            if (canNavigateBack) {
                IconButton(onClick = onBackClick) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(id = R.string.back_button))
                }
            }
        },
        actions = {
            if (showLogout) {
                IconButton(onClick = onLogoutClick) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Cerrar sesión")
                }
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun AppPreview() {
    MyApplicationTheme {
        HomeDefaultScreen(onConnectClick = {})
    }
}

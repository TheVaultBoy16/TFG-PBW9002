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
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
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
import com.example.myapplication.data.SshService
import com.example.myapplication.ui.home.HomeDefaultScreen
import com.example.myapplication.ui.navigation.ApplicationNavGraph
import com.example.myapplication.ui.navigation.Screen
import com.example.myapplication.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val sshService = SshService()

    private var currentHost = ""
    private var currentUser = ""
    private var currentPass = ""
    private var currentPort = 2222

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                val scope = rememberCoroutineScope()

                var selectedItem by remember { mutableStateOf<HomeItem?>(null) }
                var vmList by remember { mutableStateOf<List<HomeItem>>(emptyList()) }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        MyTopAppBar(
                            title = when (currentRoute) {
                                Screen.VmDetail.route -> selectedItem?.name ?: "Detalle"
                                Screen.Login.route -> "Añadir conexión"
                                else -> stringResource(id = R.string.app_name)
                            },
                            canNavigateBack = currentRoute != Screen.Default.route,
                            navigateUp = { navController.popBackStack() }
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
                                // Forzamos el idioma a inglés por el parser
                                val command = "export LC_ALL=C; virsh list --all"
                                val result = sshService.executeCommand(username, hostname, password, command, port)
                                
                                if (!result.startsWith("ERROR_SSH:")) {
                                    val parsedVms = parseVirshOutput(result)
                                    if (parsedVms.isNotEmpty()) {
                                        vmList = parsedVms
                                        navController.navigate(Screen.Home.route)
                                    } else {
                                        Log.d("VIRSH_DEBUG", "Salida: $result")
                                        Toast.makeText(this@MainActivity, "No se encontraron MVs o formato desconocido", Toast.LENGTH_LONG).show()
                                        vmList = emptyList()
                                        navController.navigate(Screen.Home.route)
                                    }
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
                                
                                sshService.executeCommand(currentUser, currentHost, currentPass, "export LC_ALL=C; virsh $action ${item.name}", currentPort)
                                
                                repeat(10) { 
                                    delay(4000)
                                    val r = sshService.executeCommand(currentUser, currentHost, currentPass, "export LC_ALL=C; virsh list --all", currentPort)
                                    if (!r.startsWith("ERROR_SSH:")) {
                                        val newList = parseVirshOutput(r)
                                        vmList = newList
                                        if (newList.find { it.name == item.name }?.state?.lowercase()?.contains(targetState) == true) return@launch
                                    }
                                }
                            }
                        },
                        onTakeSnapshot = { item, snapshotName ->
                            scope.launch {
                                val result = sshService.executeCommand(currentUser, currentHost, currentPass, "virsh snapshot-create-as ${item.name} $snapshotName", currentPort)
                                Toast.makeText(this@MainActivity, if (result.startsWith("ERROR_SSH:")) "Error al tomar snapshot" else "Snapshot OK", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onRestoreSnapshot = { item, snapshotName ->
                            scope.launch {
                                val result = sshService.executeCommand(currentUser, currentHost, currentPass, "virsh snapshot-revert ${item.name} $snapshotName", currentPort)
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
        val lines = output.lines()
        val vms = mutableListOf<HomeItem>()
        for (line in lines) {
            val t = line.trim()
            // Ignoramos cabeceras y líneas vacías
            if (t.isEmpty() || t.startsWith("Id") || t.startsWith("---") || t.contains("Name") || t.contains("State")) continue
            
            // Separamos por espacios y filtramos elementos vacíos
            val parts = t.split(Regex("\\s+")).filter { it.isNotBlank() }
            
            if (parts.size >= 3) {
                val name = parts[1]
                // El estado puede tener varias palabras separadas
                val state = parts.subList(2, parts.size).joinToString(" ")
                vms.add(HomeItem(id = parts[0], name = name, state = state, imageRes = R.drawable.apagada))
            }
        }
        return vms
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyTopAppBar(
    title: String,
    canNavigateBack: Boolean,
    navigateUp: () -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
    modifier: Modifier = Modifier
){
    CenterAlignedTopAppBar(
        title = { Text(title) },
        modifier = modifier,
        scrollBehavior = scrollBehavior,
        navigationIcon = {
            if (canNavigateBack) {
                IconButton(onClick = navigateUp) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(id = R.string.back_button))
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

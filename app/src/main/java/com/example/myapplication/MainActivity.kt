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
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val sshService = SshService()

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
                            scope.launch {

                                val command = "export PATH=\$PATH:/usr/bin:/usr/sbin:/usr/local/bin:/snap/bin; " +
                                              "if command -v virsh >/dev/null 2>&1; then " +
                                              "  virsh list --all; " +
                                              "else " +
                                              "  echo 'Error: virsh no encontrado. Rutas: '\$PATH; " +
                                              "fi"
                                
                                val result = sshService.executeCommand(username, hostname, password, command, port)
                                
                                if (!result.startsWith("Error:")) {
                                    val parsedVms = parseVirshOutput(result)
                                    if (parsedVms.isNotEmpty()) {
                                        vmList = parsedVms
                                        Toast.makeText(this@MainActivity, "Conectado: ${parsedVms.size} MVs", Toast.LENGTH_SHORT).show()
                                        navController.navigate(Screen.Home.route)
                                    } else {
                                        Log.d("SSH_RESULT", result)
                                        Toast.makeText(this@MainActivity, "Conectado, pero no se encontraron máquinas virtuales", Toast.LENGTH_LONG).show()
                                        vmList = emptyList()
                                        navController.navigate(Screen.Home.route)
                                    }
                                } else {
                                    Toast.makeText(this@MainActivity, "Fallo: $result", Toast.LENGTH_LONG).show()
                                }
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
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty() || 
                trimmedLine.startsWith("Id") || 
                trimmedLine.startsWith("---") ||
                trimmedLine.contains("Nombre") ||
                trimmedLine.contains("Estado")) continue
            
            val parts = trimmedLine.split(Regex("\\s+"))
            
            if (parts.size >= 3) {
                vms.add(
                    HomeItem(
                        id = parts[0],
                        name = parts[1],
                        state = parts[2],
                        imageRes = R.drawable.virtmanager_94317
                    )
                )
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
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(id = R.string.back_button)
                    )
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

package com.example.myapplication

import android.os.Bundle
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.myapplication.data.SessionManager
import com.example.myapplication.data.SshService
import com.example.myapplication.ui.home.HomeDefaultScreen
import com.example.myapplication.ui.home.HomeViewModel
import com.example.myapplication.ui.home.HomeViewModelFactory
import com.example.myapplication.ui.navigation.ApplicationNavGraph
import com.example.myapplication.ui.navigation.Screen
import com.example.myapplication.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val sshService = SshService()
    private lateinit var sessionManager: SessionManager

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

                val homeViewModel: HomeViewModel = viewModel(
                    factory = HomeViewModelFactory(sshService, sessionManager)
                )

                val vmList by homeViewModel.vmList.collectAsState()
                val selectedItem by homeViewModel.selectedItem.collectAsState()
                val snapshotList by homeViewModel.snapshotList.collectAsState()
                val currentScreenshot by homeViewModel.currentScreenshot.collectAsState()

                LaunchedEffect(currentRoute) {
                    if (currentRoute == Screen.Default.route) {
                        homeViewModel.refreshOnce { success ->
                            if (success) {
                                navController.navigate(Screen.Home.route) {
                                    popUpTo(Screen.Default.route) { inclusive = true }
                                }
                            }
                        }
                    }
                }

                LaunchedEffect(currentRoute) {
                    if (currentRoute == Screen.Home.route || currentRoute == Screen.VmDetail.route) {
                        homeViewModel.startPolling()
                    } else {
                        homeViewModel.stopPolling()
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        MyTopAppBar(
                            title = when (currentRoute) {
                                Screen.VmDetail.route -> selectedItem?.name ?: "Detalle"
                                Screen.Login.route -> "Añadir conexión"
                                Screen.Home.route -> "${sessionManager.getSession()?.host ?: "Sin conexión"}"
                                else -> stringResource(id = R.string.app_name)
                            },
                            canNavigateBack = currentRoute == Screen.VmDetail.route || currentRoute == Screen.Login.route,
                            onBackClick = { navController.popBackStack() },
                            showLogout = currentRoute == Screen.Home.route || currentRoute == Screen.VmDetail.route,
                            onLogoutClick = {
                                sessionManager.clearSession()
                                homeViewModel.clearData()
                                navController.navigate(Screen.Default.route) {
                                    popUpTo(0) { inclusive = true }
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    ApplicationNavGraph(
                        navController = navController,
                        selectedItem = selectedItem,
                        onSelectItem = { homeViewModel.selectItem(it) },
                        vmList = vmList,
                        snapshotList = snapshotList,
                        screenshot = currentScreenshot,
                        onLogin = { username, hostname, password, port ->
                            scope.launch {
                                val success = homeViewModel.login(username, hostname, password, port)
                                if (success) {
                                    navController.navigate(Screen.Home.route) {
                                        popUpTo(Screen.Default.route) { inclusive = true }
                                    }
                                } else {
                                    Toast.makeText(this@MainActivity, "Fallo en la conexión RSA", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        onToggleVm = { homeViewModel.toggleVm(it) },
                        onTakeSnapshot = { item, name -> 
                            scope.launch {
                                val res = homeViewModel.takeSnapshot(item, name)
                                Toast.makeText(this@MainActivity, if(res.startsWith("ERROR")) res else "Snapshot OK", Toast.LENGTH_SHORT).show()
                            } 
                        },
                        onRestoreSnapshot = { item, name -> 
                            scope.launch {
                                val res = homeViewModel.restoreSnapshot(item, name)
                                Toast.makeText(this@MainActivity, if(res.startsWith("ERROR")) res else "Restaurado OK", Toast.LENGTH_SHORT).show()
                            } 
                        },
                        onDeleteSnapshot = { item, name ->
                            scope.launch {
                                val res = homeViewModel.deleteSnapshot(item, name)
                                Toast.makeText(this@MainActivity, if(res.startsWith("ERROR")) res else "Instantánea borrada", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onTakeScreenshot = { item ->
                            scope.launch {
                                Toast.makeText(this@MainActivity, "Iniciando captura de ${item.name}...", Toast.LENGTH_SHORT).show()
                                val error = homeViewModel.takeScreenshot(item)
                                if (error != null) {
                                    Toast.makeText(this@MainActivity, "FALLO: $error", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(this@MainActivity, "Captura recibida con éxito", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onSaveVm = { item ->
                            scope.launch {
                                val res = homeViewModel.saveVm(item)
                                Toast.makeText(this@MainActivity, if(res.startsWith("ERROR")) res else "Estado guardado en disco", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onRestoreVm = { item ->
                            scope.launch {
                                val res = homeViewModel.restoreVm(item)
                                Toast.makeText(this@MainActivity, if(res.startsWith("ERROR")) res else "Estado restaurado desde disco", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
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

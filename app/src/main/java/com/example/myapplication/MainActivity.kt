package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.example.myapplication.data.HomeItem
import com.example.myapplication.ui.home.HomeDefaultScreen
import com.example.myapplication.ui.home.HomeScreen
import com.example.myapplication.ui.login.LoginScreen
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.ui.vm.VmScreen

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                // Estados de navegación
                var currentScreen by remember { mutableStateOf("default") }
                var selectedItem by remember { mutableStateOf<HomeItem?>(null) }

                // Manejo del botón atrás del sistema
                BackHandler(enabled = currentScreen != "default") {
                    currentScreen = when (currentScreen) {
                        "vm_detail" -> "home"
                        "home" -> "login"
                        "login" -> "default"
                        else -> "default"
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        MyTopAppBar(
                            title = when (currentScreen) {
                                "vm_detail" -> selectedItem?.name ?: "Detalle"
                                "login" -> "Login"
                                else -> stringResource(id = R.string.app_name)
                            },
                            canNavigateBack = currentScreen != "default",
                            navigateUp = {
                                currentScreen = when (currentScreen) {
                                    "vm_detail" -> "home"
                                    "home" -> "login"
                                    "login" -> "default"
                                    else -> "default"
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    when (currentScreen) {
                        "default" -> HomeDefaultScreen(
                            onConnectClick = { currentScreen = "login" },
                            modifier = Modifier.padding(innerPadding).fillMaxSize()
                        )
                        "login" -> LoginScreen(
                            onLoginClick = { _, _ -> currentScreen = "home" },
                            modifier = Modifier.padding(innerPadding)
                        )
                        "home" -> HomeScreen(
                            onItemClick = { item ->
                                selectedItem = item
                                currentScreen = "vm_detail"
                            },
                            modifier = Modifier.padding(innerPadding)
                        )
                        "vm_detail" -> selectedItem?.let { item ->
                            VmScreen(
                                item = item,
                                onSave = { currentScreen = "home" },
                                onCancel = { currentScreen = "home" },
                                onRestore = { currentScreen = "home" },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                    }
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

package com.example.myapplication.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.myapplication.data.HomeItem
import com.example.myapplication.ui.home.HomeDefaultScreen
import com.example.myapplication.ui.home.HomeScreen
import com.example.myapplication.ui.login.LoginScreen
import com.example.myapplication.ui.vm.VmScreen

sealed class Screen(val route: String) {
    object Default : Screen("default")
    object Login : Screen("login")
    object Home : Screen("home")
    object VmDetail : Screen("vm_detail")
}

@Composable
fun ApplicationNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    selectedItem: HomeItem?,
    onSelectItem: (HomeItem) -> Unit,
    onLogin: (String, String, String, Int) -> Unit,
    vmList: List<HomeItem>
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Default.route,
        modifier = modifier
    ) {
        composable(route = Screen.Default.route) {
            HomeDefaultScreen(
                onConnectClick = { navController.navigate(Screen.Login.route) }
            )
        }
        composable(route = Screen.Login.route) {
            LoginScreen(
                onLoginClick = { username, hostname, password, port -> 
                    onLogin(username, hostname, password, port)
                }
            )
        }
        composable(route = Screen.Home.route) {
            HomeScreen(
                vmList = vmList,
                onItemClick = { item ->
                    onSelectItem(item)
                    navController.navigate(Screen.VmDetail.route)
                }
            )
        }
        composable(route = Screen.VmDetail.route) {
            selectedItem?.let { item ->
                VmScreen(
                    item = item,
                    onSave = { navController.popBackStack() },
                    onRestore = { navController.popBackStack() },
                    onCancel = { navController.popBackStack() }
                )
            }
        }
    }
}

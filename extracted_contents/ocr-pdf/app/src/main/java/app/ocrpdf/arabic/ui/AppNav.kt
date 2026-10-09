package app.ocrpdf.arabic.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun AppNav(vm: MainViewModel) {
    val nav = rememberNavController()
    val snack = remember { SnackbarHostState() }
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { for (m in vm.messages) snack.showSnackbar(ctx.getString(m)) }

    NavHost(nav, startDestination = "home") {
        composable("home") {
            HomeScreen(vm, snack, onOpen = { nav.navigate("job/$it") }, onSettings = { nav.navigate("settings") })
        }
        composable("job/{id}") { e ->
            JobScreen(vm, snack, e.arguments!!.getString("id")!!, onBack = { nav.popBackStack() })
        }
        composable("settings") { SettingsScreen(vm, onBack = { nav.popBackStack() }) }
    }
}

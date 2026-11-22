package com.decmike.gestion

import android.Manifest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.decmike.gestion.ui.AmbianceEditScreen
import com.decmike.gestion.ui.AudioScreen
import com.decmike.gestion.ui.AudiogramScreen
import com.decmike.gestion.ui.DeviceEditScreen
import com.decmike.gestion.ui.DuplicatesScreen
import com.decmike.gestion.ui.FilesScreen
import com.decmike.gestion.ui.HearingTestScreen
import com.decmike.gestion.ui.ListenerEditScreen
import com.decmike.gestion.ui.PlaylistScreen
import com.decmike.gestion.ui.PreviewScreen
import com.decmike.gestion.ui.SettingsScreen
import com.decmike.gestion.ui.SoundScreen
import com.decmike.gestion.ui.hasStorageAccess
import com.decmike.gestion.ui.theme.GestionTheme

private data class Dest(val route: String, val label: String, val icon: ImageVector)

private val DESTS = listOf(
    Dest("files", "Fichiers", Icons.Filled.Folder),
    Dest("duplicates", "Doublons", Icons.Filled.ContentCopy),
    Dest("audio", "Musique", Icons.Filled.LibraryMusic),
    Dest("sound", "Profils", Icons.Filled.Equalizer),
    Dest("settings", "Sources", Icons.Filled.Settings),
)

class MainActivity : ComponentActivity() {
    private var permissionTick by mutableIntStateOf(0)
    private var hadAccess = false

    private val runtimePerms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionTick++
        app.files.rescanAsync()
    }

    private val app get() = application as GestionApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestRuntimePermissions()
        hadAccess = hasStorageAccess()
        if (hadAccess && app.files.files.value.isEmpty()) app.files.rescanAsync()
        setContent {
            GestionTheme { Root(app, permissionTick) }
        }
    }

    override fun onResume() {
        super.onResume()
        permissionTick++
        // L'utilisateur revient des réglages après avoir accordé l'accès aux fichiers.
        val now = hasStorageAccess()
        if (now && !hadAccess) app.files.rescanAsync()
        hadAccess = now
        app.player.connect()
    }

    private fun requestRuntimePermissions() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms += Manifest.permission.POST_NOTIFICATIONS
            perms += Manifest.permission.READ_MEDIA_AUDIO
            perms += Manifest.permission.READ_MEDIA_VIDEO
            perms += Manifest.permission.READ_MEDIA_IMAGES
        } else {
            perms += Manifest.permission.READ_EXTERNAL_STORAGE
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) perms += Manifest.permission.WRITE_EXTERNAL_STORAGE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) perms += Manifest.permission.BLUETOOTH_CONNECT
        runtimePerms.launch(perms.toTypedArray())
    }
}

@Composable
private fun Root(app: GestionApp, permissionTick: Int) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    Scaffold(
        bottomBar = {
            if (DESTS.any { it.route == route }) NavigationBar {
                DESTS.forEach { d ->
                    NavigationBarItem(
                        selected = route == d.route,
                        onClick = { nav.goTab(d.route) },
                        icon = { Icon(d.icon, null) },
                        label = { Text(d.label) }
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(bottom = pad.calculateBottomPadding()).consumeWindowInsets(PaddingValues(bottom = pad.calculateBottomPadding()))) {
            NavHost(nav, startDestination = "files") {
                composable("files") {
                    FilesScreen(app, onOpen = { nav.navigate("preview?uri=" + Uri.encode(it.uri)) }, permissionTick)
                }
                composable("duplicates") {
                    DuplicatesScreen(app, onOpen = { nav.navigate("preview?uri=" + Uri.encode(it.uri)) })
                }
                composable("audio") {
                    AudioScreen(app, onOpenPlaylist = { nav.navigate("playlist/$it") }, onOpenSound = { nav.goTab("sound") })
                }
                composable("sound") {
                    SoundScreen(
                        app,
                        onListener = { nav.navigate("listener/$it") },
                        onDevice = { nav.navigate("device/$it") },
                        onAmbiance = { nav.navigate("ambiance/$it") },
                    )
                }
                composable("settings") { SettingsScreen(app, permissionTick) }
                composable(
                    "preview?uri={uri}",
                    arguments = listOf(navArgument("uri") { type = NavType.StringType; defaultValue = "" })
                ) { e ->
                    PreviewScreen(app, e.arguments?.getString("uri").orEmpty()) { nav.popBackStack() }
                }
                composable("playlist/{id}") { e -> PlaylistScreen(app, e.arguments?.getString("id").orEmpty()) { nav.popBackStack() } }
                composable("listener/{id}") { e ->
                    val id = e.arguments?.getString("id").orEmpty()
                    ListenerEditScreen(
                        app, id, onBack = { nav.popBackStack() },
                        onAudiogram = { nav.navigate("audiogram/$id") },
                        onTest = { nav.navigate("hearing/$id") },
                    )
                }
                composable("audiogram/{id}") { e -> AudiogramScreen(app, e.arguments?.getString("id").orEmpty()) { nav.popBackStack() } }
                composable("hearing/{id}") { e -> HearingTestScreen(app, e.arguments?.getString("id").orEmpty()) { nav.popBackStack() } }
                composable("device/{id}") { e -> DeviceEditScreen(app, e.arguments?.getString("id").orEmpty()) { nav.popBackStack() } }
                composable("ambiance/{id}") { e -> AmbianceEditScreen(app, e.arguments?.getString("id").orEmpty()) { nav.popBackStack() } }
            }
        }
    }
}

private fun NavHostController.goTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

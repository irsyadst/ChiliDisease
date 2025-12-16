package com.irsyad.chilidisease

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

// Import Screens & Utils
import com.irsyad.chilidisease.navigation.Screen
import com.irsyad.chilidisease.ui.screens.AboutScreen
import com.irsyad.chilidisease.ui.screens.HomeScreen
import com.irsyad.chilidisease.ui.screens.ScanScreen
import com.irsyad.chilidisease.ui.screens.StaticResultScreen
import com.irsyad.chilidisease.ui.theme.ChiliDiseaseTheme
import com.irsyad.chilidisease.utils.ImageHolder

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()

        val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        setContent {
            ChiliDiseaseTheme {
                MainApp(cameraExecutor)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}

@Composable
fun MainApp(cameraExecutor: ExecutorService) {
    val navController = rememberNavController()
    val items = listOf(Screen.Home, Screen.Scan, Screen.About)

    // --- FUNGSI NAVIGASI PINTAR ---
    // Fungsi ini memastikan saat pindah menu (baik dari tombol Home maupun BottomBar),
    // perilakunya konsisten: tidak menumpuk layar, tapi berpindah tab.
    val navigateToTab = { route: String ->
        navController.navigate(route) {
            // Pop up ke start destination (Home) agar tidak ada tumpukan back stack yang aneh
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            // Hindari membuat instance ganda jika diklik berulang
            launchSingleTop = true
            // Restore state (posisi scroll, dll) jika kembali
            restoreState = true
        }
    }

    Scaffold(
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route

            // Sembunyikan BottomBar hanya di layar Hasil Foto
            if (currentRoute != Screen.StaticResult.route) {
                NavigationBar {
                    items.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(if (currentRoute == screen.route) screen.selectedIcon else screen.unselectedIcon, contentDescription = screen.title) },
                            label = { Text(screen.title) },
                            selected = currentRoute == screen.route,
                            onClick = { navigateToTab(screen.route) } // Gunakan fungsi navigasi pintar
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    // Update aksi tombol agar menggunakan navigasi tab
                    onLiveScanClick = { navigateToTab(Screen.Scan.route) },
                    onAboutClick = { navigateToTab(Screen.About.route) },
                    onImagePicked = { bitmap ->
                        ImageHolder.image = bitmap
                        // Untuk ke StaticResult tetap pakai navigate biasa (Push) karena ini sub-menu
                        navController.navigate(Screen.StaticResult.route)
                    }
                )
            }
            composable(Screen.Scan.route) { ScanScreen(cameraExecutor) }
            composable(Screen.About.route) { AboutScreen() }
            composable(Screen.StaticResult.route) {
                StaticResultScreen(onBackClick = { navController.popBackStack() })
            }
        }
    }
}
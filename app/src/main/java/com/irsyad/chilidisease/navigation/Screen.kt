package com.irsyad.chilidisease.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.Camera
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val selectedIcon: ImageVector, val unselectedIcon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Filled.Home, Icons.Outlined.Home)
    object Scan : Screen("scan", "Scan", Icons.Filled.Camera, Icons.Outlined.Camera)
    object About : Screen("about", "Penyakit", Icons.Filled.Info, Icons.Outlined.Info)
    object StaticResult : Screen("static_result", "Hasil Analisis", Icons.Filled.Info, Icons.Outlined.Info)
}
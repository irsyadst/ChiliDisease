package com.irsyad.chilidisease.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// --- IMPORT DARI FILE LAIN (Modular) ---
import com.irsyad.chilidisease.utils.DiseaseInfo
import com.irsyad.chilidisease.utils.DiseaseList // Mengambil data dari DiseaseData.kt
import com.irsyad.chilidisease.ui.components.DiseaseListCard // Mengambil UI Card dari DiseaseComponents.kt
import com.irsyad.chilidisease.ui.components.DiseaseDetailView // Mengambil UI Detail dari DiseaseComponents.kt
// --------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen() {
    // State untuk navigasi (List <-> Detail)
    var selectedDisease by remember { mutableStateOf<DiseaseInfo?>(null) }

    // Handle tombol kembali
    BackHandler(enabled = selectedDisease != null) {
        selectedDisease = null
    }

    if (selectedDisease == null) {
        // --- TAMPILAN LIST ---
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text(
                "Tentang Tanamanan",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // List Penyakit
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Menggunakan data 'DiseaseList' dari utils/DiseaseData.kt
                items(DiseaseList) { disease ->
                    // Menggunakan komponen 'DiseaseListCard' dari ui/components/DiseaseComponents.kt
                    DiseaseListCard(disease = disease) {
                        selectedDisease = disease
                    }
                }
            }
        }
    } else {
        // --- TAMPILAN DETAIL ---
        // Menggunakan komponen 'DiseaseDetailView' dari ui/components/DiseaseComponents.kt
        DiseaseDetailView(disease = selectedDisease!!) {
            selectedDisease = null // Kembali ke list saat tombol back ditekan
        }
    }
}
package com.irsyad.chilidisease.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class DiseaseInfo(val name: String, val description: String)

@Composable
fun AboutScreen() {
    val diseases = listOf(
        DiseaseInfo("Bercak Daun Serkospora", "Penyakit jamur yang menyebabkan bercak bulat pada daun dengan pusat abu-abu dan pinggiran coklat gelap."),
        DiseaseInfo("Buah Cabai Sehat", "Buah cabai dalam kondisi prima, bebas dari bercak, busuk, atau deformasi."),
        DiseaseInfo("Busuk Buah Antraknosa", "Infeksi jamur menyebabkan lesi cekung berair pada buah, seringkali dengan spora berwarna pink/orange."),
        DiseaseInfo("Daun Cabai Sehat", "Daun berwarna hijau segar, bentuk normal, dan bebas dari bercak atau keriting."),
        DiseaseInfo("Virus Kuning", "Penyakit virus (seringkali Gemini Virus) menyebabkan daun menguning cerah dan mengeriting.")
    )
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Ensiklopedia Penyakit", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(diseases) { disease ->
                Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(disease.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(disease.description, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
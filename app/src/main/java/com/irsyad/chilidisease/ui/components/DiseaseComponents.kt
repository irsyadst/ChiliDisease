package com.irsyad.chilidisease.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.irsyad.chilidisease.utils.DiseaseInfo

// --- KOMPONEN KARTU (LIST) ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiseaseListCard(disease: DiseaseInfo, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(id = disease.imageRes),
                contentDescription = disease.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).background(Color.LightGray)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(disease.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Klik untuk detail...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

// --- KOMPONEN DETAIL (FULL PAGE) ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiseaseDetailView(disease: DiseaseInfo, onBackClick: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(disease.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) { Icon(Icons.Filled.ArrowBack, "Kembali") }
                },
                windowInsets = WindowInsets(0.dp) // Fix App Bar Lebar
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            Image(
                painter = painterResource(id = disease.imageRes),
                contentDescription = disease.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(250.dp).background(Color.Gray)
            )
            Column(modifier = Modifier.padding(16.dp)) {
                Text(disease.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(disease.description, style = MaterialTheme.typography.bodyLarge)

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                DetailSection("Penyebab", disease.cause, Color(0xFFFFEBEE))
                Spacer(modifier = Modifier.height(12.dp))
                DetailSection("Pencegahan", disease.prevention, Color(0xFFE8F5E9))
                Spacer(modifier = Modifier.height(12.dp))
                DetailSection("Penanganan", disease.treatment, Color(0xFFFFF3E0))
            }
        }
    }
}

@Composable
fun DetailSection(title: String, content: String, color: Color) {
    Card(
        colors = CardDefaults.cardColors(containerColor = color),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.Black)
            Spacer(modifier = Modifier.height(4.dp))
            Text(content, style = MaterialTheme.typography.bodyMedium, color = Color.Black)
        }
    }
}
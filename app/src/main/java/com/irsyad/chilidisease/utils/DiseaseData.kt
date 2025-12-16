package com.irsyad.chilidisease.utils

import com.irsyad.chilidisease.R

// 1. Data Class dipindah ke sini agar bisa dipakai semua layar
data class DiseaseInfo(
    val name: String,
    val description: String,
    val cause: String,
    val prevention: String,
    val treatment: String,
    val imageRes: Int
)

// 2. Daftar Penyakit (Sumber Data Tunggal)
val DiseaseList = listOf(
    DiseaseInfo(
        name = "Bercak Daun Serkospora",
        description = "Bercak bulat kecil dengan pusat berwarna abu-abu muda atau putih dan tepi coklat tua.",
        cause = "Disebabkan oleh jamur Cercospora capsici. Jamur ini berkembang pesat pada kondisi kelembaban tinggi dan suhu hangat.",
        prevention = "• Gunakan jarak tanam yang tidak terlalu rapat.\n• Perbaiki drainase lahan agar tidak menggenang.\n• Gunakan mulsa plastik.\n• Rotasi tanaman dengan tanaman bukan famili Solanaceae.",
        treatment = "• Pangkas dan bakar daun yang terinfeksi.\n• Semprotkan fungisida berbahan aktif tembaga atau mankozeb sesuai dosis.",
        imageRes = R.drawable.bercak_daun_serkospora
    ),
    DiseaseInfo(
        name = "Busuk Buah Antraknosa",
        description = "Bercak hitam melingkar atau cekung pada buah cabai yang menyebabkan pembusukan basah.",
        cause = "Infeksi jamur Colletotrichum sp. (terutama C. capsici dan C. gloeosporioides). Spora menyebar melalui air hujan atau cipratan air.",
        prevention = "• Gunakan benih sehat/tahan penyakit.\n• Sanitasi lahan dari gulma dan sisa tanaman sakit.\n• Hindari penyiraman curah dari atas (sprinkler).",
        treatment = "• Petik dan musnahkan (bakar/kubur) buah yang terserang.\n• Aplikasikan fungisida sistemik (seperti difenokonazol) atau kontak (tembaga hidroksida) secara bergantian.",
        imageRes = R.drawable.busuh_buah_antraknosa
    ),
    DiseaseInfo(
        name = "Virus Kuning (Gemini)",
        description = "Daun berwarna kuning terang, mengeriting, ukuran daun mengecil, dan tanaman menjadi kerdil.",
        cause = "Disebabkan oleh Gemini Virus yang ditularkan oleh hama vektor Kutu Kebul (Bemisia tabaci).",
        prevention = "• Gunakan varietas tahan virus.\n• Gunakan mulsa perak untuk memantulkan cahaya (mengusir kutu).\n• Tanam tanaman pagar (jagung) di keliling lahan.",
        treatment = "• Cabut dan bakar tanaman yang terinfeksi agar tidak menular (Eradikasi).\n• Kendalikan kutu kebul dengan insektisida atau perangkap kuning.",
        imageRes = R.drawable.virus_kuning
    ),
    DiseaseInfo(
        name = "Daun Cabai Sehat",
        description = "Daun berwarna hijau segar, bentuk normal, rata, dan bebas dari bercak atau lubang.",
        cause = "Perawatan yang baik, nutrisi cukup, dan lingkungan yang mendukung.",
        prevention = "Pertahankan pola pemupukan dan penyiraman yang teratur.",
        treatment = "Lanjutkan perawatan rutin. Monitor secara berkala untuk deteksi dini hama.",
        imageRes = R.drawable.daun_cabai_sehat
    ),
    DiseaseInfo(
        name = "Buah Cabai Sehat",
        description = "Buah cabai berbentuk sempurna, kulit mulus mengkilap, warna cerah (hijau/merah) merata.",
        cause = "Tanaman sehat dan bebas dari serangan hama lalat buah atau jamur.",
        prevention = "Jaga kebersihan lahan dan cukupi kebutuhan unsur hara Kalium dan Kalsium.",
        treatment = "Siap panen saat tingkat kematangan sesuai.",
        imageRes = R.drawable.buah_cabai_sehat
    )
)

// 3. Fungsi Helper untuk Mencari Penyakit Berdasarkan Label YOLO
fun getDiseaseByLabel(label: String): DiseaseInfo? {
    return when {
        label.contains("Serkospora", true) || label.contains("Leaf Spot", true) ->
            DiseaseList.find { it.name.contains("Serkospora", true) }

        label.contains("Antraknosa", true) || label.contains("Anthracnose", true) ->
            DiseaseList.find { it.name.contains("Antraknosa", true) }

        label.contains("Kuning", true) || label.contains("Virus", true) ->
            DiseaseList.find { it.name.contains("Kuning", true) }

        label.contains("Sehat", true) || label.contains("Healthy", true) ->
            // Cek apakah buah atau daun (bisa disesuaikan jika model membedakan)
            if (label.contains("Buah", true) || label.contains("Fruit", true)) {
                DiseaseList.find { it.name.contains("Buah Cabai Sehat", true) }
            } else {
                DiseaseList.find { it.name.contains("Daun Cabai Sehat", true) }
            }

        else -> null
    }
}
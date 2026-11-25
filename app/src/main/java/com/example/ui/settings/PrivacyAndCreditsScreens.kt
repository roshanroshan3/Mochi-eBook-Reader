package com.example.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalMochiPalette

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    val palette = LocalMochiPalette.current
    val items = listOf(
        "Local-First Reading & Notes" to "Reading sessions, highlights, bookmarks, CAD markups, and theme settings stay strictly in Mochi's local SQLite & DataStore storage.",
        "Optional Project Gutenberg Access" to "Network permission (INTERNET) is used exclusively when you choose to browse, preview covers, or download free public-domain ebooks from Project Gutenberg via Gutendex.",
        "Zero Accounts or Telemetry" to "No sign-in, no analytics, no crash trackers, and no ads.",
        "Scoped Storage Access (SAF)" to "Mochi only reads local folders and files you explicitly pick through Android's Storage Access Framework.",
        "Non-Destructive Guarantee" to "Mochi never modifies your original EPUB, PDF, CBZ, Markdown, or TXT files."
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(palette.background))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
                .align(Alignment.TopCenter)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("privacy_back_button")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color(palette.text))
                    }
                    Text("Privacy & Access", style = MaterialTheme.typography.headlineMedium, color = Color(palette.text))
                }
            }
            items(items) { (title, body) ->
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(palette.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(palette.textSecondary).copy(alpha = 0.14f), RoundedCornerShape(20.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(title, style = MaterialTheme.typography.titleMedium, color = Color(palette.text))
                        Text(body, style = MaterialTheme.typography.bodyMedium, color = Color(palette.textSecondary))
                    }
                }
            }
        }
    }
}

private data class CreditEntry(
    val name: String,
    val category: String,
    val license: String
)

@Composable
fun CreditsScreen(onBack: () -> Unit) {
    val palette = LocalMochiPalette.current
    val credits = listOf(
        CreditEntry("Inter", "UI Sans Font", "SIL Open Font License 1.1"),
        CreditEntry("Literata", "Long-Reading Serif Font", "SIL Open Font License 1.1"),
        CreditEntry("Lexend", "Dyslexia-Friendly Font", "SIL Open Font License 1.1"),
        CreditEntry("JetBrains Mono", "Monospace Font", "SIL Open Font License 1.1"),
        CreditEntry("Caveat & Patrick Hand", "Handwriting & Notes Fonts", "SIL Open Font License 1.1"),
        CreditEntry("Catppuccin, Nord, Dracula, Rosé Pine, Tokyo Night, Gruvbox, Everforest, Kanagawa, Solarized, One Dark", "Bundled Color Palettes", "MIT / Permissive Community Palettes"),
        CreditEntry("Mochi Core English Lexicon (WordNet-style)", "Bundled Offline Dictionary", "WordNet 3.0 / CC0-1.0 Permissive Format"),
        CreditEntry("StarDict (.ifo/.idx/.dict), dictd (.index/.dict) & Wiktionary/Kaikki JSONL", "Supported Offline Dictionary Formats", "Open Dictionary Specifications · SAF Imported"),
        CreditEntry("SuperMemo SM-2 & Anki TSV/CSV", "Spaced Repetition Vocabulary Engine", "Public Domain Algorithm & Open Deck Format"),
        CreditEntry("Android PdfRenderer & PdfDocument", "Fixed-Layout PDF Rendering & Annotated Copy Export", "Android Platform Graphics API"),
        CreditEntry("AGSL RuntimeShader & Compose GraphicsLayer", "Interactive GPU Page Curl & Liquid Glass Specular Shading", "Android Graphics Shading Language (API 33+) & Compose Canvas Fallback"),
        CreditEntry("CAD Vector Studio (SVG / JSON)", "Layered Vector Markups & Non-Destructive CAD Exports", "Open SVG 1.1 & Portable JSON Formats"),
        CreditEntry("Jetpack Compose, Material 3, Room, DataStore, Navigation, WorkManager, DocumentFile", "AndroidX Libraries", "Apache License 2.0"),
        CreditEntry("Project Gutenberg & Gutendex API", "Public-Domain Free Ebook Catalog", "Public Domain Books & MIT Gutendex API"),
        CreditEntry("Coil & OkHttp", "Image Caching & Ebook Catalog Networking", "Apache License 2.0"),
        CreditEntry("Moshi", "Theme Pack & Catalog JSON Serialization", "Apache License 2.0"),
        CreditEntry("Material Symbols / Icons", "UI Iconography", "Apache License 2.0")
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(palette.background))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
                .align(Alignment.TopCenter)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("credits_back_button")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color(palette.text))
                    }
                    Text("Credits & Licenses", style = MaterialTheme.typography.headlineMedium, color = Color(palette.text))
                }
            }
            items(credits) { entry ->
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(palette.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(palette.textSecondary).copy(alpha = 0.14f), RoundedCornerShape(18.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(entry.name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = Color(palette.text))
                        Text("${entry.category} · ${entry.license}", style = MaterialTheme.typography.bodySmall, color = Color(palette.textSecondary))
                    }
                }
            }
        }
    }
}

package com.example.ui.reader

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.db.BookEntity
import com.example.data.reader.FootnoteEntry
import com.example.data.reader.ParsedDocument
import com.example.data.reader.ReaderDocumentCache
import com.example.data.reader.ReaderSearchMatch
import com.example.theme.ContrastChecker
import com.example.theme.MochiThemePalette
import com.example.ui.designsystem.LiquidGlassSurface
import com.example.ui.theme.LocalMochiPalette

@Composable
fun TocContentList(
    document: ParsedDocument,
    chapterStartPages: Map<Int, Int>,
    currentChapterIndex: Int,
    onJumpToChapter: (Int) -> Unit,
    onOpenBacklinkBook: (BookEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalMochiPalette.current
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        item {
            Text(
                text = "Table of Contents",
                style = MaterialTheme.typography.titleLarge,
                color = Color(palette.text)
            )
            Spacer(modifier = Modifier.height(6.dp))
        }
        items(document.chapters, key = { it.index }) { chapter ->
            val selected = chapter.index == currentChapterIndex
            val startPage = (chapterStartPages[chapter.index] ?: 0) + 1
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (selected) Color(palette.accentContainer) else Color(palette.surfaceVariant).copy(alpha = 0.45f),
                onClick = { onJumpToChapter(chapter.index) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = chapter.title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (selected) ContrastChecker.bestOnColor(Color(palette.accentContainer)) else Color(palette.text),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "p. $startPage",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(palette.textSecondary)
                    )
                }
            }
        }

        if (document.backlinks.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Vault Backlinks",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(palette.text)
                )
            }
            items(document.backlinks, key = { "bl_${it.id}" }) { linked ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(palette.surface),
                    onClick = { onOpenBacklinkBook(linked) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(palette.textSecondary).copy(alpha = 0.16f), RoundedCornerShape(14.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Rounded.Link, contentDescription = null, tint = Color(palette.link))
                        Column {
                            Text(linked.title, style = MaterialTheme.typography.titleSmall, color = Color(palette.text))
                            Text(linked.authors, style = MaterialTheme.typography.bodySmall, color = Color(palette.textSecondary))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSearchSheet(
    query: String,
    results: List<ReaderSearchMatch>,
    onQueryChange: (String) -> Unit,
    onSelectMatch: (ReaderSearchMatch) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalMochiPalette.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(palette.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 640.dp)
                .align(Alignment.CenterHorizontally)
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                placeholder = { Text("Search in book…") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("in_book_search_input")
            )

            Text(
                text = if (query.length < 2) "Type at least 2 characters" else "${results.size} match(es)",
                style = MaterialTheme.typography.labelMedium,
                color = Color(palette.textSecondary)
            )

            LazyColumn(
                contentPadding = PaddingValues(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(340.dp)
            ) {
                items(results) { match ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(palette.surfaceVariant).copy(alpha = 0.45f),
                        onClick = {
                            onSelectMatch(match)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(match.chapterTitle, style = MaterialTheme.typography.labelMedium, color = Color(palette.link))
                            Text(
                                text = buildAnnotatedString {
                                    append("…")
                                    append(match.snippetBefore)
                                    withStyle(SpanStyle(background = Color(palette.accentContainer), fontWeight = FontWeight.Bold)) {
                                        append(match.matchText)
                                    }
                                    append(match.snippetAfter)
                                    append("…")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(palette.text),
                                maxLines = 3
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FootnotePopoverDialog(
    footnote: FootnoteEntry,
    onDismiss: () -> Unit
) {
    val palette = LocalMochiPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(palette.surface),
        title = { Text(footnote.title, color = Color(palette.text)) },
        text = { Text(footnote.body, style = MaterialTheme.typography.bodyMedium, color = Color(palette.text)) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color(palette.link))
            }
        }
    )
}

@Composable
fun FullScreenImageViewer(
    bookUri: String,
    inlineImages: Map<String, ByteArray>,
    imageKey: String,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, bookUri, imageKey) {
        value = withContext(Dispatchers.IO) {
            ReaderDocumentCache.loadAndDecodeImageBlock(
                context = context,
                bookUri = bookUri,
                inlineImages = inlineImages,
                imageKey = imageKey,
                maxDimensionPx = 2200
            )?.asImageBitmap()
        }
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 6f)
                        offset = if (scale > 1f) offset + pan else Offset.Zero
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > 1.2f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2.5f
                            }
                        }
                    )
                }
        ) {
            val loadedBitmap = bitmap
            if (loadedBitmap != null) {
                Image(
                    bitmap = loadedBitmap,
                    contentDescription = "Zoomed illustration",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        }
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Close image viewer", tint = Color.White)
            }
        }
    }
}

data class ReaderCommandItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val keywords: String,
    val execute: () -> Unit
)

@Composable
fun CommandPaletteDialog(
    themes: List<MochiThemePalette>,
    totalPages: Int,
    commands: List<ReaderCommandItem>,
    onGoToPage: (Int) -> Unit,
    onSelectTheme: (MochiThemePalette) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalMochiPalette.current
    var query by remember { mutableStateOf("") }

    val dynamicCommands = remember(query, commands, themes, totalPages) {
        val q = query.trim().lowercase()
        val list = mutableListOf<ReaderCommandItem>()

        // "go to 42" or bare number
        val pageMatch = Regex("^(?:go\\s+to\\s+|page\\s+)?(\\d+)$").matchEntire(q)
        if (pageMatch != null) {
            val targetPage = pageMatch.groupValues[1].toIntOrNull()?.coerceIn(1, totalPages) ?: 1
            list.add(
                ReaderCommandItem(
                    id = "goto_$targetPage",
                    title = "Go to page $targetPage",
                    subtitle = "Jump directly to page $targetPage of $totalPages",
                    keywords = "go to page"
                ) {
                    onGoToPage(targetPage - 1)
                }
            )
        }

        // Theme commands: "theme nord"
        val themeQuery = if (q.startsWith("theme ")) q.removePrefix("theme ").trim() else q
        if (themeQuery.isNotBlank()) {
            themes.filter { it.name.lowercase().contains(themeQuery) }.take(5).forEach { t ->
                list.add(
                    ReaderCommandItem(
                        id = "theme_${t.id}",
                        title = "Theme: ${t.name}",
                        subtitle = if (t.isDark) "Switch to dark theme" else "Switch to light theme",
                        keywords = "theme ${t.name}"
                    ) {
                        onSelectTheme(t)
                    }
                )
            }
        }

        val matchingStatic = commands.filter {
            q.isEmpty() || it.title.lowercase().contains(q) || it.keywords.lowercase().contains(q)
        }
        list + matchingStatic
    }

    Dialog(onDismissRequest = onDismiss) {
        LiquidGlassSurface(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Terminal, contentDescription = null, tint = Color(palette.accent))
                    Text("Command Palette", style = MaterialTheme.typography.titleMedium, color = Color(palette.text))
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Type action, \"go to 4\", \"theme nord\"…") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("command_palette_input")
                )
                LazyColumn(
                    modifier = Modifier.height(260.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(dynamicCommands, key = { it.id }) { cmd ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(palette.surfaceVariant).copy(alpha = 0.5f),
                            onClick = {
                                cmd.execute()
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.MenuBook, contentDescription = null, tint = Color(palette.link), modifier = Modifier.size(18.dp))
                                Column {
                                    Text(cmd.title, style = MaterialTheme.typography.titleSmall, color = Color(palette.text))
                                    Text(cmd.subtitle, style = MaterialTheme.typography.bodySmall, color = Color(palette.textSecondary))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

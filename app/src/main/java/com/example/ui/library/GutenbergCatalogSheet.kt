package com.example.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.library.GutenbergBook
import com.example.data.library.GutenbergRepository
import com.example.theme.ContrastChecker
import com.example.ui.designsystem.LiquidGlassSurface
import com.example.ui.theme.LocalMochiPalette
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GutenbergCatalogSheet(
    state: GutenbergCatalogState,
    onDismiss: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: (String, String) -> Unit,
    onSelectTopic: (String) -> Unit,
    onDownloadBook: (GutenbergBook) -> Unit,
    onOpenDownloadedBook: (Long) -> Unit,
    onLoadMore: () -> Unit
) {
    val palette = LocalMochiPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(palette.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.90f)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(palette.accentContainer)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Public,
                            contentDescription = null,
                            tint = ContrastChecker.bestOnColor(Color(palette.accentContainer))
                        )
                    }
                    Column {
                        Text(
                            text = "Project Gutenberg",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color(palette.text)
                        )
                        Text(
                            text = if (state.totalCount > 0) {
                                "${state.totalCount} free public-domain classics"
                            } else {
                                "70,000+ free public-domain EPUB & TXT classics"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(palette.textSecondary)
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("gutenberg_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close Project Gutenberg catalog",
                        tint = Color(palette.textSecondary)
                    )
                }
            }

            // Search bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LiquidGlassSurface(
                    shape = CircleShape,
                    elevation = 4.dp,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = Color(palette.textSecondary),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (state.query.isEmpty()) {
                                Text(
                                    text = "Search author or classic title…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(palette.textSecondary),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            BasicTextField(
                                value = state.query,
                                onValueChange = onQueryChange,
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(
                                    onSearch = { onSearch(state.query, state.selectedTopic) }
                                ),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color(palette.text)
                                ),
                                cursorBrush = SolidColor(Color(palette.accent)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("gutenberg_search_input")
                            )
                        }
                        if (state.query.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    onQueryChange("")
                                    onSearch("", state.selectedTopic)
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Clear search",
                                    tint = Color(palette.textSecondary),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
                Button(
                    onClick = { onSearch(state.query, state.selectedTopic) },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(palette.accent),
                        contentColor = ContrastChecker.bestOnColor(Color(palette.accent))
                    ),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("gutenberg_search_button")
                ) {
                    Text("Go")
                }
            }

            // Topic chips
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GutenbergRepository.TOPICS.forEach { topic ->
                    val selected = state.selectedTopic == topic
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectTopic(topic) },
                        label = { Text(topic) },
                        modifier = Modifier.testTag("gutenberg_topic_${topic.lowercase().replace(" ", "_")}")
                    )
                }
            }

            // Body
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.isLoading && state.books.isEmpty() -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(color = Color(palette.accent))
                            Text(
                                text = "Fetching Project Gutenberg catalog…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(palette.textSecondary)
                            )
                        }
                    }

                    state.errorMessage != null && state.books.isEmpty() -> {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Couldn't reach Project Gutenberg",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(palette.text)
                            )
                            Text(
                                text = state.errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(palette.textSecondary)
                            )
                            OutlinedButton(
                                onClick = { onSearch(state.query, state.selectedTopic) },
                                modifier = Modifier.testTag("gutenberg_retry_button")
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Retry")
                            }
                        }
                    }

                    state.books.isEmpty() -> {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MenuBook,
                                contentDescription = null,
                                tint = Color(palette.textSecondary),
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = "No matching classics found",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(palette.text)
                            )
                            Text(
                                text = "Try another author, title, or topic filter.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(palette.textSecondary)
                            )
                        }
                    }

                    else -> {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (state.isLoading) {
                                item(key = "loading_bar") {
                                    LinearProgressIndicator(
                                        color = Color(palette.accent),
                                        trackColor = Color(palette.surfaceVariant),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(CircleShape)
                                    )
                                }
                            }
                            items(state.books, key = { it.id }) { book ->
                                val progress = state.downloadProgress[book.id]
                                val existingBookId = state.downloadedBookIds[book.id]
                                GutenbergBookRow(
                                    book = book,
                                    downloadProgress = progress,
                                    downloadedBookId = existingBookId,
                                    onDownload = { onDownloadBook(book) },
                                    onOpen = { existingBookId?.let(onOpenDownloadedBook) }
                                )
                            }
                            if (state.nextUrl != null) {
                                item(key = "load_more") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (state.isLoadingMore) {
                                            CircularProgressIndicator(
                                                color = Color(palette.accent),
                                                modifier = Modifier.size(28.dp)
                                            )
                                        } else {
                                            OutlinedButton(
                                                onClick = onLoadMore,
                                                shape = CircleShape,
                                                modifier = Modifier.testTag("gutenberg_load_more_button")
                                            ) {
                                                Text("Load More Classics")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GutenbergBookRow(
    book: GutenbergBook,
    downloadProgress: Float?,
    downloadedBookId: Long?,
    onDownload: () -> Unit,
    onOpen: () -> Unit
) {
    val palette = LocalMochiPalette.current
    val downloadsFormatted = when {
        book.downloadCount >= 1000 -> String.format("%.1fk", book.downloadCount / 1000f)
        else -> book.downloadCount.toString()
    }
    val langs = book.languages.joinToString("/").uppercase().ifBlank { "EN" }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(palette.surfaceVariant).copy(alpha = 0.42f),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(palette.textSecondary).copy(alpha = 0.14f), RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .clickable {
                if (downloadedBookId != null) onOpen() else if (downloadProgress == null) onDownload()
            }
            .testTag("gutenberg_book_card_${book.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .aspectRatio(0.68f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(palette.accentContainer), Color(palette.surfaceVariant))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoStories,
                    contentDescription = null,
                    tint = Color(palette.textSecondary).copy(alpha = 0.55f),
                    modifier = Modifier.size(24.dp)
                )
                val coverUrl = book.coverUrl
                if (!coverUrl.isNullOrBlank()) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val coverRequest = remember(coverUrl) {
                        coil.request.ImageRequest.Builder(context)
                            .data(coverUrl)
                            .size(168, 250)
                            .precision(coil.size.Precision.INEXACT)
                            .scale(coil.size.Scale.FILL)
                            .allowHardware(true)
                            .allowRgb565(true)
                            .crossfade(160)
                            .build()
                    }
                    AsyncImage(
                        model = coverRequest,
                        contentDescription = "Cover for ${book.title}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = Color(palette.text),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = book.authors,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(palette.textSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${book.format.displayName} · $langs · $downloadsFormatted downloads",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(palette.link),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (downloadProgress != null) {
                    LinearProgressIndicator(
                        progress = { downloadProgress.coerceIn(0.02f, 1f) },
                        color = Color(palette.accent),
                        trackColor = Color(palette.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp)
                            .clip(CircleShape)
                    )
                }
            }

            when {
                downloadProgress != null -> {
                    val pct = (downloadProgress * 100).roundToInt().coerceIn(1, 99)
                    Surface(
                        shape = CircleShape,
                        color = Color(palette.surface),
                        modifier = Modifier.defaultMinSize(minWidth = 64.dp, minHeight = 48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
                            Text(
                                text = "$pct%",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(palette.accent)
                            )
                        }
                    }
                }

                downloadedBookId != null -> {
                    Button(
                        onClick = onOpen,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(palette.accentContainer),
                            contentColor = ContrastChecker.bestOnColor(Color(palette.accentContainer))
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("gutenberg_open_${book.id}")
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Open")
                    }
                }

                else -> {
                    Button(
                        onClick = onDownload,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(palette.accent),
                            contentColor = ContrastChecker.bestOnColor(Color(palette.accent))
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("gutenberg_get_${book.id}")
                    ) {
                        Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Get")
                    }
                }
            }
        }
    }
}

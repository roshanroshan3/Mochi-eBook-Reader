package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.db.BookEntity
import com.example.theme.MochiThemePalette
import com.example.ui.AppViewModel
import com.example.ui.designsystem.GlassNavDestination
import com.example.ui.designsystem.LiquidGlassBottomBar
import com.example.ui.designsystem.LiquidGlassNavigationRail
import com.example.ui.designsystem.LocalAnimatedVisibilityScope
import com.example.ui.designsystem.LocalSharedTransitionScope
import com.example.ui.designsystem.MochiAdaptiveContainer
import com.example.ui.designsystem.MochiWidthClass
import com.example.ui.dictionary.DictionaryManagerSheet
import com.example.ui.dictionary.DictionaryViewModel
import com.example.ui.library.BookDetailSheet
import com.example.ui.library.GutenbergCatalogSheet
import com.example.ui.library.LibraryScreen
import com.example.ui.library.LibraryViewModel
import com.example.ui.notes.NotesScreen
import com.example.ui.onboarding.OnboardingScreen
import com.example.ui.reader.ReaderScreen
import com.example.ui.reader.ReaderViewModel
import com.example.ui.settings.CreditsScreen
import com.example.ui.settings.PrivacyScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.stats.ReadingStatsSheet
import com.example.ui.theme.LocalMochiPalette
import com.example.ui.theme.MochiExpressiveTheme
import com.example.ui.themes.ThemeEditorScreen
import com.example.ui.themes.ThemeGalleryScreen

import androidx.compose.material3.MaterialTheme
import com.example.data.reader.ReaderLocator
import com.example.ui.theme.toMaterialColorScheme

object MochiRoutes {
    const val LIBRARY = "library"
    const val NOTES = "notes"
    const val SETTINGS = "settings"
    const val THEMES = "themes"
    const val THEME_EDITOR = "theme_editor"
    const val PRIVACY = "privacy"
    const val CREDITS = "credits"
    const val READER = "reader/{bookId}?morph={morphKey}&locator={locator}"

    fun readerRoute(bookId: Long, morphKey: String = "", locator: ReaderLocator? = null): String {
        val encodedMorph = Uri.encode(morphKey)
        val encodedLocator = Uri.encode(locator?.let { ReaderLocator.toJson(it) }.orEmpty())
        return "reader/$bookId?morph=$encodedMorph&locator=$encodedLocator"
    }
}

class MainActivity : ComponentActivity() {

    private var pendingIncomingUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )
        enableEdgeToEdge()
        pendingIncomingUri = extractIncomingUri(intent)
        setContent {
            MochiAppRoot(
                incomingUri = pendingIncomingUri,
                onConsumedIncomingUri = { pendingIncomingUri = null }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingIncomingUri = extractIncomingUri(intent)
    }

    private fun extractIncomingUri(intent: Intent?): Uri? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            else -> null
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MochiAppRoot(
    incomingUri: Uri?,
    onConsumedIncomingUri: () -> Unit,
    appViewModel: AppViewModel = viewModel(),
    libraryViewModel: LibraryViewModel = viewModel()
) {
    val context = LocalContext.current
    val appState by appViewModel.uiState.collectAsStateWithLifecycle()
    val libraryState by libraryViewModel.uiState.collectAsStateWithLifecycle()
    val gutenbergState by libraryViewModel.gutenbergState.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    val activePalette = appViewModel.resolveActivePalette(appState, systemDark)

    val snackbarHostState = remember { SnackbarHostState() }
    var editingTheme by remember { mutableStateOf<MochiThemePalette?>(null) }
    var themePendingExport by remember { mutableStateOf<MochiThemePalette?>(null) }
    var bookForInfoSheet by remember { mutableStateOf<BookEntity?>(null) }
    var pendingOpenedBookId by remember { mutableStateOf<Long?>(null) }
    var showReadingStatsSheet by remember { mutableStateOf(false) }
    var showGutenbergSheet by remember { mutableStateOf(false) }

    LaunchedEffect(incomingUri) {
        if (incomingUri != null) {
            appViewModel.handleIncomingFile(incomingUri) { openedId ->
                pendingOpenedBookId = openedId
            }
            onConsumedIncomingUri()
        }
    }

    LaunchedEffect(appState.statusMessage) {
        val msg = appState.statusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        appViewModel.clearMessage()
    }

    LaunchedEffect(libraryState.eventMessage) {
        val msg = libraryState.eventMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        libraryViewModel.clearEventMessage()
    }

    // SAF & Photo Picker Launchers
    val addFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) libraryViewModel.addSafFolder(uri)
    }

    val importBookLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) libraryViewModel.importFile(uri)
    }

    val importThemeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) appViewModel.importThemePack(context, uri)
    }

    val importFontLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) appViewModel.importCustomFont(uri)
    }

    val exportThemeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        val targetPalette = themePendingExport
        themePendingExport = null
        if (uri != null && targetPalette != null) {
            appViewModel.exportThemePack(context, uri, targetPalette)
        }
    }

    val shelfWallpaperLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            appViewModel.setShelfBackground(uri.toString())
        }
    }

    MochiExpressiveTheme(
        palette = activePalette,
        glassIntensity = appState.prefs.glassIntensity,
        solidChrome = appState.prefs.solidChrome,
        reduceMotion = appState.prefs.reduceMotion,
        useDynamicColor = appState.prefs.useMaterialYouDynamic,
        matchSystemBars = appState.prefs.matchSystemBars,
        warmLightEnabled = appState.prefs.warmLightEnabled,
        warmLightIntensity = appState.prefs.warmLightIntensity
    ) {
        MochiAdaptiveContainer { widthClass, _, _ ->
            if (!appState.prefs.onboardingCompleted) {
                OnboardingScreen(
                    themes = appState.themes,
                    activeTheme = activePalette,
                    onSelectTheme = appViewModel::selectTheme,
                    onFinish = { appViewModel.setOnboardingCompleted(true) }
                )
                return@MochiAdaptiveContainer
            }

            val navController = rememberNavController()
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination?.route ?: MochiRoutes.LIBRARY

            LaunchedEffect(pendingOpenedBookId) {
                val id = pendingOpenedBookId ?: return@LaunchedEffect
                pendingOpenedBookId = null
                navController.navigate(MochiRoutes.readerRoute(id))
            }

            val topLevelRoutes = setOf(MochiRoutes.LIBRARY, MochiRoutes.NOTES, MochiRoutes.SETTINGS)
            val isReaderRoute = currentRoute.startsWith("reader/")
            var libraryBottomBarVisible by remember { mutableStateOf(true) }

            val showBottomBar = widthClass == MochiWidthClass.COMPACT &&
                currentRoute in topLevelRoutes &&
                (currentRoute != MochiRoutes.LIBRARY || libraryBottomBarVisible)
            val showNavRail = widthClass != MochiWidthClass.COMPACT && !isReaderRoute

            val destinations = remember {
                listOf(
                    GlassNavDestination(
                        route = MochiRoutes.LIBRARY,
                        label = "Library",
                        selectedIcon = Icons.Rounded.AutoStories,
                        unselectedIcon = Icons.Outlined.AutoStories,
                        testTag = "nav_library"
                    ),
                    GlassNavDestination(
                        route = MochiRoutes.NOTES,
                        label = "Notes",
                        selectedIcon = Icons.Rounded.EditNote,
                        unselectedIcon = Icons.Outlined.EditNote,
                        testTag = "nav_notes"
                    ),
                    GlassNavDestination(
                        route = MochiRoutes.SETTINGS,
                        label = "Settings",
                        selectedIcon = Icons.Rounded.Settings,
                        unselectedIcon = Icons.Outlined.Settings,
                        testTag = "nav_settings"
                    )
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(activePalette.background))
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    if (showNavRail) {
                        LiquidGlassNavigationRail(
                            destinations = destinations,
                            currentRoute = currentRoute,
                            onSelect = { targetRoute ->
                                if (targetRoute != currentRoute) {
                                    navController.navigate(targetRoute) {
                                        popUpTo(MochiRoutes.LIBRARY) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        )
                    }

                    SharedTransitionLayout(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                    ) {
                        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                            NavHost(
                                navController = navController,
                                startDestination = MochiRoutes.LIBRARY,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                composable(MochiRoutes.LIBRARY) {
                                    CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                                        LibraryScreen(
                                            state = libraryState,
                                            prefs = appState.prefs,
                                            onOpenReader = { book, morphKey ->
                                                navController.navigate(MochiRoutes.readerRoute(book.id, morphKey))
                                            },
                                            onSetTab = libraryViewModel::setTab,
                                            onQueryChange = libraryViewModel::setQuery,
                                            onSetSort = libraryViewModel::setSort,
                                            onSetFormatFilter = libraryViewModel::setFormatFilter,
                                            onSetStatusFilter = libraryViewModel::setStatusFilter,
                                            onSetTagFilter = libraryViewModel::setTagFilter,
                                            onToggleDuplicatesOnly = libraryViewModel::toggleDuplicatesOnly,
                                            onToggleShowHidden = libraryViewModel::toggleShowHidden,
                                            onSetShelfStyle = appViewModel::setShelfStyle,
                                            onNavigateFolder = libraryViewModel::navigateFolder,
                                            onAddSafFolder = { addFolderLauncher.launch(null) },
                                            onImportFile = {
                                                importBookLauncher.launch(
                                                    arrayOf(
                                                        "application/epub+zip",
                                                        "application/pdf",
                                                        "application/vnd.comicbook+zip",
                                                        "application/x-cbz",
                                                        "text/markdown",
                                                        "text/plain",
                                                        "*/*"
                                                    )
                                                )
                                            },
                                            onRescanAll = libraryViewModel::rescanAll,
                                            onRemoveFolder = libraryViewModel::removeFolder,
                                            onToggleSelect = libraryViewModel::toggleSelection,
                                            onClearSelection = libraryViewModel::clearSelection,
                                            onSwapBooks = libraryViewModel::swapBooks,
                                            onStackBooks = libraryViewModel::stackBookOnto,
                                            onUnstackBook = libraryViewModel::unstackBook,
                                            onUpdateStatus = libraryViewModel::markBooksStatus,
                                            onHideBooks = libraryViewModel::hideBooks,
                                            onRemoveFromLibrary = libraryViewModel::removeBooksFromLibrary,
                                            onDeleteFilesFromDisk = libraryViewModel::deleteBooksFromDisk,
                                            onCreateCollection = libraryViewModel::createCollection,
                                            onAddBooksToCollection = libraryViewModel::addBooksToCollection,
                                            onRemoveBookFromCollection = libraryViewModel::removeBookFromCollection,
                                            onDeleteCollection = libraryViewModel::deleteCollection,
                                            onAddTag = libraryViewModel::addTagToBook,
                                            onRemoveTag = libraryViewModel::removeTagFromBook,
                                            onBottomBarVisibilityChange = { visible -> libraryBottomBarVisible = visible },
                                            onOpenStats = { showReadingStatsSheet = true },
                                            onOpenGutenbergCatalog = {
                                                libraryViewModel.openGutenbergCatalogIfNeeded()
                                                showGutenbergSheet = true
                                            }
                                        )
                                    }
                                }

                                composable(MochiRoutes.NOTES) {
                                    CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                                        NotesScreen(
                                            prefs = appState.prefs,
                                            themes = appState.themes,
                                            activeTheme = activePalette,
                                            onJumpToBookLocator = { targetBookId, targetLocator ->
                                                navController.navigate(MochiRoutes.readerRoute(targetBookId, locator = targetLocator))
                                            },
                                            onSaveVaultFolderUri = { uri ->
                                                appViewModel.updateReaderSettings { it.copy(vaultExportFolderUri = uri) }
                                            },
                                            onSetPerLanguageDefaultDict = { lang, dictId ->
                                                appViewModel.updateReaderSettings { s ->
                                                    val updated = s.perLanguageDefaultDict.toMutableMap()
                                                    if (dictId == null) updated.remove(lang) else updated[lang] = dictId
                                                    s.copy(perLanguageDefaultDict = updated)
                                                }
                                            },
                                            onShowMessage = appViewModel::showMessage
                                        )
                                    }
                                }

                                composable(MochiRoutes.SETTINGS) {
                                    val dictViewModel: DictionaryViewModel = viewModel()
                                    val dictState by dictViewModel.uiState.collectAsStateWithLifecycle()
                                    var showDictManager by remember { mutableStateOf(false) }

                                    CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                                        SettingsScreen(
                                            prefs = appState.prefs,
                                            activePalette = activePalette,
                                            onOpenThemes = { navController.navigate(MochiRoutes.THEMES) },
                                            onOpenThemeStudio = {
                                                editingTheme = activePalette
                                                navController.navigate(MochiRoutes.THEME_EDITOR)
                                            },
                                            onOpenDictionaries = { showDictManager = true },
                                            onOpenStats = { showReadingStatsSheet = true },
                                            onOpenPrivacy = { navController.navigate(MochiRoutes.PRIVACY) },
                                            onOpenCredits = { navController.navigate(MochiRoutes.CREDITS) },
                                            onApplyPreset = appViewModel::applyPreset,
                                            onSetAutoDarkMode = appViewModel::setAutoDarkMode,
                                            onSetShelfStyle = appViewModel::setShelfStyle,
                                            onSetSkeuomorphicShelf = appViewModel::setSkeuomorphicShelfEnabled,
                                            onSetGlassIntensity = appViewModel::setGlassIntensity,
                                            onSetSolidChrome = appViewModel::setSolidChrome,
                                            onSetReduceMotion = appViewModel::setReduceMotion,
                                            onSetSpineThickness = appViewModel::setSpineThicknessByPages,
                                            onSetMascotEnabled = appViewModel::setMascotEnabled,
                                            onPickShelfBackground = {
                                                shelfWallpaperLauncher.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                )
                                            },
                                            onClearShelfBackground = { appViewModel.setShelfBackground(null) },
                                            onUpdateShelfBgDimBlur = { dim, blur ->
                                                appViewModel.setShelfBackground(appState.prefs.shelfBackgroundUri, dim, blur)
                                            },
                                            onSetWarmLight = appViewModel::setWarmLight,
                                            onSetMatchSystemBars = appViewModel::setMatchSystemBars,
                                            onSelectPastelIcon = appViewModel::setSelectedPastelIcon,
                                            onReplayOnboarding = { appViewModel.setOnboardingCompleted(false) },
                                            onResetDefaults = appViewModel::resetSettings
                                        )
                                        if (showDictManager) {
                                            DictionaryManagerSheet(
                                                dictionaries = dictState.dictionaries,
                                                perLanguageDefaults = appState.prefs.readerSettings.perLanguageDefaultDict,
                                                isImporting = dictState.isImporting,
                                                onToggleEnabled = dictViewModel::toggleDictionaryEnabled,
                                                onMoveOrder = dictViewModel::moveDictionaryOrder,
                                                onUpdateLanguage = dictViewModel::updateDictionaryLanguage,
                                                onDeleteCustom = dictViewModel::deleteCustomDictionary,
                                                onSetPerLanguageDefault = { lang, dictId ->
                                                    appViewModel.updateReaderSettings { s ->
                                                        val updated = s.perLanguageDefaultDict.toMutableMap()
                                                        if (dictId == null) updated.remove(lang) else updated[lang] = dictId
                                                        s.copy(perLanguageDefaultDict = updated)
                                                    }
                                                },
                                                onImportSafTree = { uri ->
                                                    dictViewModel.importFromSafTree(uri, appViewModel::showMessage)
                                                },
                                                onImportSafFile = { uri ->
                                                    dictViewModel.importFromSafFile(uri, appViewModel::showMessage)
                                                },
                                                onDismiss = { showDictManager = false }
                                            )
                                        }
                                    }
                                }

                                composable(MochiRoutes.THEMES) {
                                    ThemeGalleryScreen(
                                        themes = appState.themes,
                                        activeTheme = activePalette,
                                        prefs = appState.prefs,
                                        onBack = { navController.popBackStack() },
                                        onSelectTheme = appViewModel::selectTheme,
                                        onEditTheme = { palette ->
                                            editingTheme = palette
                                            navController.navigate(MochiRoutes.THEME_EDITOR)
                                        },
                                        onDuplicateTheme = { palette ->
                                            appViewModel.duplicateTheme(palette) { copy ->
                                                editingTheme = copy
                                                navController.navigate(MochiRoutes.THEME_EDITOR)
                                            }
                                        },
                                        onDeleteTheme = appViewModel::deleteCustomTheme,
                                        onExportThemePack = { palette ->
                                            themePendingExport = palette
                                            val safeName = palette.name.lowercase().replace(" ", "_")
                                            exportThemeLauncher.launch("$safeName.mochitheme")
                                        },
                                        onImportThemePack = {
                                            importThemeLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                                        },
                                        onSetGlassIntensity = appViewModel::setGlassIntensity,
                                        onSetSolidChrome = appViewModel::setSolidChrome,
                                        onSetDynamicColor = appViewModel::setMaterialYouDynamic
                                    )
                                }

                                composable(MochiRoutes.THEME_EDITOR) {
                                    val target = editingTheme ?: activePalette
                                    ThemeEditorScreen(
                                        initialPalette = target,
                                        onBack = { navController.popBackStack() },
                                        onSave = { saved ->
                                            appViewModel.saveCustomTheme(saved, selectAfterSave = true)
                                            navController.popBackStack()
                                        }
                                    )
                                }

                                composable(MochiRoutes.PRIVACY) {
                                    PrivacyScreen(onBack = { navController.popBackStack() })
                                }

                                composable(MochiRoutes.CREDITS) {
                                    CreditsScreen(onBack = { navController.popBackStack() })
                                }

                                composable(
                                    route = MochiRoutes.READER,
                                    arguments = listOf(
                                        navArgument("bookId") { type = NavType.LongType },
                                        navArgument("morphKey") {
                                            type = NavType.StringType
                                            defaultValue = ""
                                        },
                                        navArgument("locator") {
                                            type = NavType.StringType
                                            defaultValue = ""
                                        }
                                    )
                                ) { entry ->
                                    val bookId = entry.arguments?.getLong("bookId") ?: -1L
                                    val morphKey = entry.arguments?.getString("morphKey").orEmpty()
                                    val rawLocator = entry.arguments?.getString("locator").orEmpty()
                                    val initialLocator = remember(rawLocator) {
                                        if (rawLocator.isNotBlank()) ReaderLocator.fromJson(rawLocator) else null
                                    }
                                    val readerViewModel: ReaderViewModel = viewModel()
                                    val readerState by readerViewModel.uiState.collectAsStateWithLifecycle()

                                    LaunchedEffect(bookId, rawLocator) {
                                        if (bookId > 0L) readerViewModel.openBook(bookId, initialLocator)
                                    }

                                    val effectivePalette = readerState.bookThemeOverrideId?.let { id ->
                                        appState.themes.find { it.id == id }
                                    } ?: activePalette
                                    val readerColorScheme = remember(effectivePalette) {
                                        effectivePalette.toMaterialColorScheme()
                                    }

                                    CompositionLocalProvider(
                                        LocalAnimatedVisibilityScope provides this,
                                        LocalMochiPalette provides effectivePalette
                                    ) {
                                        MaterialTheme(colorScheme = readerColorScheme) {
                                            ReaderScreen(
                                                state = readerState,
                                                settings = appState.prefs.readerSettings,
                                                themes = appState.themes,
                                                activeTheme = effectivePalette,
                                                warmLightEnabled = appState.prefs.warmLightEnabled,
                                                morphKey = morphKey,
                                                onBack = { navController.popBackStack() },
                                                onOpenBookById = { targetId ->
                                                    navController.navigate(MochiRoutes.readerRoute(targetId))
                                                },
                                                onFindWikilinkTarget = readerViewModel::findWikilinkBookId,
                                                onOpenBookInfo = { bookForInfoSheet = it },
                                                onToggleChrome = readerViewModel::toggleChrome,
                                                onUpdatePage = readerViewModel::updateCurrentPage,
                                                onUpdateLocator = readerViewModel::updateLocator,
                                                onUpdateScrollProgression = readerViewModel::updateScrollProgression,
                                                onJumpWithUndo = readerViewModel::jumpWithUndo,
                                                onUndoJump = readerViewModel::undoJump,
                                                onDismissUndo = readerViewModel::dismissUndoJump,
                                                onSearchQueryChange = readerViewModel::updateSearchQuery,
                                                onOpenFootnote = readerViewModel::openFootnote,
                                                onCloseFootnote = readerViewModel::closeFootnote,
                                                onOpenImageZoom = readerViewModel::openImageZoom,
                                                onCloseImageZoom = readerViewModel::closeImageZoom,
                                                onToggleAutoScroll = readerViewModel::toggleAutoScroll,
                                                onUpdateSettings = appViewModel::updateReaderSettings,
                                                onSelectGlobalTheme = appViewModel::selectTheme,
                                                onSetBookThemeOverride = readerViewModel::setBookThemeOverride,
                                                onToggleWarmLight = { on -> appViewModel.setWarmLight(on, null) },
                                                onImportFont = {
                                                    importFontLauncher.launch(
                                                        arrayOf(
                                                            "font/ttf",
                                                            "font/otf",
                                                            "application/x-font-ttf",
                                                            "application/x-font-otf",
                                                            "application/octet-stream",
                                                            "*/*"
                                                        )
                                                    )
                                                },
                                                onCreateHighlight = readerViewModel::createHighlight,
                                                onUpdateAnnotation = readerViewModel::updateAnnotation,
                                                onCreateOrUpdateBookmark = readerViewModel::createOrUpdateBookmark,
                                                onDeleteAnnotation = readerViewModel::deleteAnnotation,
                                                onSaveTemplate = readerViewModel::saveTemplate,
                                                onDeleteTemplate = readerViewModel::deleteTemplate,
                                                onSaveVaultFolderUri = { uri ->
                                                    appViewModel.updateReaderSettings { it.copy(vaultExportFolderUri = uri) }
                                                },
                                                onShowMessage = appViewModel::showMessage,
                                                onUpdateFixedPdfPage = readerViewModel::updateFixedPdfPage,
                                                onToggleStudioMode = readerViewModel::toggleStudioMode,
                                                onSelectStudioTool = readerViewModel::setStudioTool,
                                                onToggleStudioSnap = readerViewModel::toggleStudioSnap,
                                                onToggleStudioOrtho = readerViewModel::toggleStudioOrtho,
                                                onUpdateStudioStyle = readerViewModel::updateStudioActiveStyle,
                                                onSelectStudioLayer = readerViewModel::selectStudioLayer,
                                                onCreateStudioLayer = readerViewModel::createStudioLayer,
                                                onUpdateStudioLayer = readerViewModel::updateStudioLayer,
                                                onDeleteStudioLayer = readerViewModel::deleteStudioLayer,
                                                onCreateStudioMarkup = readerViewModel::createStudioMarkup,
                                                onUpdateStudioMarkup = readerViewModel::updateStudioMarkup,
                                                onSelectStudioMarkups = readerViewModel::selectStudioMarkups,
                                                onMoveSelectedStudioMarkups = readerViewModel::moveSelectedStudioMarkups,
                                                onAlignSelectedStudioMarkups = readerViewModel::alignSelectedStudioMarkups,
                                                onCopySelectedStudioMarkups = readerViewModel::copySelectedStudioMarkups,
                                                onPasteStudioMarkups = readerViewModel::pasteStudioMarkups,
                                                onDeleteSelectedStudioMarkups = readerViewModel::deleteSelectedStudioMarkups,
                                                onUndoStudio = readerViewModel::undoStudio,
                                                onRedoStudio = readerViewModel::redoStudio,
                                                onSavePageScale = readerViewModel::savePageScale,
                                                onImportStudioBundle = readerViewModel::importStudioBundle,
                                                onEnsurePdfReflowLoaded = readerViewModel::ensurePdfReflowLoaded
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = if (showBottomBar) 88.dp else 16.dp)
                )

                LiquidGlassBottomBar(
                    destinations = destinations,
                    currentRoute = currentRoute,
                    visible = showBottomBar,
                    onSelect = { targetRoute ->
                        if (targetRoute != currentRoute) {
                            navController.navigate(targetRoute) {
                                popUpTo(MochiRoutes.LIBRARY) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )

                val infoBook = bookForInfoSheet?.let { current ->
                    libraryState.allBooks.find { it.id == current.id } ?: current
                }
                if (infoBook != null) {
                    BookDetailSheet(
                        book = infoBook,
                        allBooks = libraryState.allBooks,
                        collections = libraryState.collections,
                        collectionRefs = libraryState.collectionRefs,
                        tags = libraryState.tags,
                        tagRefs = libraryState.tagRefs,
                        isDuplicate = infoBook.contentHash in libraryState.duplicateHashes,
                        onDismiss = { bookForInfoSheet = null },
                        onUpdateStatus = { status -> libraryViewModel.markBooksStatus(listOf(infoBook.id), status) },
                        onToggleCollection = { colId, add ->
                            if (add) libraryViewModel.addBooksToCollection(listOf(infoBook.id), colId)
                            else libraryViewModel.removeBookFromCollection(infoBook.id, colId)
                        },
                        onAddTag = { tag -> libraryViewModel.addTagToBook(infoBook.id, tag) },
                        onRemoveTag = { tagId -> libraryViewModel.removeTagFromBook(infoBook.id, tagId) },
                        onStackOnto = { targetId -> libraryViewModel.stackBookOnto(infoBook.id, targetId) },
                        onUnstack = { libraryViewModel.unstackBook(infoBook.id) },
                        onHideBook = {
                            libraryViewModel.hideBooks(listOf(infoBook.id), !infoBook.hidden)
                            bookForInfoSheet = null
                        },
                        onRemoveFromLibrary = {
                            libraryViewModel.removeBooksFromLibrary(listOf(infoBook.id))
                            bookForInfoSheet = null
                        },
                        onDeleteFileFromDisk = {
                            libraryViewModel.deleteBooksFromDisk(listOf(infoBook.id))
                        }
                    )
                }

                if (showReadingStatsSheet) {
                    ReadingStatsSheet(
                        insights = libraryState.readingInsights,
                        onDismiss = { showReadingStatsSheet = false }
                    )
                }

                if (showGutenbergSheet) {
                    GutenbergCatalogSheet(
                        state = gutenbergState,
                        onDismiss = { showGutenbergSheet = false },
                        onQueryChange = libraryViewModel::setGutenbergQuery,
                        onSearch = { q, t -> libraryViewModel.searchGutenberg(q, t) },
                        onSelectTopic = { t -> libraryViewModel.searchGutenberg(gutenbergState.query, t) },
                        onDownloadBook = { book -> libraryViewModel.downloadGutenbergBook(book) },
                        onOpenDownloadedBook = { bookId ->
                            showGutenbergSheet = false
                            navController.navigate(MochiRoutes.readerRoute(bookId))
                        },
                        onLoadMore = libraryViewModel::loadMoreGutenberg
                    )
                }
            }
        }
    }
}


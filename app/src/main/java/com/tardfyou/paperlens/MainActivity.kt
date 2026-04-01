package com.tardfyou.paperlens

import android.content.res.Configuration
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tardfyou.paperlens.core.designsystem.PaperLensTheme
import com.tardfyou.paperlens.core.file.DocumentImportManager
import com.tardfyou.paperlens.core.model.ContrastMode
import com.tardfyou.paperlens.core.model.DocumentRepository
import com.tardfyou.paperlens.core.model.DocumentStatus
import com.tardfyou.paperlens.core.model.DocumentSummary
import com.tardfyou.paperlens.core.model.DocumentType
import com.tardfyou.paperlens.core.model.ThemeMode
import com.tardfyou.paperlens.core.model.UserPreferencesRepository
import com.tardfyou.paperlens.core.ui.PLDocumentCard
import com.tardfyou.paperlens.core.ui.PLEmptyState
import com.tardfyou.paperlens.feature.camera.CameraRoute
import com.tardfyou.paperlens.feature.highlights.HighlightsRoute
import com.tardfyou.paperlens.feature.home.HomeRoute
import com.tardfyou.paperlens.feature.ocr.OcrRoute
import com.tardfyou.paperlens.feature.pdf.PdfRoute
import com.tardfyou.paperlens.feature.reader.ReaderRoute
import com.tardfyou.paperlens.feature.settings.SettingsRoute
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PaperLensRoot() }
    }
}

data class MainUiState(
    val themeMode: ThemeMode = ThemeMode.System,
    val contrastMode: ContrastMode = ContrastMode.Standard,
)

@HiltViewModel
class MainViewModel @Inject constructor(
    preferencesRepository: UserPreferencesRepository,
) : ViewModel() {
    val uiState: StateFlow<MainUiState> = preferencesRepository.preferences
        .map { MainUiState(themeMode = it.themeMode, contrastMode = it.contrastMode) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MainUiState(),
        )
}

data class DocumentsUiState(
    val documents: List<DocumentSummary> = emptyList(),
)

@HiltViewModel
class DocumentsViewModel @Inject constructor(
    private val documentRepository: DocumentRepository,
) : ViewModel() {
    val uiState: StateFlow<DocumentsUiState> = documentRepository.observeDocuments()
        .map(::DocumentsUiState)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DocumentsUiState(),
        )

    suspend fun handleImport(
        uri: Uri,
        mimeType: String?,
        importManager: DocumentImportManager,
        onImageImported: (String) -> Unit,
        onPdfImported: (Long) -> Unit,
    ) {
        if (mimeType == "application/pdf" || uri.toString().endsWith(".pdf", ignoreCase = true)) {
            importManager.persistPdfPermission(uri)
            val documentId = documentRepository.createPdfDocument(
                title = importManager.getDisplayName(uri),
                sourceUri = uri.toString(),
                pageCount = importManager.getPdfPageCount(uri.toString()),
            )
            onPdfImported(documentId)
        } else {
            val imported = importManager.importImage(uri)
            onImageImported(imported.file.absolutePath)
        }
    }
}

@Composable
private fun PaperLensRoot(
    mainViewModel: MainViewModel = hiltViewModel(),
    documentsViewModel: DocumentsViewModel = hiltViewModel(),
) {
    val mainUiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val documentsUiState by documentsViewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isExpandedLayout = configuration.screenWidthDp >= 840
    val showNavigationRail = isExpandedLayout || (
        configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
            configuration.screenWidthDp >= 600
        )
    val coroutineScope = rememberCoroutineScope()
    val importManager = remember(context) { DocumentImportManager(context) }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                documentsViewModel.handleImport(
                    uri = uri,
                    mimeType = context.contentResolver.getType(uri),
                    importManager = importManager,
                    onImageImported = { path -> navController.navigate("ocr/${Uri.encode(path)}") },
                    onPdfImported = { documentId -> navController.navigate("pdf/$documentId") },
                )
            }
        }
    }

    PaperLensTheme(
        themeMode = mainUiState.themeMode,
        contrastMode = mainUiState.contrastMode,
    ) {
        val topLevelDestinations = listOf(
            TopLevelDestination("home", "首页", Icons.Rounded.Home),
            TopLevelDestination("documents", "文档", Icons.AutoMirrored.Rounded.Article),
            TopLevelDestination("highlights", "重点", Icons.Rounded.Bookmarks),
            TopLevelDestination("settings", "设置", Icons.Rounded.Settings),
        )

        if (showNavigationRail) {
            Row(modifier = Modifier.fillMaxSize()) {
                TopLevelNavigationRail(
                    navController = navController,
                    destinations = topLevelDestinations,
                )
                PaperLensNavHost(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f),
                    navController = navController,
                    documentsUiState = documentsUiState,
                    importLauncher = { importLauncher.launch(arrayOf("application/pdf", "image/*")) },
                    preferDocumentsDualPane = isExpandedLayout,
                )
            }
        } else {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                bottomBar = {
                    TopLevelBottomBar(
                        navController = navController,
                        destinations = topLevelDestinations,
                    )
                },
            ) { innerPadding ->
                PaperLensNavHost(
                    modifier = Modifier.padding(innerPadding),
                    navController = navController,
                    documentsUiState = documentsUiState,
                    importLauncher = { importLauncher.launch(arrayOf("application/pdf", "image/*")) },
                    preferDocumentsDualPane = false,
                )
            }
        }
    }
}

@Composable
private fun TopLevelBottomBar(
    navController: androidx.navigation.NavHostController,
    destinations: List<TopLevelDestination>,
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    NavigationBar {
        destinations.forEach { destination ->
            NavigationBarItem(
                selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true,
                onClick = { navController.navigateToTopLevel(destination.route) },
                icon = { Icon(destination.icon, contentDescription = destination.label) },
                label = { Text(destination.label) },
            )
        }
    }
}

@Composable
private fun TopLevelNavigationRail(
    navController: androidx.navigation.NavHostController,
    destinations: List<TopLevelDestination>,
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    NavigationRail {
        destinations.forEach { destination ->
            NavigationRailItem(
                selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true,
                onClick = { navController.navigateToTopLevel(destination.route) },
                icon = { Icon(destination.icon, contentDescription = destination.label) },
                label = { Text(destination.label) },
            )
        }
    }
}

@Composable
private fun PaperLensNavHost(
    navController: androidx.navigation.NavHostController,
    documentsUiState: DocumentsUiState,
    importLauncher: () -> Unit,
    preferDocumentsDualPane: Boolean,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = "home",
        modifier = modifier,
    ) {
        composable("home") {
            HomeRoute(
                onCaptureClick = { navController.navigate("camera") },
                onImportClick = importLauncher,
                onDocumentClick = { documentId ->
                    val document = documentsUiState.documents.firstOrNull { it.id == documentId }
                    if (document?.type == DocumentType.Pdf) {
                        navController.navigate("pdf/$documentId")
                    } else {
                        navController.navigate("reader/$documentId")
                    }
                },
            )
        }
        composable("documents") {
            DocumentsScreen(
                documents = documentsUiState.documents,
                onImportClick = importLauncher,
                onDocumentClick = { document ->
                    if (document.type == DocumentType.Pdf) {
                        navController.navigate("pdf/${document.id}")
                    } else {
                        navController.navigate("reader/${document.id}")
                    }
                },
                preferDualPane = preferDocumentsDualPane,
            )
        }
        composable("highlights") { HighlightsRoute() }
        composable("settings") { SettingsRoute() }
        composable("camera") {
            CameraRoute(
                onBack = { navController.popBackStack() },
                onImageCaptured = { imagePath ->
                    navController.navigate("ocr/${Uri.encode(imagePath)}")
                },
            )
        }
        composable(
            route = "ocr/{imagePath}",
            arguments = listOf(navArgument("imagePath") { type = NavType.StringType }),
        ) {
            OcrRoute()
        }
        composable(
            route = "reader/{documentId}",
            arguments = listOf(navArgument("documentId") { type = NavType.LongType }),
        ) {
            ReaderRoute()
        }
        composable(
            route = "pdf/{documentId}",
            arguments = listOf(navArgument("documentId") { type = NavType.LongType }),
        ) {
            PdfRoute()
        }
    }
}

private data class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

@Composable
private fun DocumentsScreen(
    documents: List<DocumentSummary>,
    onImportClick: () -> Unit,
    onDocumentClick: (DocumentSummary) -> Unit,
    preferDualPane: Boolean,
) {
    if (documents.isEmpty()) {
        PLEmptyState(
            title = "还没有文档",
            description = "导入 PDF 或图片后，历史记录和阅读进度会显示在这里。",
            action = { Button(onClick = onImportClick) { Text("导入文档") } },
        )
        return
    }

    if (!preferDualPane) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(documents, key = { it.id }) { document ->
                PLDocumentCard(document = document, onClick = { onDocumentClick(document) })
            }
        }
        return
    }

    var selectedDocumentId by rememberSaveable { mutableStateOf<Long?>(null) }
    LaunchedEffect(documents) {
        if (documents.none { it.id == selectedDocumentId }) {
            selectedDocumentId = documents.firstOrNull()?.id
        }
    }
    val selectedDocument = documents.firstOrNull { it.id == selectedDocumentId } ?: documents.first()

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(0.9f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(documents, key = { it.id }) { document ->
                PLDocumentCard(
                    document = document,
                    selected = document.id == selectedDocument.id,
                    onClick = { selectedDocumentId = document.id },
                )
            }
        }
        DocumentDetailPane(
            document = selectedDocument,
            modifier = Modifier
                .weight(1.1f)
                .fillMaxHeight(),
            onOpen = onDocumentClick,
        )
    }
}

@Composable
private fun DocumentDetailPane(
    document: DocumentSummary,
    onOpen: (DocumentSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = document.title,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "${document.type.displayName()} / ${document.pageCountLabel()} / ${document.status.displayName()}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = document.previewText.ifBlank {
                    "宽屏模式下可以在左侧选择文档，在右侧查看摘要并继续阅读。"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = { onOpen(document) }) {
                Text(document.actionLabel())
            }
        }
    }
}

private fun androidx.navigation.NavHostController.navigateToTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

private fun DocumentType.displayName(): String = when (this) {
    DocumentType.Image -> "图片文档"
    DocumentType.Pdf -> "PDF 文档"
}

private fun DocumentStatus.displayName(): String = when (this) {
    DocumentStatus.Draft -> "草稿"
    DocumentStatus.Processing -> "处理中"
    DocumentStatus.Ready -> "可阅读"
    DocumentStatus.Failed -> "处理失败"
}

private fun DocumentSummary.pageCountLabel(): String = when (type) {
    DocumentType.Image -> "${pageCount.coerceAtLeast(1)} 张"
    DocumentType.Pdf -> "${pageCount.coerceAtLeast(1)} 页"
}

private fun DocumentSummary.actionLabel(): String = when (type) {
    DocumentType.Image -> "继续阅读"
    DocumentType.Pdf -> "打开 PDF"
}

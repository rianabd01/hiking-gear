package com.hikinggear

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikinggear.geo.ActiveHikeSession
import com.hikinggear.geo.GeoPdfInfo
import com.hikinggear.geo.GeoPdfParser
import com.hikinggear.geo.GpxDocument
import com.hikinggear.geo.GpxParser
import com.hikinggear.geo.LiveLocation
import com.hikinggear.geo.RecordingStatus
import com.hikinggear.geo.estimateSlippyTileCount
import com.hikinggear.geo.formatDurationHms
import com.hikinggear.platform.GeoPdfNativeViewer
import com.hikinggear.platform.GpxNativeMapViewer
import com.hikinggear.platform.ActivityShareModel
import com.hikinggear.platform.AppLanguage
import com.hikinggear.platform.LocalFileStore
import com.hikinggear.platform.MapMode
import com.hikinggear.platform.OfflineMapManager
import com.hikinggear.platform.OfflinePackState
import com.hikinggear.platform.downloadedModesFor
import com.hikinggear.platform.downloadingModesFor
import com.hikinggear.platform.offlinePackKey
import com.hikinggear.platform.SavedFileEntry
import com.hikinggear.platform.SavedFileKind
import com.hikinggear.platform.currentTimeMillis
import com.hikinggear.platform.rememberCompassHeading
import com.hikinggear.platform.rememberFilePicker
import com.hikinggear.platform.rememberLiveLocation
import com.hikinggear.platform.shareActivityRoute
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ArrowLeft
import com.composables.icons.tabler.outline.Check
import com.composables.icons.tabler.outline.Compass
import com.composables.icons.tabler.outline.CurrentLocation
import com.composables.icons.tabler.outline.DotsVertical
import com.composables.icons.tabler.outline.Download
import com.composables.icons.tabler.outline.Map as TablerMap
import com.composables.icons.tabler.outline.Pencil
import com.composables.icons.tabler.outline.PlayerPause
import com.composables.icons.tabler.outline.PlayerPlay
import com.composables.icons.tabler.outline.PlayerStop
import com.composables.icons.tabler.outline.Plus
import com.composables.icons.tabler.outline.Settings
import com.composables.icons.tabler.outline.Share
import com.composables.icons.tabler.outline.Stack2
import com.composables.icons.tabler.outline.Trash
import com.composables.icons.tabler.outline.Trekking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val STABILO_YELLOW = Color(0xFFD9FF00)
private val ON_STABILO = Color(0xFF11140A)
private val LIME_SAGE = Color(0xFFB7D93D)
private val AMBER_GOLD = Color(0xFFFFD166)

private val HikingColorScheme = darkColorScheme(
    primary = STABILO_YELLOW,
    onPrimary = ON_STABILO,
    primaryContainer = Color(0xFF2B3512),
    onPrimaryContainer = STABILO_YELLOW,
    secondary = LIME_SAGE,
    onSecondary = ON_STABILO,
    secondaryContainer = Color(0xFF252D16),
    onSecondaryContainer = STABILO_YELLOW,
    tertiary = AMBER_GOLD,
    onTertiary = ON_STABILO,
    background = Color(0xFF0C0E0B),
    onBackground = Color(0xFFF2F5EC),
    surface = Color(0xFF121510),
    onSurface = Color(0xFFF2F5EC),
    surfaceVariant = Color(0xFF1C2118),
    onSurfaceVariant = Color(0xFF9EA693),
    surfaceContainer = Color(0xFF171B14),
    surfaceContainerLow = Color(0xFF121510),
    surfaceContainerHigh = Color(0xFF1E231A),
    outline = Color(0xFF343B2D),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF290606),
)
private const val MAX_OFFLINE_TILES_GUARD = 6000L
private const val LIST_PAGE_SIZE = 20

private enum class AppTab(val icon: ImageVector) {
    MAP(Tabler.Outline.TablerMap),
    ACTIVITY(Tabler.Outline.Trekking),
    COMPASS(Tabler.Outline.Compass),
    ;

    fun label(strings: AppStrings): String = when (this) {
        MAP -> strings.tabMap
        ACTIVITY -> strings.tabActivity
        COMPASS -> strings.tabCompass
    }
}

private sealed interface ActiveViewer {
    val fileName: String
    val sourceFileId: String?

    data class Loading(
        override val fileName: String,
        override val sourceFileId: String?,
        val kind: SavedFileKind,
        val pickedBytes: ByteArray? = null,
    ) : ActiveViewer

    data object LiveMap : ActiveViewer {
        override val fileName: String = "Peta Pendakian"
        override val sourceFileId: String? = null
    }

    data class Gpx(
        override val fileName: String,
        override val sourceFileId: String?,
        val doc: GpxDocument,
    ) : ActiveViewer

    data class ActivityDetail(
        override val fileName: String,
        override val sourceFileId: String?,
        val doc: GpxDocument,
    ) : ActiveViewer

    data class GeoPdf(
        override val fileName: String,
        override val sourceFileId: String?,
        val bytes: ByteArray,
        val geoInfo: GeoPdfInfo,
    ) : ActiveViewer
}

private fun initialViewerForFileId(
    sourceFileId: String?,
    savedFiles: List<SavedFileEntry> = emptyList(),
): ActiveViewer {
    if (sourceFileId == null) return ActiveViewer.LiveMap
    val name = savedFiles.firstOrNull { it.id == sourceFileId }?.name ?: sourceFileId.substringAfter("__")
    val kind = when {
        sourceFileId.startsWith("pdf__") -> SavedFileKind.GEOPDF
        sourceFileId.startsWith("act__") -> SavedFileKind.ACTIVITY
        sourceFileId.startsWith("gpx__") -> SavedFileKind.GPX
        else -> return ActiveViewer.LiveMap
    }
    return ActiveViewer.Loading(fileName = name, sourceFileId = sourceFileId, kind = kind)
}

@Composable
fun App() {
    MaterialTheme(colorScheme = HikingColorScheme) {
        val initialSession = remember {
            LocalFileStore.readActiveSession()?.let(ActiveHikeSession::deserialize)
        }
        var appLanguage by remember {
            mutableStateOf(AppLanguage.fromCode(LocalFileStore.readLanguageCode()))
        }
        val strings = remember(appLanguage) { stringsFor(appLanguage) }
        var showSettings by remember { mutableStateOf(false) }
        var currentTab by remember { mutableStateOf(AppTab.MAP) }
        var mapMode by remember { mutableStateOf(MapMode.STANDARD) }
        var activeSession by remember { mutableStateOf(initialSession) }
        var savedFiles by remember { mutableStateOf(LocalFileStore.list()) }
        var activeViewer by remember {
            mutableStateOf<ActiveViewer?>(
                initialSession?.let { initialViewerForFileId(it.sourceFileId, savedFiles) },
            )
        }
        var lastGpxDoc by remember {
            mutableStateOf((activeViewer as? ActiveViewer.Gpx)?.doc)
        }
        var statusMessage by remember { mutableStateOf<String?>(null) }
        var showAddMenu by remember { mutableStateOf(false) }
        var nowMs by remember { mutableLongStateOf(currentTimeMillis()) }
        var pendingStopSession by remember { mutableStateOf<ActiveHikeSession?>(null) }

        val offlineStates by OfflineMapManager.states.collectAsState()

        LaunchedEffect(Unit) {
            OfflineMapManager.refresh()
        }

        LaunchedEffect(activeViewer) {
            val loading = activeViewer as? ActiveViewer.Loading ?: return@LaunchedEffect
            val (nextViewer, errMsg) = withContext(Dispatchers.Default) {
                val bytes = loading.pickedBytes
                    ?: loading.sourceFileId?.let(LocalFileStore::read)
                    ?: return@withContext null to strings.readError(loading.fileName)

                when (loading.kind) {
                    SavedFileKind.GPX, SavedFileKind.ACTIVITY -> {
                        val parsed = GpxParser.parse(bytes.decodeToString())
                        if (parsed.bounds == null) {
                            null to strings.noValidCoords(loading.fileName)
                        } else {
                            val storedId = if (loading.pickedBytes != null) {
                                LocalFileStore.save(loading.fileName, loading.kind, bytes)?.id
                            } else {
                                loading.sourceFileId
                            }
                            if (loading.kind == SavedFileKind.ACTIVITY) {
                                ActiveViewer.ActivityDetail(loading.fileName, storedId, parsed) to null
                            } else {
                                ActiveViewer.Gpx(loading.fileName, storedId, parsed) to null
                            }
                        }
                    }

                    SavedFileKind.GEOPDF -> {
                        val info = GeoPdfParser.parse(bytes)
                        val storedId = if (loading.pickedBytes != null) {
                            LocalFileStore.save(loading.fileName, SavedFileKind.GEOPDF, bytes)?.id
                        } else {
                            loading.sourceFileId
                        }
                        ActiveViewer.GeoPdf(loading.fileName, storedId, bytes, info) to null
                    }
                }
            }

            if (activeViewer !== loading) return@LaunchedEffect
            if (nextViewer != null) {
                if (nextViewer is ActiveViewer.Gpx) lastGpxDoc = nextViewer.doc
                if (loading.pickedBytes != null) savedFiles = LocalFileStore.list()
                statusMessage = null
                activeViewer = nextViewer
            } else {
                statusMessage = errMsg
                activeViewer = null
            }
        }

        val isRecording = activeSession?.status == RecordingStatus.RECORDING
        val isPaused = activeSession?.status == RecordingStatus.PAUSED
        val gpsEnabled = isRecording || (!isPaused && (activeViewer != null || currentTab == AppTab.COMPASS))
        val liveLocation by rememberLiveLocation(
            enabled = gpsEnabled,
            keepActiveInBackground = isRecording,
        )

        LaunchedEffect(isRecording) {
            while (isRecording) {
                nowMs = currentTimeMillis()
                delay(1000L)
            }
        }

        LaunchedEffect(liveLocation, isRecording) {
            val loc = liveLocation ?: return@LaunchedEffect
            val session = activeSession ?: return@LaunchedEffect
            if (session.status != RecordingStatus.RECORDING) return@LaunchedEffect
            val updated = session.appendLocation(loc, currentTimeMillis()) ?: return@LaunchedEffect
            activeSession = updated
            LocalFileStore.writeActiveSession(updated.serialize())
        }

        fun startHike() {
            if (activeSession != null) return
            val now = currentTimeMillis()
            val started = ActiveHikeSession.start(now, sourceFileId = activeViewer?.sourceFileId)
            activeSession = started
            nowMs = now
            statusMessage = null
            LocalFileStore.writeActiveSession(started.serialize())
        }

        fun pauseHike() {
            val session = activeSession ?: return
            val paused = session.pause(currentTimeMillis())
            activeSession = paused
            LocalFileStore.writeActiveSession(paused.serialize())
        }

        fun resumeHike() {
            val session = activeSession ?: return
            val now = currentTimeMillis()
            val resumed = session.resume(now)
            activeSession = resumed
            nowMs = now
            LocalFileStore.writeActiveSession(resumed.serialize())
        }

        fun stopHike() {
            val session = activeSession?.pause(currentTimeMillis()) ?: return
            if (session.pointCount > 0) {
                activeSession = session
                LocalFileStore.writeActiveSession(session.serialize())
                pendingStopSession = session
            } else {
                LocalFileStore.clearActiveSession()
                activeSession = null
                statusMessage = strings.sessionStoppedEmpty
            }
        }

        fun finalizeStopSave(session: ActiveHikeSession, title: String, description: String) {
            val finalTitle = title.trim().ifEmpty { strings.defaultActivityTitle }
            val gpxBytes = session.toGpxXml(trackName = finalTitle, description = description).encodeToByteArray()
            val saved = LocalFileStore.save(session.defaultFileName(), SavedFileKind.ACTIVITY, gpxBytes)
            if (saved != null) {
                LocalFileStore.rename(saved.id, finalTitle)
            }
            LocalFileStore.clearActiveSession()
            activeSession = null
            pendingStopSession = null
            savedFiles = LocalFileStore.list()
            statusMessage = strings.activitySaved(finalTitle)
            activeViewer = null
            currentTab = AppTab.ACTIVITY
        }

        fun renameEntry(id: String, newName: String) {
            val clean = newName.trim()
            if (clean.isEmpty()) return
            if (!LocalFileStore.rename(id, clean)) return
            savedFiles = LocalFileStore.list()
            val cur = activeViewer
            if (cur != null && cur.sourceFileId == id) {
                activeViewer = when (cur) {
                    is ActiveViewer.Gpx -> cur.copy(fileName = clean)
                    is ActiveViewer.ActivityDetail -> cur.copy(fileName = clean)
                    is ActiveViewer.GeoPdf -> cur.copy(fileName = clean)
                    is ActiveViewer.Loading -> cur.copy(fileName = clean)
                    ActiveViewer.LiveMap -> cur
                }
            }
        }

        fun openEntryAsync(entry: SavedFileEntry) {
            statusMessage = null
            activeViewer = ActiveViewer.Loading(
                fileName = entry.name,
                sourceFileId = entry.id,
                kind = entry.kind,
            )
        }

        val pickGpx = rememberFilePicker(listOf("gpx", "xml")) { picked ->
            if (picked != null) {
                statusMessage = null
                activeViewer = ActiveViewer.Loading(
                    fileName = picked.name,
                    sourceFileId = null,
                    kind = SavedFileKind.GPX,
                    pickedBytes = picked.bytes,
                )
            }
        }

        val pickPdf = rememberFilePicker(listOf("pdf")) { picked ->
            if (picked != null) {
                statusMessage = null
                activeViewer = ActiveViewer.Loading(
                    fileName = picked.name,
                    sourceFileId = null,
                    kind = SavedFileKind.GEOPDF,
                    pickedBytes = picked.bytes,
                )
            }
        }

        val mapEntries = remember(savedFiles) {
            savedFiles.filter { it.kind == SavedFileKind.GPX || it.kind == SavedFileKind.GEOPDF }
        }
        val activityEntries = remember(savedFiles) {
            savedFiles.filter { it.kind == SavedFileKind.ACTIVITY }
        }

        val viewer = activeViewer
        if (viewer is ActiveViewer.ActivityDetail) {
            ActivityDetailScreen(
                viewer = viewer,
                mapMode = mapMode,
                language = appLanguage,
                strings = strings,
                onBack = { activeViewer = null },
                onRenameEntry = ::renameEntry,
            )
        } else if (viewer != null) {
            FullScreenViewer(
                viewer = viewer,
                mapMode = mapMode,
                strings = strings,
                offlineStates = offlineStates,
                liveLocation = liveLocation,
                gpsEnabled = gpsEnabled,
                lastGpxDoc = lastGpxDoc,
                activeSession = activeSession,
                nowMs = nowMs,
                statusMessage = statusMessage,
                onBack = { activeViewer = null },
                onSelectMapMode = { mapMode = it },
                onDownloadOffline = { regionId, doc ->
                    val b = doc.bounds?.paddedMeters(1500.0) ?: return@FullScreenViewer
                    val estTiles = estimateSlippyTileCount(b, minZoom = 11, maxZoom = 15)
                    if (estTiles > MAX_OFFLINE_TILES_GUARD) {
                        statusMessage = strings.areaTooLarge(estTiles, MAX_OFFLINE_TILES_GUARD)
                        return@FullScreenViewer
                    }
                    statusMessage = null
                    OfflineMapManager.download(regionId, b, minZoom = 11.0, maxZoom = 15.0, mapMode = mapMode)
                },
                onCancelOffline = { regionId, mode -> OfflineMapManager.cancel(regionId, mode) },
                onRenameEntry = ::renameEntry,
                onStartHike = ::startHike,
                onPauseHike = ::pauseHike,
                onResumeHike = ::resumeHike,
                onStopHike = ::stopHike,
            )
        } else if (showSettings) {
            SettingsScreen(
                language = appLanguage,
                strings = strings,
                onSelectLanguage = { selected ->
                    appLanguage = selected
                    LocalFileStore.writeLanguageCode(selected.code)
                },
                onBack = { showSettings = false },
            )
        } else {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
                topBar = {
                    Surface(color = MaterialTheme.colorScheme.surface) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
                        ) {
                            val title = when (currentTab) {
                                AppTab.MAP -> strings.headerMap
                                AppTab.ACTIVITY -> strings.tabActivity
                                AppTab.COMPASS -> strings.tabCompass
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .padding(start = 16.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = title,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                                IconButton(onClick = { showSettings = true }) {
                                    Icon(
                                        Tabler.Outline.Settings,
                                        contentDescription = strings.settingsTitle,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                            }
                            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                },
                bottomBar = {
                    Surface(color = MaterialTheme.colorScheme.surface) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                                    .height(60.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AppTab.entries.forEach { tab ->
                                    val selected = currentTab == tab
                                    val tint =
                                        if (selected) STABILO_YELLOW else MaterialTheme.colorScheme.onSurfaceVariant
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                            ) {
                                                currentTab = tab
                                                statusMessage = null
                                            },
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                    ) {
                                        val tabLabel = tab.label(strings)
                                        Icon(
                                            tab.icon,
                                            contentDescription = tabLabel,
                                            tint = tint,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(Modifier.height(3.dp))
                                        Text(
                                            text = tabLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                            color = tint,
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                floatingActionButton = {
                    if (currentTab == AppTab.MAP) {
                        Box {
                            FloatingActionButton(onClick = { showAddMenu = true }) {
                                Icon(
                                    Tabler.Outline.Plus,
                                    contentDescription = strings.addDesc,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = showAddMenu,
                                onDismissRequest = { showAddMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(strings.addRecordNewMap) },
                                    onClick = {
                                        showAddMenu = false
                                        activeViewer = ActiveViewer.LiveMap
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(strings.addGpx) },
                                    onClick = {
                                        showAddMenu = false
                                        pickGpx()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(strings.addGeoPdf) },
                                    onClick = {
                                        showAddMenu = false
                                        pickPdf()
                                    },
                                )
                            }
                        }
                    }
                },
            ) { padding ->
                Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                    when (currentTab) {
                        AppTab.MAP, AppTab.ACTIVITY -> key(currentTab) {
                            val isMapTab = currentTab == AppTab.MAP
                            SavedListScreen(
                                entries = if (isMapTab) mapEntries else activityEntries,
                                offlineStates = offlineStates,
                                strings = strings,
                                statusMessage = statusMessage,
                                activeSession = activeSession,
                                nowMs = nowMs,
                                emptyMessage = if (isMapTab) {
                                    strings.emptyMapsMessage
                                } else {
                                    strings.emptyActivitiesMessage
                                },
                                onResumeActiveMap = {
                                    activeViewer = initialViewerForFileId(activeSession?.sourceFileId, savedFiles)
                                },
                                onOpenEntry = ::openEntryAsync,
                                onRenameEntry = ::renameEntry,
                                onDeleteEntry = { entry ->
                                    LocalFileStore.delete(entry.id)
                                    if (entry.kind == SavedFileKind.GPX || entry.kind == SavedFileKind.ACTIVITY) {
                                        OfflineMapManager.delete(entry.id)
                                    }
                                    savedFiles = LocalFileStore.list()
                                },
                            )
                        }

                        AppTab.COMPASS -> CompassScreen(
                            liveLocation = liveLocation,
                            strings = strings,
                        )
                    }
                }
            }
        }

        pendingStopSession?.let { sessionToSave ->
            SaveActivityDialog(
                strings = strings,
                onDismiss = { pendingStopSession = null },
                onSave = { title, desc -> finalizeStopSave(sessionToSave, title, desc) },
            )
        }
    }
}

@Composable
private fun SettingsScreen(
    language: AppLanguage,
    strings: AppStrings,
    onSelectLanguage: (AppLanguage) -> Unit,
    onBack: () -> Unit,
) {
    var showLanguageDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Tabler.Outline.ArrowLeft,
                                contentDescription = strings.backDesc,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Text(
                            text = strings.settingsTitle,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showLanguageDialog = true }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = strings.settingsLanguageSection,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = strings.settingsLanguageHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = language.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = STABILO_YELLOW,
                )
            }
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
        }
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(strings.settingsLanguageSection) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    AppLanguage.entries.forEachIndexed { index, option ->
                        val selected = option == language
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectLanguage(option)
                                    showLanguageDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = option.displayName,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) STABILO_YELLOW else MaterialTheme.colorScheme.onSurface,
                            )
                            if (selected) {
                                Icon(
                                    Tabler.Outline.Check,
                                    contentDescription = null,
                                    tint = STABILO_YELLOW,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        if (index < AppLanguage.entries.lastIndex) {
                            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(strings.cancelAction)
                }
            },
        )
    }
}

@Composable
private fun StravaRecorderOverlay(
    session: ActiveHikeSession?,
    nowMs: Long,
    statusMessage: String?,
    strings: AppStrings,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (statusMessage != null) {
            Surface(
                color = Color(0xDE131610),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = statusMessage,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        if (session == null) {
            Button(
                onClick = onStart,
                shape = CircleShape,
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = STABILO_YELLOW,
                    contentColor = ON_STABILO,
                ),
                contentPadding = PaddingValues(horizontal = 36.dp, vertical = 14.dp),
            ) {
                Icon(
                    Tabler.Outline.PlayerPlay,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = ON_STABILO
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = strings.startAction,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = ON_STABILO,
                )
            }
        } else {
            val isRec = session.status == RecordingStatus.RECORDING
            val km = (session.totalDistanceMeters / 100.0).roundToInt() / 10.0
            val gain = session.totalElevationGainMeters.roundToInt()
            val duration = formatDurationHms(session.elapsedMs(nowMs))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xEB131610),
                shape = RoundedCornerShape(24.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (!isRec) {
                        Text(
                            text = strings.recorderPausedBadge,
                            style = MaterialTheme.typography.labelSmall,
                            color = AMBER_GOLD,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(6.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        MetricItem(label = strings.recorderTime, value = duration)
                        MetricItem(label = strings.recorderDistance, value = "$km km")
                        MetricItem(label = strings.recorderElevation, value = "+$gain m")
                        MetricItem(label = strings.recorderPoints, value = "${session.pointCount}")
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (isRec) {
                            Button(
                                onClick = onPause,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(24.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = STABILO_YELLOW,
                                    contentColor = ON_STABILO,
                                ),
                                contentPadding = PaddingValues(vertical = 12.dp),
                            ) {
                                Icon(
                                    Tabler.Outline.PlayerPause,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = ON_STABILO
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(strings.pauseAction, fontWeight = FontWeight.Bold, color = ON_STABILO)
                            }
                        } else {
                            Button(
                                onClick = onResume,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(24.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = STABILO_YELLOW,
                                    contentColor = ON_STABILO,
                                ),
                                contentPadding = PaddingValues(vertical = 12.dp),
                            ) {
                                Icon(
                                    Tabler.Outline.PlayerPlay,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = ON_STABILO
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(strings.resumeAction, fontWeight = FontWeight.Bold, color = ON_STABILO)
                            }
                        }

                        OutlinedButton(
                            onClick = onStop,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(24.dp),
                            contentPadding = PaddingValues(vertical = 12.dp),
                        ) {
                            Icon(
                                Tabler.Outline.PlayerStop,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = Color(0xFFFF7A7A)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = strings.stopAction,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF7A7A),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF9EA693),
        )
    }
}

@Composable
private fun SavedListScreen(
    entries: List<SavedFileEntry>,
    offlineStates: Map<String, OfflinePackState>,
    strings: AppStrings,
    statusMessage: String?,
    activeSession: ActiveHikeSession?,
    nowMs: Long,
    emptyMessage: String,
    onResumeActiveMap: () -> Unit,
    onOpenEntry: (SavedFileEntry) -> Unit,
    onRenameEntry: (String, String) -> Unit,
    onDeleteEntry: (SavedFileEntry) -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<SavedFileEntry?>(null) }
    var pendingRename by remember { mutableStateOf<SavedFileEntry?>(null) }
    var visibleCount by remember { mutableIntStateOf(LIST_PAGE_SIZE) }
    val listState = rememberLazyListState()

    val pageItems = remember(entries, visibleCount) { entries.take(visibleCount) }
    val hasMore = pageItems.size < entries.size
    val shouldLoadMore by remember(listState, pageItems.size, entries.size) {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            pageItems.size < entries.size && lastVisible >= pageItems.size - 3
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            visibleCount = (visibleCount + LIST_PAGE_SIZE).coerceAtMost(entries.size)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (activeSession != null || statusMessage != null) {
            Spacer(Modifier.height(10.dp))
        }
        if (activeSession != null) {
            val isRec = activeSession.status == RecordingStatus.RECORDING
            val duration = formatDurationHms(activeSession.elapsedMs(nowMs))
            val km = (activeSession.totalDistanceMeters / 100.0).roundToInt() / 10.0
            Card(
                onClick = onResumeActiveMap,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isRec) Color(0xFF222A12) else Color(0xFF2B2412),
                ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isRec) strings.activeRecordingBanner else strings.activePausedBanner,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isRec) STABILO_YELLOW else AMBER_GOLD,
                        )
                        Text(
                            text = strings.tapToOpenMapHint(duration, km),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text("→", style = MaterialTheme.typography.titleLarge)
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        if (statusMessage != null) {
            Text(
                text = statusMessage,
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(6.dp))
        }

        if (entries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = emptyMessage,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 84.dp),
        ) {
            items(pageItems, key = { it.id }) { entry ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenEntry(entry) },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 4.dp)) {
                            Text(
                                text = entry.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val kindLabel = when (entry.kind) {
                                SavedFileKind.GPX -> "GPX"
                                SavedFileKind.GEOPDF -> "GeoPDF"
                                SavedFileKind.ACTIVITY -> strings.kindActivityLabel
                            }
                            val downloadedModes = downloadedModesFor(offlineStates, entry.id)
                            val isAnyDownloading = downloadingModesFor(offlineStates, entry.id).isNotEmpty()
                            val offSuffix = when {
                                isAnyDownloading -> strings.statusDownloadingSuffix
                                downloadedModes.isNotEmpty() ->
                                    strings.statusOfflineSuffix(downloadedModes.joinToString(", ") { strings.mapModeLabel(it) })
                                else -> ""
                            }
                            Text(
                                text = "$kindLabel$offSuffix",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (downloadedModes.isNotEmpty()) {
                                    LIME_SAGE
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        var cardMenuExpanded by remember(entry.id) { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { cardMenuExpanded = true }) {
                                Icon(
                                    Tabler.Outline.DotsVertical,
                                    contentDescription = strings.optionsDesc,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(19.dp),
                                )
                            }
                            DropdownMenu(
                                expanded = cardMenuExpanded,
                                onDismissRequest = { cardMenuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(strings.renameAction) },
                                    leadingIcon = {
                                        Icon(
                                            Tabler.Outline.Pencil,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    onClick = {
                                        cardMenuExpanded = false
                                        pendingRename = entry
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(strings.deleteAction, color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = {
                                        Icon(
                                            Tabler.Outline.Trash,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    },
                                    onClick = {
                                        cardMenuExpanded = false
                                        pendingDelete = entry
                                    },
                                )
                            }
                        }
                    }
                    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
                }
            }
            if (hasMore) {
                item(key = "pagination_loader") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
            }
        }
    }

    pendingRename?.let { target ->
        RenameTitleDialog(
            initialName = target.name,
            strings = strings,
            onDismiss = { pendingRename = null },
            onConfirm = { newName ->
                pendingRename = null
                onRenameEntry(target.id, newName)
            },
        )
    }

    pendingDelete?.let { target ->
        val hasOffline = downloadedModesFor(offlineStates, target.id).isNotEmpty()
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(strings.deleteDialogTitle) },
            text = {
                Text(
                    if (hasOffline) {
                        strings.deleteWithOfflineBody(target.name)
                    } else {
                        strings.deleteSingleBody(target.name)
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        onDeleteEntry(target)
                    },
                ) {
                    Text(strings.deleteAction, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(strings.cancelAction)
                }
            },
        )
    }
}

@Composable
private fun SaveActivityDialog(
    strings: AppStrings,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var title by remember(strings.defaultActivityTitle) { mutableStateOf(strings.defaultActivityTitle) }
    var description by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.saveActivityDialogTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    label = { Text(strings.activityNameLabel) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    minLines = 2,
                    maxLines = 4,
                    label = { Text(strings.descriptionOptionalLabel) },
                    placeholder = { Text(strings.activityDescPlaceholder) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(title, description) },
                enabled = title.isNotBlank(),
            ) {
                Text(strings.saveAction, color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancelAction)
            }
        },
    )
}

@Composable
private fun RenameTitleDialog(
    initialName: String,
    strings: AppStrings,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var draft by remember(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.renameDialogTitle) },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                label = { Text(strings.nameFieldLabel) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(draft) },
                enabled = draft.isNotBlank(),
            ) {
                Text(strings.saveAction, color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancelAction)
            }
        },
    )
}

@Composable
private fun ActivityDetailScreen(
    viewer: ActiveViewer.ActivityDetail,
    mapMode: MapMode,
    language: AppLanguage,
    strings: AppStrings,
    onBack: () -> Unit,
    onRenameEntry: (String, String) -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showShareModelDialog by remember { mutableStateOf(false) }
    var selectedShareModel by remember { mutableStateOf(ActivityShareModel.MAP_CARD) }
    var isSharing by remember { mutableStateOf(false) }
    val doc = viewer.doc
    val distText = if (doc.totalDistanceMeters >= 1000.0) {
        "${(doc.totalDistanceMeters / 100.0).roundToInt() / 10.0} km"
    } else {
        "${doc.totalDistanceMeters.roundToInt()} m"
    }
    val durationText = doc.durationMs?.let(::formatDurationHms) ?: "--:--:--"
    val gainText = "+${doc.totalElevationGainMeters.roundToInt()} m"
    val maxEleText = doc.maxElevationMeters?.let { "${it.roundToInt()} ${strings.elevUnit}" } ?: "-"

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Tabler.Outline.ArrowLeft,
                                contentDescription = strings.backDesc,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = strings.activityDetailTitle,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        IconButton(
                            enabled = !isSharing,
                            onClick = { showShareModelDialog = true },
                        ) {
                            if (isSharing) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    Tabler.Outline.Share,
                                    contentDescription = strings.shareToSocialDesc,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        val fileId = viewer.sourceFileId
                        if (fileId != null) {
                            Box {
                                IconButton(onClick = { showMenu = true }) {
                                    Icon(
                                        Tabler.Outline.DotsVertical,
                                        contentDescription = strings.optionsDesc,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(strings.renameAction) },
                                        leadingIcon = {
                                            Icon(
                                                Tabler.Outline.Pencil,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        },
                                        onClick = {
                                            showMenu = false
                                            showRenameDialog = true
                                        },
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Box(modifier = Modifier.fillMaxWidth().height(290.dp)) {
                GpxNativeMapViewer(
                    doc = doc,
                    liveLocation = null,
                    recordedSegments = emptyList(),
                    showUserLocation = false,
                    mapMode = mapMode,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = strings.activityBadge,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = STABILO_YELLOW,
                    )
                    Text(
                        text = viewer.fileName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    val desc = doc.description?.takeIf { it.isNotBlank() }
                    if (desc != null) {
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            ActivityStatCell(
                                label = strings.statDistance,
                                value = distText,
                                highlight = true,
                                modifier = Modifier.weight(1f)
                            )
                            ActivityStatCell(
                                label = strings.statDuration,
                                value = durationText,
                                highlight = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
                        Row(modifier = Modifier.fillMaxWidth()) {
                            ActivityStatCell(label = strings.statElevGain, value = gainText, modifier = Modifier.weight(1f))
                            ActivityStatCell(label = strings.statMaxElev, value = maxEleText, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }

    if (showShareModelDialog) {
        ActivityShareModelDialog(
            doc = doc,
            selectedModel = selectedShareModel,
            strings = strings,
            onSelectModel = { selectedShareModel = it },
            onDismiss = { showShareModelDialog = false },
            onExport = {
                showShareModelDialog = false
                isSharing = true
                shareActivityRoute(
                    title = viewer.fileName,
                    description = doc.description,
                    doc = doc,
                    distanceText = distText,
                    durationText = durationText,
                    elevationGainText = gainText,
                    maxElevationText = maxEleText,
                    mapMode = mapMode,
                    shareModel = selectedShareModel,
                    language = language,
                    onComplete = { isSharing = false },
                )
            },
        )
    }

    val fileId = viewer.sourceFileId
    if (showRenameDialog && fileId != null) {
        RenameTitleDialog(
            initialName = viewer.fileName,
            strings = strings,
            onDismiss = { showRenameDialog = false },
            onConfirm = { newName ->
                showRenameDialog = false
                onRenameEntry(fileId, newName)
            },
        )
    }
}

@Composable
private fun ActivityShareModelDialog(
    doc: GpxDocument,
    selectedModel: ActivityShareModel,
    strings: AppStrings,
    onSelectModel: (ActivityShareModel) -> Unit,
    onDismiss: () -> Unit,
    onExport: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.chooseShareModelTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ActivityShareModel.entries.chunked(2).forEach { rowModels ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        rowModels.forEach { model ->
                            val selected = model == selectedModel
                            Surface(
                                onClick = { onSelectModel(model) },
                                modifier = Modifier.weight(1f),
                                color = if (selected) Color(0xFF1F2614) else Color(0xFF171B14),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(
                                    width = if (selected) 1.5.dp else 1.dp,
                                    color = if (selected) STABILO_YELLOW else Color(0xFF2E3528),
                                ),
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    ShareModelThumbnail(
                                        model = model,
                                        doc = doc,
                                        modifier = Modifier.fillMaxWidth().height(84.dp),
                                    )
                                    Text(
                                        text = strings.shareModelLabel(model),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) STABILO_YELLOW else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                    )
                                    Text(
                                        text = strings.shareModelSubtitle(model),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onExport) {
                Text(strings.shareAction, color = STABILO_YELLOW, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancelAction)
            }
        },
    )
}

@Composable
private fun ShareModelThumbnail(
    model: ActivityShareModel,
    doc: GpxDocument,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        when (model) {
            ActivityShareModel.MAP_CARD -> {
                drawRect(Color(0xFF1E261C))
                drawRect(Color(0xFF10130E), topLeft = Offset(0f, h * 0.64f), size = Size(w, h * 0.36f))
            }

            ActivityShareModel.STORY_POSTER -> {
                val frameW = w * 0.56f
                val left = (w - frameW) / 2f
                drawRect(Color(0xFF0A0C09))
                drawRect(Color(0xFF222B1F), topLeft = Offset(left, 2f), size = Size(frameW, h - 4f))
                drawRect(Color(0x990E110C), topLeft = Offset(left, h * 0.7f), size = Size(frameW, h * 0.28f))
            }

            ActivityShareModel.ROUTE_MINIMAL -> {
                drawRect(Color(0xFF0C0E0B))
            }

            ActivityShareModel.TRANSPARENT_STICKER -> {
                val cell = 8.dp.toPx()
                val cols = (w / cell).toInt() + 1
                val rows = (h / cell).toInt() + 1
                for (r in 0 until rows) {
                    for (c in 0 until cols) {
                        val shade = if ((r + c) % 2 == 0) Color(0xFF1B1F19) else Color(0xFF242921)
                        drawRect(shade, topLeft = Offset(c * cell, r * cell), size = Size(cell, cell))
                    }
                }
            }
        }

        // Draw miniature route path from actual GPX bounds
        val b = doc.bounds
        val pts = doc.tracks.firstOrNull { it.points.size >= 2 }?.points
        val routeColor = if (model == ActivityShareModel.MAP_CARD || model == ActivityShareModel.STORY_POSTER) {
            Color(0xFFFF7A1A)
        } else {
            STABILO_YELLOW
        }
        val boxTop = if (model == ActivityShareModel.MAP_CARD) h * 0.1f else h * 0.14f
        val boxBot = if (model == ActivityShareModel.MAP_CARD) h * 0.54f else h * 0.66f
        val boxLeft = if (model == ActivityShareModel.STORY_POSTER) w * 0.28f else w * 0.16f
        val boxRight = w - boxLeft

        if (b != null && pts != null && pts.size >= 2) {
            val lonSpan = (b.maxLon - b.minLon).coerceAtLeast(1e-6)
            val latSpan = (b.maxLat - b.minLat).coerceAtLeast(1e-6)
            val step = (pts.size / 36).coerceAtLeast(1)
            val path = Path()
            pts.forEachIndexed { idx, p ->
                if (idx % step != 0 && idx != pts.lastIndex) return@forEachIndexed
                val nx = ((p.coord.lon - b.minLon) / lonSpan).toFloat()
                val ny = ((b.maxLat - p.coord.lat) / latSpan).toFloat()
                val x = boxLeft + nx * (boxRight - boxLeft)
                val y = boxTop + ny * (boxBot - boxTop)
                if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = routeColor,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        } else {
            val path = Path().apply {
                moveTo(boxLeft, boxBot)
                lineTo(w * 0.45f, h * 0.38f)
                lineTo(boxRight, boxTop)
            }
            drawPath(path, color = routeColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
        }

        // Miniature stat accent bars at bottom
        val barLeft = if (model == ActivityShareModel.STORY_POSTER) w * 0.27f else 10.dp.toPx()
        val barWidth = (w - barLeft * 2f) * 0.45f
        drawRect(STABILO_YELLOW, topLeft = Offset(barLeft, h * 0.78f), size = Size(barWidth, 3.dp.toPx()))
        drawRect(Color(0xFF9EA693), topLeft = Offset(barLeft, h * 0.85f), size = Size(barWidth * 1.4f, 2.dp.toPx()))
    }
}

@Composable
private fun ActivityStatCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = if (highlight) STABILO_YELLOW else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun FullScreenViewer(
    viewer: ActiveViewer,
    mapMode: MapMode,
    strings: AppStrings,
    offlineStates: Map<String, OfflinePackState>,
    liveLocation: LiveLocation?,
    gpsEnabled: Boolean,
    lastGpxDoc: GpxDocument?,
    activeSession: ActiveHikeSession?,
    nowMs: Long,
    statusMessage: String?,
    onBack: () -> Unit,
    onSelectMapMode: (MapMode) -> Unit,
    onDownloadOffline: (String, GpxDocument) -> Unit,
    onCancelOffline: (String, MapMode) -> Unit,
    onRenameEntry: (String, String) -> Unit,
    onStartHike: () -> Unit,
    onPauseHike: () -> Unit,
    onResumeHike: () -> Unit,
    onStopHike: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showMapModeOverlay by remember { mutableStateOf(false) }
    var recenterTrigger by remember { mutableIntStateOf(0) }
    var gpsNotice by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(gpsNotice) {
        if (gpsNotice != null) {
            delay(2000L)
            gpsNotice = null
        }
    }
    val gpxViewer = viewer as? ActiveViewer.Gpx
    val fileId = viewer.sourceFileId
    val regionId = gpxViewer?.sourceFileId
    val offState = regionId?.let { offlineStates[offlinePackKey(it, mapMode)] ?: OfflinePackState.NotDownloaded }
    val downloadedModes = remember(offlineStates, regionId) {
        regionId?.let { downloadedModesFor(offlineStates, it).toSet() } ?: emptySet()
    }
    val downloadingModes = remember(offlineStates, regionId) {
        regionId?.let { downloadingModesFor(offlineStates, it).toSet() } ?: emptySet()
    }
    val coordText = liveLocation?.let {
        "${formatCoord(it.coord.lat)}, ${formatCoord(it.coord.lon)}"
    } ?: "GPS..."

    Box(modifier = Modifier.fillMaxSize()) {
        when (viewer) {
            is ActiveViewer.Loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = strings.loadingFile(viewer.fileName),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            is ActiveViewer.LiveMap -> GpxNativeMapViewer(
                doc = null,
                liveLocation = liveLocation,
                recordedSegments = activeSession?.coordSegments.orEmpty(),
                showUserLocation = gpsEnabled,
                mapMode = mapMode,
                recenterTrigger = recenterTrigger,
                modifier = Modifier.fillMaxSize(),
            )

            is ActiveViewer.Gpx -> GpxNativeMapViewer(
                doc = viewer.doc,
                liveLocation = liveLocation,
                recordedSegments = activeSession?.coordSegments.orEmpty(),
                showUserLocation = gpsEnabled,
                mapMode = mapMode,
                recenterTrigger = recenterTrigger,
                modifier = Modifier.fillMaxSize(),
            )

            is ActiveViewer.ActivityDetail -> Unit
            is ActiveViewer.GeoPdf -> GeoPdfNativeViewer(
                pdfBytes = viewer.bytes,
                geoInfo = viewer.geoInfo,
                liveLocation = liveLocation,
                gpxOverlay = lastGpxDoc,
                recordedSegments = activeSession?.coordSegments.orEmpty(),
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Translucent floating header: <- | Title | Lat, Lon | ⋮
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            color = Color(0xB811140F),
            shape = RoundedCornerShape(18.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Tabler.Outline.ArrowLeft,
                            contentDescription = strings.backDesc,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = if (viewer is ActiveViewer.LiveMap) strings.liveMapTitle else viewer.fileName,
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = coordText,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFE0E0E0),
                        maxLines = 1,
                        modifier = Modifier.padding(end = if (fileId != null) 0.dp else 12.dp),
                    )
                    if (fileId != null) {
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(
                                    Tabler.Outline.DotsVertical,
                                    contentDescription = strings.menuDesc,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(strings.renameAction) },
                                    leadingIcon = {
                                        Icon(
                                            Tabler.Outline.Pencil,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        showRenameDialog = true
                                    },
                                )
                                if (gpxViewer != null && regionId != null) {
                                    val modeLabel = strings.mapModeLabel(mapMode)
                                    when (offState) {
                                        is OfflinePackState.Downloaded -> {
                                            DropdownMenuItem(
                                                text = { Text(strings.offlineReady(modeLabel)) },
                                                leadingIcon = {
                                                    Icon(
                                                        Tabler.Outline.Check,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                },
                                                enabled = false,
                                                onClick = {},
                                            )
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        strings.deleteOfflineMode(modeLabel),
                                                        color = MaterialTheme.colorScheme.error
                                                    )
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        Tabler.Outline.Trash,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(18.dp),
                                                    )
                                                },
                                                onClick = {
                                                    showMenu = false
                                                    onCancelOffline(regionId, mapMode)
                                                },
                                            )
                                        }

                                        is OfflinePackState.Downloading -> {
                                            val expected = offState.expectedResources.coerceAtLeast(1L)
                                            val pct = ((offState.completedResources.toFloat() / expected.toFloat())
                                                .coerceIn(0f, 1f) * 100).roundToInt()
                                            DropdownMenuItem(
                                                text = { Text(strings.cancelDownloadMode(modeLabel, pct)) },
                                                leadingIcon = {
                                                    Icon(
                                                        Tabler.Outline.Trash,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                },
                                                onClick = {
                                                    showMenu = false
                                                    onCancelOffline(regionId, mapMode)
                                                },
                                            )
                                        }

                                        else -> DropdownMenuItem(
                                            text = { Text(strings.downloadMapMode(modeLabel)) },
                                            leadingIcon = {
                                                Icon(
                                                    Tabler.Outline.Download,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            },
                                            onClick = {
                                                showMenu = false
                                                onDownloadOffline(regionId, gpxViewer.doc)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                val description = gpxViewer?.doc?.description?.takeIf { it.isNotBlank() }
                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFD0D5C8),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                    )
                }
                if (offState is OfflinePackState.Downloading) {
                    val expected = offState.expectedResources.coerceAtLeast(1L)
                    val progress = (offState.completedResources.toFloat() / expected.toFloat()).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                    )
                }
            }
        }

        if (viewer is ActiveViewer.LiveMap || viewer is ActiveViewer.Gpx) {
            MapOverlayControls(
                selectedMode = mapMode,
                downloadedModes = downloadedModes,
                downloadingModes = downloadingModes,
                showModeOverlay = showMapModeOverlay,
                hasGpsFix = liveLocation != null,
                strings = strings,
                onToggleModeOverlay = { showMapModeOverlay = !showMapModeOverlay },
                onSelectMode = {
                    onSelectMapMode(it)
                    showMapModeOverlay = false
                },
                onTargetGps = {
                    showMapModeOverlay = false
                    recenterTrigger++
                    if (liveLocation == null) {
                        gpsNotice = strings.searchingGpsNotice
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 12.dp, bottom = if (activeSession != null) 186.dp else 88.dp),
            )
        }

        if (viewer !is ActiveViewer.Loading) {
            StravaRecorderOverlay(
                session = activeSession,
                nowMs = nowMs,
                statusMessage = gpsNotice ?: statusMessage,
                strings = strings,
                onStart = onStartHike,
                onPause = onPauseHike,
                onResume = onResumeHike,
                onStop = onStopHike,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding(),
            )
        }

        if (showRenameDialog && fileId != null) {
            RenameTitleDialog(
                initialName = viewer.fileName,
                strings = strings,
                onDismiss = { showRenameDialog = false },
                onConfirm = { newName ->
                    showRenameDialog = false
                    onRenameEntry(fileId, newName)
                },
            )
        }
    }
}

@Composable
private fun MapOverlayControls(
    selectedMode: MapMode,
    downloadedModes: Set<MapMode>,
    downloadingModes: Set<MapMode>,
    showModeOverlay: Boolean,
    hasGpsFix: Boolean,
    strings: AppStrings,
    onToggleModeOverlay: () -> Unit,
    onSelectMode: (MapMode) -> Unit,
    onTargetGps: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showModeOverlay) {
            Surface(
                color = Color(0xE011140F),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF2E3528)),
            ) {
                Column(modifier = Modifier.width(188.dp).padding(vertical = 6.dp)) {
                    MapMode.entries.forEach { mode ->
                        val active = mode == selectedMode
                        val isOffline = mode in downloadedModes
                        val isDownloading = mode in downloadingModes
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectMode(mode) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = strings.mapModeLabel(mode),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                color = if (active) STABILO_YELLOW else Color.White,
                            )
                            if (isDownloading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(13.dp),
                                    strokeWidth = 1.8.dp,
                                    color = STABILO_YELLOW,
                                )
                            } else if (isOffline) {
                                Text(
                                    text = strings.offlineBadge,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = LIME_SAGE,
                                )
                            }
                            if (active) {
                                Icon(
                                    Tabler.Outline.Check,
                                    contentDescription = null,
                                    tint = STABILO_YELLOW,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(
                onClick = onToggleModeOverlay,
                color = Color(0xCC11140F),
                shape = CircleShape,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (showModeOverlay || selectedMode != MapMode.STANDARD) STABILO_YELLOW else Color(
                        0xFF2E3528
                    ),
                ),
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Tabler.Outline.Stack2,
                        contentDescription = strings.mapModeButtonDesc,
                        tint = if (showModeOverlay || selectedMode != MapMode.STANDARD) STABILO_YELLOW else Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Surface(
                onClick = onTargetGps,
                color = Color(0xCC11140F),
                shape = CircleShape,
                border = BorderStroke(1.dp, Color(0xFF2E3528)),
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Tabler.Outline.CurrentLocation,
                        contentDescription = strings.recenterGpsDesc,
                        tint = if (hasGpsFix) STABILO_YELLOW else Color(0xFFD0D5C8),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CompassScreen(
    liveLocation: LiveLocation?,
    strings: AppStrings,
) {
    val heading by rememberCompassHeading()
    val degrees = heading?.degrees ?: 0.0
    val degInt = ((degrees.roundToInt() % 360) + 360) % 360

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (heading != null) "$degInt°" else "--°",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (heading != null) cardinalDirection(degInt, strings) else strings.compassWaiting,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        CompassDial(
            headingDegrees = degrees.toFloat(),
            hasReading = heading != null,
            cardinals = strings.compassDialCardinals,
            modifier = Modifier.size(270.dp),
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val h = heading
                if (h == null) {
                    Text(
                        text = strings.compassNoSensor,
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    val northType = if (h.isTrueNorth) strings.compassTrueNorth else strings.compassMagneticNorth
                    val accText =
                        if (h.accuracyDegrees >= 0) "±${h.accuracyDegrees.roundToInt()}°" else strings.compassNeedsCal
                    Text(strings.compassRefAcc(northType, accText), style = MaterialTheme.typography.bodySmall)
                    if (h.accuracyDegrees < 0 || h.accuracyDegrees > 25) {
                        Text(
                            text = strings.compassCalHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = AMBER_GOLD,
                        )
                    }
                }
                val locText = if (liveLocation == null) {
                    strings.compassGpsSearching
                } else {
                    "GPS: ${formatCoord(liveLocation.coord.lat)}, ${formatCoord(liveLocation.coord.lon)}" +
                            (liveLocation.altitudeMeters?.let { "  •  ${it.roundToInt()} ${strings.elevUnit}" } ?: "")
                }
                Text(
                    text = locText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CompassDial(
    headingDegrees: Float,
    hasReading: Boolean,
    cardinals: List<Pair<Int, String>>,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        drawCircle(
            color = Color(0xFF2E3528),
            radius = radius,
            center = center,
            style = Stroke(width = 3.dp.toPx()),
        )
        drawLine(
            color = Color(0xFFFF5252),
            start = Offset(center.x, center.y - radius - 8.dp.toPx()),
            end = Offset(center.x, center.y - radius + 16.dp.toPx()),
            strokeWidth = 3.5.dp.toPx(),
            cap = StrokeCap.Round,
        )

        rotate(degrees = if (hasReading) -headingDegrees else 0f, pivot = center) {
            for (deg in 0 until 360 step 5) {
                val isMajor = deg % 30 == 0
                val isMedium = deg % 15 == 0
                val tickLen = when {
                    isMajor -> 14.dp.toPx()
                    isMedium -> 9.dp.toPx()
                    else -> 5.dp.toPx()
                }
                val rad = (deg - 90) * PI / 180.0
                val outer = radius - 6.dp.toPx()
                val inner = outer - tickLen
                val tickColor =
                    if (deg == 0) Color(0xFFFF5252) else if (isMajor) Color(0xFFF2F5EC) else Color(0xFF737D69)
                drawLine(
                    color = tickColor,
                    start = Offset(center.x + (outer * cos(rad)).toFloat(), center.y + (outer * sin(rad)).toFloat()),
                    end = Offset(center.x + (inner * cos(rad)).toFloat(), center.y + (inner * sin(rad)).toFloat()),
                    strokeWidth = if (isMajor) 2.5.dp.toPx() else 1.2.dp.toPx(),
                )
            }

            val tipY = center.y - radius + 22.dp.toPx()
            val northPath = Path().apply {
                moveTo(center.x, tipY)
                lineTo(center.x - 6.dp.toPx(), tipY + 10.dp.toPx())
                lineTo(center.x + 6.dp.toPx(), tipY + 10.dp.toPx())
                close()
            }
            drawPath(northPath, color = Color(0xFFFF5252))

            val labelRadius = radius - 48.dp.toPx()
            cardinals.forEach { (deg, label) ->
                val rad = (deg - 90) * PI / 180.0
                val color = if (deg == 0) Color(0xFFFF5252) else Color.White
                val measured = textMeasurer.measure(
                    text = label,
                    style = TextStyle(color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold),
                )
                val x = center.x + (labelRadius * cos(rad)).toFloat() - measured.size.width / 2f
                val y = center.y + (labelRadius * sin(rad)).toFloat() - measured.size.height / 2f
                drawText(measured, topLeft = Offset(x, y))
            }
        }

        val cross = 12.dp.toPx()
        drawLine(
            Color(0xFF8E9683),
            Offset(center.x - cross, center.y),
            Offset(center.x + cross, center.y),
            1.5.dp.toPx()
        )
        drawLine(
            Color(0xFF8E9683),
            Offset(center.x, center.y - cross),
            Offset(center.x, center.y + cross),
            1.5.dp.toPx()
        )
    }
}

private fun cardinalDirection(deg: Int, strings: AppStrings): String =
    strings.compassDirs8[((deg + 22) % 360) / 45]

private fun formatCoord(v: Double): String {
    val scaled = (v * 10000.0).roundToInt() / 10000.0
    return scaled.toString()
}

package com.hikinggear.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import com.hikinggear.geo.Bounds
import com.hikinggear.geo.GeoPdfInfo
import com.hikinggear.geo.GpxDocument
import com.hikinggear.geo.LatLon
import com.hikinggear.geo.LiveLocation
import kotlinx.coroutines.flow.StateFlow

class PickedFile(
    val name: String,
    val bytes: ByteArray,
)

enum class AppLanguage(val code: String, val displayName: String) {
    INDONESIAN("id", "Bahasa Indonesia"),
    ENGLISH("en", "English"),
    ;

    companion object {
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code == code?.trim()?.lowercase() } ?: INDONESIAN
    }
}

enum class ActivityShareModel(val id: String, val label: String, val subtitle: String) {
    MAP_CARD("map_card", "Kartu Peta", "4:5 • Peta & metrik"),
    STORY_POSTER("story_poster", "Poster Story", "9:16 • Peta penuh"),
    ROUTE_MINIMAL("route_minimal", "Siluet Rute", "4:5 • Tanpa peta"),
    TRANSPARENT_STICKER("transparent", "Transparan", "PNG • Tempel foto"),
}

enum class MapMode(val id: String, val label: String) {
    STANDARD("standard", "Standar"),
    TOPO("topo", "Topografi"),
    SATELLITE("satellite", "Satelit"),
    DARK("dark", "Gelap"),
}

enum class SavedFileKind {
    GPX,
    GEOPDF,
    ACTIVITY,
}

data class SavedFileEntry(
    val id: String,
    val name: String,
    val kind: SavedFileKind,
    val sizeBytes: Long,
    val savedAtEpochMs: Long,
)

data class CompassHeading(
    val degrees: Double,
    val isTrueNorth: Boolean,
    val accuracyDegrees: Double,
)

sealed interface OfflinePackState {
    data object NotDownloaded : OfflinePackState
    data class Downloading(
        val completedResources: Long,
        val expectedResources: Long,
        val bytesCompleted: Long,
    ) : OfflinePackState

    data class Downloaded(val bytes: Long) : OfflinePackState
    data class Error(val message: String) : OfflinePackState
}

expect fun currentTimeMillis(): Long

expect object LocalFileStore {
    fun list(): List<SavedFileEntry>
    fun save(name: String, kind: SavedFileKind, bytes: ByteArray): SavedFileEntry?
    fun read(id: String): ByteArray?
    fun rename(id: String, newName: String): Boolean
    fun delete(id: String): Boolean
    fun readActiveSession(): String?
    fun writeActiveSession(content: String): Boolean
    fun clearActiveSession(): Boolean
    fun readLanguageCode(): String?
    fun writeLanguageCode(code: String): Boolean
}

expect object OfflineMapManager {
    val states: StateFlow<Map<String, OfflinePackState>>
    fun refresh()
    fun download(
        regionId: String,
        bounds: Bounds,
        minZoom: Double = 11.0,
        maxZoom: Double = 15.0,
        mapMode: MapMode = MapMode.STANDARD
    )

    fun cancel(regionId: String, mapMode: MapMode = MapMode.STANDARD)
    fun delete(regionId: String)
}

fun offlinePackKey(regionId: String, mapMode: MapMode): String =
    if (mapMode == MapMode.STANDARD) regionId else "$regionId::${mapMode.id}"

fun downloadedModesFor(states: Map<String, OfflinePackState>, regionId: String): List<MapMode> =
    MapMode.entries.filter { states[offlinePackKey(regionId, it)] is OfflinePackState.Downloaded }

fun downloadingModesFor(states: Map<String, OfflinePackState>, regionId: String): List<MapMode> =
    MapMode.entries.filter { states[offlinePackKey(regionId, it)] is OfflinePackState.Downloading }

@Composable
expect fun rememberFilePicker(
    allowedExtensions: List<String>,
    onResult: (PickedFile?) -> Unit,
): () -> Unit

@Composable
expect fun rememberLiveLocation(
    enabled: Boolean = true,
    keepActiveInBackground: Boolean = false,
): State<LiveLocation?>

@Composable
expect fun rememberCompassHeading(): State<CompassHeading?>

expect fun shareActivityRoute(
    title: String,
    description: String?,
    doc: GpxDocument,
    distanceText: String,
    durationText: String,
    elevationGainText: String,
    maxElevationText: String,
    mapMode: MapMode = MapMode.STANDARD,
    shareModel: ActivityShareModel = ActivityShareModel.MAP_CARD,
    language: AppLanguage = AppLanguage.INDONESIAN,
    onComplete: () -> Unit,
)

@Composable
expect fun GpxNativeMapViewer(
    doc: GpxDocument?,
    liveLocation: LiveLocation?,
    recordedSegments: List<List<LatLon>> = emptyList(),
    showUserLocation: Boolean = true,
    mapMode: MapMode = MapMode.STANDARD,
    recenterTrigger: Int = 0,
    modifier: Modifier = Modifier,
)

@Composable
expect fun GeoPdfNativeViewer(
    pdfBytes: ByteArray,
    geoInfo: GeoPdfInfo,
    liveLocation: LiveLocation?,
    gpxOverlay: GpxDocument?,
    recordedSegments: List<List<LatLon>> = emptyList(),
    modifier: Modifier = Modifier,
)

internal fun parseTitleMap(raw: String?): MutableMap<String, String> {
    val result = mutableMapOf<String, String>()
    raw?.lineSequence()?.forEach { line ->
        val sep = line.indexOf('\t')
        if (sep > 0) {
            val id = line.substring(0, sep)
            val title = line.substring(sep + 1).trim()
            if (id.isNotBlank() && title.isNotBlank()) result[id] = title
        }
    }
    return result
}

internal fun serializeTitleMap(map: Map<String, String>): String =
    map.entries.joinToString("\n") { (id, title) ->
        val clean = title.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim()
        "$id\t$clean"
    }

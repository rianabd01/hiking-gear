package com.hikinggear.platform

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.hikinggear.geo.Bounds
import com.hikinggear.geo.GeoPdfInfo
import com.hikinggear.geo.GpxDocument
import com.hikinggear.geo.LatLon
import com.hikinggear.geo.LiveLocation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual object LocalFileStore {
    // ponytail: file-backed store; migrate metadata to SQLite if search/tags/sync are added later.
    private val dir: File
        get() = File(System.getProperty("user.home"), ".hiking-gear/library").apply { mkdirs() }

    private val activeSessionFile: File
        get() = File(dir, "active_hike_session.txt")

    private val titlesFile: File
        get() = File(dir, "library_titles.txt")

    private val languageFile: File
        get() = File(dir, "app_language.txt")

    private fun readTitles(): MutableMap<String, String> =
        parseTitleMap(if (titlesFile.isFile) runCatching { titlesFile.readText() }.getOrNull() else null)

    private fun writeTitles(titles: Map<String, String>): Boolean =
        runCatching { titlesFile.writeText(serializeTitleMap(titles)); true }.getOrDefault(false)

    actual fun list(): List<SavedFileEntry> {
        val titles = readTitles()
        return dir.listFiles()
            ?.asSequence()
            ?.filter { it.isFile }
            ?.mapNotNull { file ->
                val id = file.name
                val kind = kindFromId(id) ?: return@mapNotNull null
                SavedFileEntry(
                    id = id,
                    name = titles[id] ?: id.substringAfter("__"),
                    kind = kind,
                    sizeBytes = file.length(),
                    savedAtEpochMs = file.lastModified(),
                )
            }
            ?.sortedByDescending { it.savedAtEpochMs }
            ?.toList()
            .orEmpty()
    }

    actual fun save(name: String, kind: SavedFileKind, bytes: ByteArray): SavedFileEntry? {
        val id = buildStoredId(name, kind)
        val target = File(dir, id)
        return runCatching {
            target.writeBytes(bytes)
            SavedFileEntry(
                id = id,
                name = id.substringAfter("__"),
                kind = kind,
                sizeBytes = bytes.size.toLong(),
                savedAtEpochMs = target.lastModified(),
            )
        }.getOrNull()
    }

    actual fun read(id: String): ByteArray? {
        if (!isSafeId(id)) return null
        val target = File(dir, id)
        return if (target.isFile) runCatching { target.readBytes() }.getOrNull() else null
    }

    actual fun rename(id: String, newName: String): Boolean {
        if (!isSafeId(id)) return false
        val clean = newName.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim()
        if (clean.isEmpty()) return false
        if (!File(dir, id).isFile) return false
        val titles = readTitles()
        titles[id] = clean
        return writeTitles(titles)
    }

    actual fun delete(id: String): Boolean {
        if (!isSafeId(id)) return false
        val target = File(dir, id)
        val removed = target.isFile && target.delete()
        if (removed) {
            val titles = readTitles()
            if (titles.remove(id) != null) {
                writeTitles(titles)
            }
        }
        return removed
    }

    actual fun readActiveSession(): String? =
        if (activeSessionFile.isFile) runCatching { activeSessionFile.readText() }.getOrNull() else null

    actual fun writeActiveSession(content: String): Boolean =
        runCatching { activeSessionFile.writeText(content); true }.getOrDefault(false)

    actual fun clearActiveSession(): Boolean =
        !activeSessionFile.exists() || activeSessionFile.delete()

    actual fun readLanguageCode(): String? =
        if (languageFile.isFile) runCatching { languageFile.readText().trim() }.getOrNull() else null

    actual fun writeLanguageCode(code: String): Boolean =
        runCatching { languageFile.writeText(code.trim()); true }.getOrDefault(false)
}

actual object OfflineMapManager {
    private val _states = MutableStateFlow<Map<String, OfflinePackState>>(emptyMap())
    actual val states: StateFlow<Map<String, OfflinePackState>> = _states

    actual fun refresh() {}

    actual fun download(regionId: String, bounds: Bounds, minZoom: Double, maxZoom: Double, mapMode: MapMode) {}

    actual fun cancel(regionId: String, mapMode: MapMode) {
        _states.value = _states.value - offlinePackKey(regionId, mapMode)
    }

    actual fun delete(regionId: String) {
        val prefix = "$regionId::"
        _states.value = _states.value.filterKeys { it != regionId && !it.startsWith(prefix) }
    }
}

private fun buildStoredId(name: String, kind: SavedFileKind): String {
    val clean = name.substringAfterLast('/').substringAfterLast('\\')
        .replace("..", "_")
        .ifBlank { "file" }
    val prefix = when (kind) {
        SavedFileKind.GPX -> "gpx__"
        SavedFileKind.GEOPDF -> "pdf__"
        SavedFileKind.ACTIVITY -> "act__"
    }
    return prefix + clean
}

private fun kindFromId(id: String): SavedFileKind? = when {
    id.startsWith("gpx__") -> SavedFileKind.GPX
    id.startsWith("pdf__") -> SavedFileKind.GEOPDF
    id.startsWith("act__") -> SavedFileKind.ACTIVITY
    else -> null
}

private fun isSafeId(id: String): Boolean =
    kindFromId(id) != null && !id.contains('/') && !id.contains('\\') && !id.contains("..")

@Composable
actual fun rememberFilePicker(
    allowedExtensions: List<String>,
    onResult: (PickedFile?) -> Unit,
): () -> Unit = remember { { onResult(null) } }

@Composable
actual fun rememberLiveLocation(
    enabled: Boolean,
    keepActiveInBackground: Boolean,
): State<LiveLocation?> = remember { mutableStateOf(null) }

@Composable
actual fun rememberCompassHeading(): State<CompassHeading?> = remember { mutableStateOf(null) }

actual fun shareActivityRoute(
    title: String,
    description: String?,
    doc: GpxDocument,
    distanceText: String,
    durationText: String,
    elevationGainText: String,
    maxElevationText: String,
    mapMode: MapMode,
    shareModel: ActivityShareModel,
    language: AppLanguage,
    onComplete: () -> Unit,
) {
    onComplete()
}

@Composable
actual fun GpxNativeMapViewer(
    doc: GpxDocument?,
    liveLocation: LiveLocation?,
    recordedSegments: List<List<LatLon>>,
    showUserLocation: Boolean,
    mapMode: MapMode,
    recenterTrigger: Int,
    modifier: Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text("Map native aktif di iOS/Android (${doc?.tracks?.size ?: 0} track)")
    }
}

@Composable
actual fun GeoPdfNativeViewer(
    pdfBytes: ByteArray,
    geoInfo: GeoPdfInfo,
    liveLocation: LiveLocation?,
    gpxOverlay: GpxDocument?,
    recordedSegments: List<List<LatLon>>,
    modifier: Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text("PDF viewer native aktif di iOS/Android (${pdfBytes.size} bytes)")
    }
}

package com.hikinggear.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import com.hikinggear.geo.Bounds
import com.hikinggear.geo.GeoPdfInfo
import com.hikinggear.geo.GpxDocument
import com.hikinggear.geo.LatLon
import com.hikinggear.geo.LiveLocation
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectMake
import platform.CoreLocation.CLHeading
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLLocationAccuracyBest
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSDirectoryEnumerationSkipsHiddenFiles
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeToURL
import platform.PDFKit.PDFAnnotation
import platform.PDFKit.PDFAnnotationSubtypeCircle
import platform.PDFKit.PDFAnnotationSubtypeInk
import platform.PDFKit.PDFBorder
import platform.PDFKit.PDFDocument
import platform.PDFKit.PDFView
import platform.PDFKit.addBezierPath
import platform.PDFKit.kPDFDisplaySinglePage
import platform.PDFKit.setInteriorColor
import platform.UIKit.UIApplication
import platform.UIKit.UIBezierPath
import platform.UIKit.UIColor
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIView
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.systemBlueColor
import platform.UIKit.systemRedColor
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeData
import platform.UniformTypeIdentifiers.UTTypePDF
import platform.UniformTypeIdentifiers.UTTypeXML
import platform.darwin.NSObject
import platform.posix.memcpy
import kotlin.math.roundToInt

data class NativeMapPin(
    val lat: Double,
    val lon: Double,
    val title: String,
    val subtitle: String?,
)

interface IosNativeMapBridge {
    fun createMapView(): UIView
    fun updateMapView(
        view: UIView,
        trackLines: List<List<LatLon>>,
        recordedLines: List<List<LatLon>>,
        pins: List<NativeMapPin>,
        bounds: Bounds?,
        showUserLocation: Boolean,
        liveLocation: LiveLocation?,
        styleModeId: String,
        recenterTrigger: Int,
    )
    fun refreshOfflineStates()
    fun startOfflineDownload(regionId: String, bounds: Bounds, minZoom: Double, maxZoom: Double, styleModeId: String)
    fun cancelOfflineDownload(regionId: String)
    fun deleteOfflineDownload(regionId: String)
    fun shareActivityCard(
        title: String,
        subtitle: String?,
        trackLines: List<List<LatLon>>,
        bounds: Bounds?,
        distanceText: String,
        durationText: String,
        elevationGainText: String,
        maxElevationText: String,
        styleModeId: String,
        modelId: String,
        languageCode: String,
        shareText: String,
        onComplete: () -> Unit,
    )
}

object IosMapBridgeRegistry {
    var bridge: IosNativeMapBridge? = null
        set(value) {
            field = value
            value?.refreshOfflineStates()
        }

    fun clearAllStates() {
        OfflineMapManager.clearAll()
    }

    fun notifyNotDownloaded(regionId: String) {
        OfflineMapManager.setState(regionId, OfflinePackState.NotDownloaded)
    }

    fun notifyDownloading(regionId: String, completed: Long, expected: Long, bytes: Long) {
        OfflineMapManager.setState(regionId, OfflinePackState.Downloading(completed, expected, bytes))
    }

    fun notifyDownloaded(regionId: String, bytes: Long) {
        OfflineMapManager.setState(regionId, OfflinePackState.Downloaded(bytes))
    }

    fun notifyError(regionId: String, message: String) {
        OfflineMapManager.setState(regionId, OfflinePackState.Error(message))
    }
}

actual fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000.0).toLong()

@OptIn(ExperimentalForeignApi::class)
actual object LocalFileStore {
    private const val ACTIVE_SESSION_FILE = "active_hike_session.txt"
    private const val TITLES_FILE = "library_titles.txt"
    private const val LANGUAGE_FILE = "app_language.txt"

    // ponytail: file-backed store in Documents/library; add SQLite index if tags/search/sync are added later.
    private fun libraryDir(): NSURL? {
        val fm = NSFileManager.defaultManager
        val docs = fm.URLsForDirectory(NSDocumentDirectory, NSUserDomainMask).firstOrNull() as? NSURL
            ?: return null
        val dir = docs.URLByAppendingPathComponent("library", isDirectory = true) ?: return null
        fm.createDirectoryAtURL(dir, withIntermediateDirectories = true, attributes = null, error = null)
        return dir
    }

    private fun readMetaText(fileName: String): String? {
        val fileUrl = libraryDir()?.URLByAppendingPathComponent(fileName) ?: return null
        return NSData.dataWithContentsOfURL(fileUrl)?.toByteArray()?.decodeToString()
    }

    private fun writeMetaText(fileName: String, content: String): Boolean {
        val fileUrl = libraryDir()?.URLByAppendingPathComponent(fileName) ?: return false
        val data = content.encodeToByteArray().toNSData() ?: return false
        return data.writeToURL(fileUrl, atomically = true)
    }

    actual fun list(): List<SavedFileEntry> {
        val dir = libraryDir() ?: return emptyList()
        val fm = NSFileManager.defaultManager
        val urls = fm.contentsOfDirectoryAtURL(
            dir,
            includingPropertiesForKeys = null,
            options = NSDirectoryEnumerationSkipsHiddenFiles,
            error = null,
        ) ?: return emptyList()
        val titles = parseTitleMap(readMetaText(TITLES_FILE))

        return urls.mapNotNull { item ->
            val url = item as? NSURL ?: return@mapNotNull null
            val id = url.lastPathComponent ?: return@mapNotNull null
            val kind = kindFromId(id) ?: return@mapNotNull null
            val path = url.path ?: return@mapNotNull null
            val attrs = fm.attributesOfItemAtPath(path, error = null)
            val size = (attrs?.get(NSFileSize) as? NSNumber)?.longLongValue ?: 0L
            val modMs = (attrs?.get(NSFileModificationDate) as? NSDate)
                ?.let { (it.timeIntervalSince1970 * 1000.0).toLong() } ?: 0L
            SavedFileEntry(
                id = id,
                name = titles[id] ?: id.substringAfter("__"),
                kind = kind,
                sizeBytes = size,
                savedAtEpochMs = modMs,
            )
        }.sortedByDescending { it.savedAtEpochMs }
    }

    actual fun save(name: String, kind: SavedFileKind, bytes: ByteArray): SavedFileEntry? {
        val dir = libraryDir() ?: return null
        val id = buildStoredId(name, kind)
        val fileUrl = dir.URLByAppendingPathComponent(id) ?: return null
        val data = bytes.toNSData() ?: return null
        if (!data.writeToURL(fileUrl, atomically = true)) return null
        return SavedFileEntry(
            id = id,
            name = id.substringAfter("__"),
            kind = kind,
            sizeBytes = bytes.size.toLong(),
            savedAtEpochMs = currentTimeMillis(),
        )
    }

    actual fun read(id: String): ByteArray? {
        if (!isSafeId(id)) return null
        val fileUrl = libraryDir()?.URLByAppendingPathComponent(id) ?: return null
        return NSData.dataWithContentsOfURL(fileUrl)?.toByteArray()
    }

    actual fun rename(id: String, newName: String): Boolean {
        if (!isSafeId(id)) return false
        val clean = newName.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim()
        if (clean.isEmpty()) return false
        val fileUrl = libraryDir()?.URLByAppendingPathComponent(id) ?: return false
        val path = fileUrl.path ?: return false
        if (!NSFileManager.defaultManager.fileExistsAtPath(path)) return false
        val titles = parseTitleMap(readMetaText(TITLES_FILE))
        titles[id] = clean
        return writeMetaText(TITLES_FILE, serializeTitleMap(titles))
    }

    actual fun delete(id: String): Boolean {
        if (!isSafeId(id)) return false
        val fileUrl = libraryDir()?.URLByAppendingPathComponent(id) ?: return false
        val removed = NSFileManager.defaultManager.removeItemAtURL(fileUrl, error = null)
        if (removed) {
            val titles = parseTitleMap(readMetaText(TITLES_FILE))
            if (titles.remove(id) != null) {
                writeMetaText(TITLES_FILE, serializeTitleMap(titles))
            }
        }
        return removed
    }

    actual fun readActiveSession(): String? = readMetaText(ACTIVE_SESSION_FILE)

    actual fun writeActiveSession(content: String): Boolean = writeMetaText(ACTIVE_SESSION_FILE, content)

    actual fun clearActiveSession(): Boolean {
        val fileUrl = libraryDir()?.URLByAppendingPathComponent(ACTIVE_SESSION_FILE) ?: return true
        val fm = NSFileManager.defaultManager
        val path = fileUrl.path ?: return true
        if (!fm.fileExistsAtPath(path)) return true
        return fm.removeItemAtURL(fileUrl, error = null)
    }

    actual fun readLanguageCode(): String? = readMetaText(LANGUAGE_FILE)?.trim()

    actual fun writeLanguageCode(code: String): Boolean = writeMetaText(LANGUAGE_FILE, code.trim())
}

actual object OfflineMapManager {
    private val _states = MutableStateFlow<Map<String, OfflinePackState>>(emptyMap())
    actual val states: StateFlow<Map<String, OfflinePackState>> = _states

    actual fun refresh() {
        IosMapBridgeRegistry.bridge?.refreshOfflineStates()
    }

    actual fun download(regionId: String, bounds: Bounds, minZoom: Double, maxZoom: Double, mapMode: MapMode) {
        val packKey = offlinePackKey(regionId, mapMode)
        setState(packKey, OfflinePackState.Downloading(0L, 0L, 0L))
        IosMapBridgeRegistry.bridge?.startOfflineDownload(packKey, bounds, minZoom, maxZoom, mapMode.id)
    }

    actual fun cancel(regionId: String, mapMode: MapMode) {
        IosMapBridgeRegistry.bridge?.cancelOfflineDownload(offlinePackKey(regionId, mapMode))
    }

    actual fun delete(regionId: String) {
        IosMapBridgeRegistry.bridge?.deleteOfflineDownload(regionId)
    }

    internal fun clearAll() {
        _states.value = emptyMap()
    }

    internal fun setState(regionId: String, state: OfflinePackState) {
        val next = _states.value.toMutableMap()
        if (state is OfflinePackState.NotDownloaded) {
            next.remove(regionId)
        } else {
            next[regionId] = state
        }
        _states.value = next
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

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberFilePicker(
    allowedExtensions: List<String>,
    onResult: (PickedFile?) -> Unit,
): () -> Unit {
    val latestCallback by rememberUpdatedState(onResult)
    val delegateHolder = remember { mutableStateOf<UIDocumentPickerDelegateProtocol?>(null) }

    return remember(allowedExtensions) {
        {
            val types = buildList<UTType> {
                if ("pdf" in allowedExtensions) add(UTTypePDF)
                if ("xml" in allowedExtensions || "gpx" in allowedExtensions) {
                    add(UTTypeXML)
                    UTType.typeWithFilenameExtension("gpx")?.let(::add)
                    add(UTTypeData)
                }
            }.ifEmpty { listOf(UTTypeData) }

            val picker = UIDocumentPickerViewController(forOpeningContentTypes = types, asCopy = true)
            val delegate = object : NSObject(), UIDocumentPickerDelegateProtocol {
                override fun documentPicker(
                    controller: UIDocumentPickerViewController,
                    didPickDocumentsAtURLs: List<*>,
                ) {
                    val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
                    if (url == null) {
                        latestCallback(null)
                        return
                    }
                    val accessed = url.startAccessingSecurityScopedResource()
                    try {
                        val data = NSData.dataWithContentsOfURL(url)
                        val bytes = data?.toByteArray()
                        val name = url.lastPathComponent ?: "file"
                        latestCallback(bytes?.let { PickedFile(name, it) })
                    } finally {
                        if (accessed) url.stopAccessingSecurityScopedResource()
                    }
                    delegateHolder.value = null
                }

                override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
                    latestCallback(null)
                    delegateHolder.value = null
                }
            }
            delegateHolder.value = delegate
            picker.delegate = delegate

            topViewController()?.presentViewController(picker, animated = true, completion = null)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberLiveLocation(
    enabled: Boolean,
    keepActiveInBackground: Boolean,
): State<LiveLocation?> {
    val state = remember { mutableStateOf<LiveLocation?>(null) }
    // CLLocationManager.delegate is weak in ObjC; retain delegate strongly for effect lifetime.
    val delegateHolder = remember { mutableStateOf<CLLocationManagerDelegateProtocol?>(null) }

    DisposableEffect(enabled, keepActiveInBackground) {
        if (!enabled) {
            state.value = null
            delegateHolder.value = null
            return@DisposableEffect onDispose {}
        }

        val manager = CLLocationManager()
        val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
            override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
                val loc = didUpdateLocations.lastOrNull() as? CLLocation ?: return
                loc.coordinate.useContents {
                    state.value = LiveLocation(
                        coord = LatLon(latitude, longitude),
                        altitudeMeters = loc.altitude,
                        accuracyMeters = loc.horizontalAccuracy,
                    )
                }
            }

            override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
                // Keep last known fix; user may not have granted permission yet.
            }
        }
        delegateHolder.value = delegate
        manager.delegate = delegate
        manager.desiredAccuracy = kCLLocationAccuracyBest
        manager.distanceFilter = 5.0
        if (keepActiveInBackground) {
            manager.allowsBackgroundLocationUpdates = true
            manager.pausesLocationUpdatesAutomatically = false
            manager.showsBackgroundLocationIndicator = true
        }
        manager.requestWhenInUseAuthorization()
        manager.startUpdatingLocation()

        onDispose {
            manager.stopUpdatingLocation()
            if (keepActiveInBackground) {
                manager.allowsBackgroundLocationUpdates = false
            }
            manager.delegate = null
            delegateHolder.value = null
        }
    }

    return state
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberCompassHeading(): State<CompassHeading?> {
    val state = remember { mutableStateOf<CompassHeading?>(null) }
    val delegateHolder = remember { mutableStateOf<CLLocationManagerDelegateProtocol?>(null) }

    DisposableEffect(Unit) {
        if (!CLLocationManager.headingAvailable()) {
            return@DisposableEffect onDispose {}
        }
        val manager = CLLocationManager()
        val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
            override fun locationManager(manager: CLLocationManager, didUpdateHeading: CLHeading) {
                val useTrue = didUpdateHeading.trueHeading >= 0.0
                val rawDeg = if (useTrue) didUpdateHeading.trueHeading else didUpdateHeading.magneticHeading
                val valid = didUpdateHeading.headingAccuracy >= 0.0
                val prev = state.value
                if (rawDeg >= 0.0 && (valid || prev == null)) {
                    state.value = CompassHeading(
                        degrees = rawDeg,
                        isTrueNorth = useTrue,
                        accuracyDegrees = didUpdateHeading.headingAccuracy,
                    )
                } else if (!valid && prev != null) {
                    state.value = prev.copy(accuracyDegrees = didUpdateHeading.headingAccuracy)
                }
            }

            override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
                // Ignore transient heading errors and keep listening.
            }

            override fun locationManagerShouldDisplayHeadingCalibration(
                manager: CLLocationManager,
            ): Boolean = true
        }
        delegateHolder.value = delegate
        manager.delegate = delegate
        manager.headingFilter = 1.0
        manager.startUpdatingHeading()

        onDispose {
            manager.stopUpdatingHeading()
            manager.delegate = null
            delegateHolder.value = null
        }
    }

    return state
}

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
    val bridge = IosMapBridgeRegistry.bridge
    if (bridge == null) {
        onComplete()
        return
    }
    val trackLines = doc.tracks.mapNotNull { track ->
        if (track.points.size < 2) null else track.points.map { it.coord }
    }
    val isId = language == AppLanguage.INDONESIAN
    val shareText = buildString {
        append(title)
        val desc = description?.trim()?.takeIf { it.isNotEmpty() }
        if (desc != null) {
            append("\n")
            append(desc)
        }
        if (isId) {
            append("\nJarak: $distanceText • Durasi: $durationText • Kenaikan: $elevationGainText • Maks: $maxElevationText")
        } else {
            append("\nDistance: $distanceText • Duration: $durationText • Elev. Gain: $elevationGainText • Max: $maxElevationText")
        }
    }
    bridge.shareActivityCard(
        title = title,
        subtitle = description?.trim()?.takeIf { it.isNotEmpty() },
        trackLines = trackLines,
        bounds = doc.bounds,
        distanceText = distanceText,
        durationText = durationText,
        elevationGainText = elevationGainText,
        maxElevationText = maxElevationText,
        styleModeId = mapMode.id,
        modelId = shareModel.id,
        languageCode = language.code,
        shareText = shareText,
        onComplete = onComplete,
    )
}

@OptIn(ExperimentalForeignApi::class)
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
    val bridge = IosMapBridgeRegistry.bridge
    val trackLines = remember(doc) {
        doc?.tracks?.mapNotNull { track ->
            if (track.points.size < 2) null else track.points.map { it.coord }
        }.orEmpty()
    }
    val pins = remember(doc) {
        doc?.waypoints?.map { wpt ->
            NativeMapPin(
                lat = wpt.coord.lat,
                lon = wpt.coord.lon,
                title = wpt.name ?: "Waypoint",
                subtitle = wpt.elevationMeters?.let { "${it.roundToInt()} mdpl" },
            )
        }.orEmpty()
    }

    UIKitView(
        modifier = modifier,
        factory = {
            bridge?.createMapView() ?: UIView()
        },
        update = { view ->
            bridge?.updateMapView(
                view = view,
                trackLines = trackLines,
                recordedLines = recordedSegments,
                pins = pins,
                bounds = doc?.bounds,
                showUserLocation = showUserLocation,
                liveLocation = liveLocation,
                styleModeId = mapMode.id,
                recenterTrigger = recenterTrigger,
            )
        },
    )
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun GeoPdfNativeViewer(
    pdfBytes: ByteArray,
    geoInfo: GeoPdfInfo,
    liveLocation: LiveLocation?,
    gpxOverlay: GpxDocument?,
    recordedSegments: List<List<LatLon>>,
    modifier: Modifier,
) {
    val pdfDoc = remember(pdfBytes) {
        pdfBytes.toNSData()?.let { PDFDocument(data = it) }
    }
    val activeAnnotations = remember(pdfDoc) { mutableListOf<PDFAnnotation>() }

    UIKitView(
        modifier = modifier,
        factory = {
            PDFView().apply {
                autoScales = true
                displayMode = kPDFDisplaySinglePage
                backgroundColor = UIColor.blackColor
                document = pdfDoc
            }
        },
        update = { pdfView ->
            if (pdfView.document !== pdfDoc) {
                pdfView.document = pdfDoc
            }
            val page = pdfDoc?.pageAtIndex(0uL) ?: return@UIKitView

            activeAnnotations.forEach { page.removeAnnotation(it) }
            activeAnnotations.clear()

            if (!geoInfo.isGeoreferenced) return@UIKitView

            fun addPolylineAnnotation(coords: List<LatLon>, strokeColor: UIColor, width: Double) {
                val pdfPts = coords.mapNotNull { geoInfo.latLonToPage(it) }
                if (pdfPts.size < 2) return
                var minX = Double.POSITIVE_INFINITY
                var minY = Double.POSITIVE_INFINITY
                var maxX = Double.NEGATIVE_INFINITY
                var maxY = Double.NEGATIVE_INFINITY
                pdfPts.forEach { p ->
                    if (p.x < minX) minX = p.x
                    if (p.y < minY) minY = p.y
                    if (p.x > maxX) maxX = p.x
                    if (p.y > maxY) maxY = p.y
                }
                val pad = 4.0
                val bx = minX - pad
                val by = minY - pad
                val bw = (maxX - minX) + pad * 2
                val bh = (maxY - minY) + pad * 2
                val path = UIBezierPath()
                pdfPts.forEachIndexed { idx, p ->
                    val pt = CGPointMake(p.x - bx, p.y - by)
                    if (idx == 0) path.moveToPoint(pt) else path.addLineToPoint(pt)
                }
                val ink = PDFAnnotation(
                    bounds = CGRectMake(bx, by, bw, bh),
                    forType = PDFAnnotationSubtypeInk,
                    withProperties = null,
                ).apply {
                    color = strokeColor
                    border = PDFBorder().apply { lineWidth = width }
                    addBezierPath(path)
                }
                page.addAnnotation(ink)
                activeAnnotations.add(ink)
            }

            // Draw reference GPX tracks (orange)
            gpxOverlay?.tracks?.forEach { track ->
                addPolylineAnnotation(track.points.map { it.coord }, UIColor.orangeColor, 2.5)
            }

            // Draw live recorded hike trail (red)
            recordedSegments.forEach { seg ->
                addPolylineAnnotation(seg, UIColor.systemRedColor, 3.0)
            }

            // Draw live GPS blue dot in native PDF page coordinates
            liveLocation?.let { loc ->
                val pt = geoInfo.latLonToPage(loc.coord) ?: return@let
                val r = 6.0
                val dot = PDFAnnotation(
                    bounds = CGRectMake(pt.x - r, pt.y - r, r * 2, r * 2),
                    forType = PDFAnnotationSubtypeCircle,
                    withProperties = null,
                ).apply {
                    color = UIColor.whiteColor
                    setInteriorColor(UIColor.systemBlueColor)
                    border = PDFBorder().apply { lineWidth = 2.0 }
                }
                page.addAnnotation(dot)
                activeAnnotations.add(dot)
            }
        },
    )
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData? {
    if (isEmpty()) return NSData()
    return usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val len = length.toInt()
    if (len == 0) return ByteArray(0)
    val out = ByteArray(len)
    out.usePinned { pinned ->
        memcpy(pinned.addressOf(0), bytes, length)
    }
    return out
}

private fun topViewController() =
    UIApplication.sharedApplication.connectedScenes
        .filterIsInstance<UIWindowScene>()
        .flatMap { it.windows.filterIsInstance<UIWindow>() }
        .firstOrNull { it.isKeyWindow() }
        ?.rootViewController
        ?.let { root ->
            var top = root
            while (top.presentedViewController != null) {
                top = top.presentedViewController!!
            }
            top
        }

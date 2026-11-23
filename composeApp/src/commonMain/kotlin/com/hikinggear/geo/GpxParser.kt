package com.hikinggear.geo

data class GpxPoint(
    val coord: LatLon,
    val elevationMeters: Double? = null,
    val time: String? = null,
    val name: String? = null,
)

data class GpxTrack(
    val name: String?,
    val points: List<GpxPoint>,
    val distanceMeters: Double,
    val elevationGainMeters: Double,
    val elevationLossMeters: Double,
    val minElevation: Double?,
    val maxElevation: Double?,
)

data class GpxDocument(
    val name: String?,
    val description: String? = null,
    val tracks: List<GpxTrack>,
    val waypoints: List<GpxPoint>,
    val bounds: Bounds?,
) {
    val totalDistanceMeters: Double get() = tracks.sumOf { it.distanceMeters }
    val totalElevationGainMeters: Double get() = tracks.sumOf { it.elevationGainMeters }
    val maxElevationMeters: Double? get() = tracks.mapNotNull { it.maxElevation }.maxOrNull()
    val durationMs: Long?
        get() {
            val firstMs = tracks.firstNotNullOfOrNull { t ->
                t.points.firstNotNullOfOrNull { it.time?.let(::parseIsoEpochMs) }
            } ?: return null
            val lastMs = tracks.asReversed().firstNotNullOfOrNull { t ->
                t.points.asReversed().firstNotNullOfOrNull { it.time?.let(::parseIsoEpochMs) }
            } ?: return null
            return (lastMs - firstMs).coerceAtLeast(0L)
        }
}

/**
 * Zero-dependency GPX 1.0/1.1 parser for <trk>/<trkpt>, <rte>/<rtept>, and <wpt>.
 * ponytail: regex-based XML extraction; swap for streaming pull parser only if parsing >20 MB GPX files becomes slow.
 */
object GpxParser {
    private val trackBlockRegex = Regex("<(?:\\w+:)?(?:trk|rte)\\b[^>]*>([\\s\\S]*?)</(?:\\w+:)?(?:trk|rte)>")
    private val pointWithBodyRegex = Regex("<(?:\\w+:)?(trkpt|rtept)\\b([^>]*)>([\\s\\S]*?)</(?:\\w+:)?\\1>")
    private val selfClosingPointRegex = Regex("<(?:\\w+:)?(trkpt|rtept)\\b([^>]*)/\\s*>")
    private val wptWithBodyRegex = Regex("<(?:\\w+:)?wpt\\b([^>]*)>([\\s\\S]*?)</(?:\\w+:)?wpt>")
    private val wptSelfClosingRegex = Regex("<(?:\\w+:)?wpt\\b([^>]*)/\\s*>")
    private val attrRegex = Regex("\\b(lat|lon)\\s*=\\s*[\"']([^\"']+)[\"']")
    private val eleRegex = Regex("<(?:\\w+:)?ele\\b[^>]*>\\s*([^<]+?)\\s*</(?:\\w+:)?ele>")
    private val nameRegex = Regex("<(?:\\w+:)?name\\b[^>]*>\\s*(?:<!\\[CDATA\\[([\\s\\S]*?)]]>|([^<]*?))\\s*</(?:\\w+:)?name>")
    private val descRegex = Regex("<(?:\\w+:)?desc\\b[^>]*>\\s*(?:<!\\[CDATA\\[([\\s\\S]*?)]]>|([^<]*?))\\s*</(?:\\w+:)?desc>")
    private val timeRegex = Regex("<(?:\\w+:)?time\\b[^>]*>\\s*([^<]+?)\\s*</(?:\\w+:)?time>")
    private val metadataRegex = Regex("<(?:\\w+:)?metadata\\b[^>]*>([\\s\\S]*?)</(?:\\w+:)?metadata>")

    fun parse(xml: String): GpxDocument {
        val metaBlock = metadataRegex.find(xml)?.groupValues?.get(1)
        val docName = metaBlock?.let(::extractName)
        var trackDescFallback: String? = null

        val tracks = trackBlockRegex.findAll(xml).mapNotNull { match ->
            val block = match.groupValues[1]
            val trackName = extractName(block)
            if (trackDescFallback == null) trackDescFallback = extractDesc(block)
            val points = extractTrackPoints(block)
            if (points.isEmpty()) null else buildTrack(trackName, points)
        }.toList()

        val docDesc = metaBlock?.let(::extractDesc) ?: trackDescFallback
        val waypoints = extractWaypoints(xml)

        // Fallback: if GPX has bare <trkpt>/<rtept> outside <trk>/<rte>
        val finalTracks = if (tracks.isEmpty()) {
            val loose = extractTrackPoints(xml)
            if (loose.isNotEmpty()) listOf(buildTrack(docName, loose)) else emptyList()
        } else {
            tracks
        }

        val allCoords = buildList {
            finalTracks.forEach { t -> t.points.forEach { add(it.coord) } }
            waypoints.forEach { add(it.coord) }
        }

        return GpxDocument(
            name = docName ?: finalTracks.firstOrNull()?.name,
            description = docDesc,
            tracks = finalTracks,
            waypoints = waypoints,
            bounds = Bounds.from(allCoords),
        )
    }

    private fun extractTrackPoints(content: String): List<GpxPoint> {
        val indexed = mutableListOf<Pair<Int, GpxPoint>>()

        for (m in pointWithBodyRegex.findAll(content)) {
            parsePoint(m.groupValues[2], m.groupValues[3])?.let { indexed += m.range.first to it }
        }
        for (m in selfClosingPointRegex.findAll(content)) {
            parsePoint(m.groupValues[2], "")?.let { indexed += m.range.first to it }
        }

        return indexed.sortedBy { it.first }.map { it.second }
    }

    private fun extractWaypoints(content: String): List<GpxPoint> {
        val indexed = mutableListOf<Pair<Int, GpxPoint>>()

        for (m in wptWithBodyRegex.findAll(content)) {
            parsePoint(m.groupValues[1], m.groupValues[2])?.let { indexed += m.range.first to it }
        }
        for (m in wptSelfClosingRegex.findAll(content)) {
            parsePoint(m.groupValues[1], "")?.let { indexed += m.range.first to it }
        }

        return indexed.sortedBy { it.first }.map { it.second }
    }

    private fun parsePoint(attrs: String, body: String): GpxPoint? {
        var lat: Double? = null
        var lon: Double? = null
        for (a in attrRegex.findAll(attrs)) {
            val value = a.groupValues[2].trim().toDoubleOrNull() ?: continue
            when (a.groupValues[1]) {
                "lat" -> lat = value
                "lon" -> lon = value
            }
        }
        if (lat == null || lon == null) return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null

        val ele = eleRegex.find(body)?.groupValues?.get(1)?.trim()?.toDoubleOrNull()
        val time = timeRegex.find(body)?.groupValues?.get(1)?.trim()
        val name = extractName(body)
        return GpxPoint(LatLon(lat, lon), elevationMeters = ele, time = time, name = name)
    }

    private fun extractName(block: String): String? = extractTagText(nameRegex, block)

    private fun extractDesc(block: String): String? = extractTagText(descRegex, block)

    private fun extractTagText(regex: Regex, block: String): String? {
        val m = regex.find(block) ?: return null
        val raw = (m.groupValues[1].ifEmpty { m.groupValues[2] }).trim()
        return raw.ifEmpty { null }?.unescapeXml()
    }

    private fun buildTrack(name: String?, points: List<GpxPoint>): GpxTrack {
        var dist = 0.0
        var gain = 0.0
        var loss = 0.0
        var minEle: Double? = null
        var maxEle: Double? = null
        var prevEle: Double? = null

        for (i in points.indices) {
            if (i > 0) dist += haversineMeters(points[i - 1].coord, points[i].coord)
            val e = points[i].elevationMeters
            if (e != null) {
                if (minEle == null || e < minEle) minEle = e
                if (maxEle == null || e > maxEle) maxEle = e
                if (prevEle != null) {
                    val delta = e - prevEle
                    if (delta > 0) gain += delta else loss -= delta
                }
                prevEle = e
            }
        }

        return GpxTrack(
            name = name,
            points = points,
            distanceMeters = dist,
            elevationGainMeters = gain,
            elevationLossMeters = loss,
            minElevation = minEle,
            maxElevation = maxEle,
        )
    }

    private fun String.unescapeXml(): String =
        replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
}


private val isoTimeRegex = Regex("""^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.\d+)?(Z|[+-]\d{2}:\d{2})?$""")

internal fun parseIsoEpochMs(raw: String): Long? {
    val m = isoTimeRegex.matchEntire(raw.trim()) ?: return null
    val year = m.groupValues[1].toLongOrNull() ?: return null
    val month = m.groupValues[2].toLongOrNull() ?: return null
    val day = m.groupValues[3].toLongOrNull() ?: return null
    val hour = m.groupValues[4].toLongOrNull() ?: return null
    val min = m.groupValues[5].toLongOrNull() ?: return null
    val sec = m.groupValues[6].toLongOrNull() ?: return null
    val tz = m.groupValues[7]
    val offsetSec = when {
        tz.isEmpty() || tz == "Z" -> 0L
        else -> {
            val sign = if (tz[0] == '-') -1L else 1L
            val tzH = tz.substring(1, 3).toLongOrNull() ?: return null
            val tzM = tz.substring(4, 6).toLongOrNull() ?: return null
            sign * (tzH * 3600L + tzM * 60L)
        }
    }
    val y = year - if (month <= 2L) 1L else 0L
    val era = (if (y >= 0L) y else y - 399L) / 400L
    val yoe = y - era * 400L
    val doy = (153L * (month + if (month > 2L) -3L else 9L) + 2L) / 5L + day - 1L
    val doe = yoe * 365L + yoe / 4L - yoe / 100L + doy
    val days = era * 146_097L + doe - 719_468L
    return (days * 86_400L + hour * 3600L + min * 60L + sec - offsetSec) * 1000L
}

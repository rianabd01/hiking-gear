package com.hikinggear.geo

import kotlin.math.roundToInt

enum class RecordingStatus {
    RECORDING,
    PAUSED,
}

data class RecordedPoint(
    val lat: Double,
    val lon: Double,
    val elevationMeters: Double?,
    val epochMs: Long,
)

data class ActiveHikeSession(
    val startedAtEpochMs: Long,
    val status: RecordingStatus,
    val accumulatedMovingMs: Long,
    val lastResumedAtEpochMs: Long?,
    val segments: List<List<RecordedPoint>>,
    val sourceFileId: String? = null,
) {
    val pointCount: Int
        get() = segments.sumOf { it.size }

    val coordSegments: List<List<LatLon>>
        get() = segments.mapNotNull { seg ->
            if (seg.size < 2) null else seg.map { LatLon(it.lat, it.lon) }
        }

    val totalDistanceMeters: Double
        get() = segments.sumOf { seg ->
            var d = 0.0
            for (i in 1 until seg.size) {
                d += haversineMeters(
                    LatLon(seg[i - 1].lat, seg[i - 1].lon),
                    LatLon(seg[i].lat, seg[i].lon),
                )
            }
            d
        }

    val totalElevationGainMeters: Double
        get() = segments.sumOf { seg ->
            var gain = 0.0
            var prev: Double? = null
            for (pt in seg) {
                val e = pt.elevationMeters ?: continue
                if (prev != null && e - prev > 1.5) gain += (e - prev)
                prev = e
            }
            gain
        }

    fun elapsedMs(nowMs: Long): Long {
        val activeDelta = if (status == RecordingStatus.RECORDING && lastResumedAtEpochMs != null) {
            (nowMs - lastResumedAtEpochMs).coerceAtLeast(0L)
        } else {
            0L
        }
        return accumulatedMovingMs + activeDelta
    }

    fun pause(nowMs: Long): ActiveHikeSession {
        if (status == RecordingStatus.PAUSED) return this
        return copy(
            status = RecordingStatus.PAUSED,
            accumulatedMovingMs = elapsedMs(nowMs),
            lastResumedAtEpochMs = null,
        )
    }

    fun resume(nowMs: Long): ActiveHikeSession {
        if (status == RecordingStatus.RECORDING) return this
        val nextSegments = if (segments.isEmpty() || segments.last().isNotEmpty()) {
            segments + listOf(emptyList())
        } else {
            segments
        }
        return copy(
            status = RecordingStatus.RECORDING,
            lastResumedAtEpochMs = nowMs,
            segments = nextSegments,
        )
    }

    fun appendLocation(loc: LiveLocation, nowMs: Long): ActiveHikeSession? {
        if (status != RecordingStatus.RECORDING) return null
        if (loc.accuracyMeters != null && loc.accuracyMeters > MAX_ACCURACY_METERS) return null

        val currentSegs = if (segments.isEmpty()) listOf(emptyList()) else segments
        val lastSeg = currentSegs.last()
        val prev = lastSeg.lastOrNull()
        if (prev != null) {
            val moved = haversineMeters(LatLon(prev.lat, prev.lon), loc.coord)
            if (moved < MIN_STEP_METERS) return null
        }

        val nextPt = RecordedPoint(
            lat = loc.coord.lat,
            lon = loc.coord.lon,
            elevationMeters = loc.altitudeMeters,
            epochMs = nowMs,
        )
        val updatedSegs = currentSegs.dropLast(1) + listOf(lastSeg + nextPt)
        return copy(segments = updatedSegs)
    }

    fun defaultFileName(): String = "Pendakian-${formatCompactUtc(startedAtEpochMs)}.gpx"

    fun toGpxXml(
        trackName: String = "Aktivitas Pendakian",
        description: String? = null,
    ): String =
        buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("""<gpx version="1.1" creator="HikingGear">""")
            appendLine("  <metadata>")
            appendLine("    <name>${trackName.escapeXml()}</name>")
            description?.trim()?.takeIf { it.isNotEmpty() }?.let { desc ->
                appendLine("    <desc>${desc.escapeXml()}</desc>")
            }
            appendLine("  </metadata>")
            val nonEmpty = segments.filter { it.isNotEmpty() }
            nonEmpty.forEachIndexed { idx, seg ->
                val segName = if (nonEmpty.size == 1) trackName else "$trackName #${idx + 1}"
                appendLine("  <trk>")
                appendLine("    <name>${segName.escapeXml()}</name>")
                appendLine("    <trkseg>")
                seg.forEach { pt ->
                    append("""      <trkpt lat="${pt.lat}" lon="${pt.lon}">""")
                    pt.elevationMeters?.let { ele ->
                        val rounded = (ele * 10.0).roundToInt() / 10.0
                        append("<ele>$rounded</ele>")
                    }
                    append("<time>${formatIsoUtc(pt.epochMs)}</time>")
                    appendLine("</trkpt>")
                }
                appendLine("    </trkseg>")
                appendLine("  </trk>")
            }
            appendLine("</gpx>")
        }

    fun serialize(): String = buildString {
        val safeSource = sourceFileId.orEmpty().replace('\t', ' ').replace('\n', ' ')
        appendLine("V1\t$startedAtEpochMs\t${status.name}\t$accumulatedMovingMs\t${lastResumedAtEpochMs ?: -1L}\t$safeSource")
        segments.forEach { seg ->
            appendLine("SEG")
            seg.forEach { pt ->
                appendLine("PT\t${pt.lat}\t${pt.lon}\t${pt.elevationMeters ?: ""}\t${pt.epochMs}")
            }
        }
    }

    companion object {
        private const val MIN_STEP_METERS = 5.0
        private const val MAX_ACCURACY_METERS = 50.0

        fun start(nowMs: Long, sourceFileId: String? = null): ActiveHikeSession = ActiveHikeSession(
            startedAtEpochMs = nowMs,
            status = RecordingStatus.RECORDING,
            accumulatedMovingMs = 0L,
            lastResumedAtEpochMs = nowMs,
            segments = listOf(emptyList()),
            sourceFileId = sourceFileId,
        )

        fun deserialize(raw: String): ActiveHikeSession? {
            val lines = raw.lineSequence().map { it.trimEnd('\r', '\n') }.filter { it.isNotBlank() }.toList()
            if (lines.isEmpty()) return null
            val header = lines.first().split('\t')
            if (header.size < 5 || header[0] != "V1") return null

            val startedAt = header[1].toLongOrNull() ?: return null
            val status = runCatching { RecordingStatus.valueOf(header[2]) }.getOrNull() ?: return null
            val accMs = header[3].toLongOrNull() ?: return null
            val resumedRaw = header[4].toLongOrNull() ?: -1L
            val resumedAt = if (resumedRaw >= 0L) resumedRaw else null
            val sourceId = header.getOrNull(5)?.takeIf { it.isNotEmpty() }

            val segments = mutableListOf<MutableList<RecordedPoint>>()
            for (i in 1 until lines.size) {
                val line = lines[i]
                if (line == "SEG") {
                    segments += mutableListOf<RecordedPoint>()
                } else if (line.startsWith("PT\t")) {
                    val parts = line.split('\t')
                    if (parts.size < 5) continue
                    val lat = parts[1].toDoubleOrNull() ?: continue
                    val lon = parts[2].toDoubleOrNull() ?: continue
                    val ele = parts[3].takeIf { it.isNotEmpty() }?.toDoubleOrNull()
                    val ts = parts[4].toLongOrNull() ?: continue
                    if (segments.isEmpty()) segments += mutableListOf<RecordedPoint>()
                    segments.last() += RecordedPoint(lat, lon, ele, ts)
                }
            }

            return ActiveHikeSession(
                startedAtEpochMs = startedAt,
                status = status,
                accumulatedMovingMs = accMs,
                lastResumedAtEpochMs = resumedAt,
                segments = segments.ifEmpty { listOf(emptyList<RecordedPoint>()) },
                sourceFileId = sourceId,
            )
        }
    }
}

fun formatDurationHms(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val h = totalSec / 3600L
    val m = (totalSec % 3600L) / 60L
    val s = totalSec % 60L
    return "${pad2(h)}:${pad2(m)}:${pad2(s)}"
}

private fun pad2(v: Long): String = if (v < 10) "0$v" else "$v"

private fun String.escapeXml(): String =
    replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

private fun epochMsToUtcParts(epochMs: Long): LongArray {
    val sec = Math.floorDivCompat(epochMs, 1000L)
    val days = Math.floorDivCompat(sec, 86_400L)
    val secOfDay = sec - days * 86_400L
    val z = days + 719_468L
    val era = (if (z >= 0) z else z - 146_096L) / 146_097L
    val doe = z - era * 146_097L
    val yoe = (doe - doe / 1460L + doe / 36_524L - doe / 146_096L) / 365L
    val y = yoe + era * 400L
    val doy = doe - (365L * yoe + yoe / 4L - yoe / 100L)
    val mp = (5L * doy + 2L) / 153L
    val d = doy - (153L * mp + 2L) / 5L + 1L
    val m = mp + (if (mp < 10L) 3L else -9L)
    val year = y + (if (m <= 2L) 1L else 0L)
    val hour = secOfDay / 3600L
    val minute = (secOfDay % 3600L) / 60L
    val second = secOfDay % 60L
    return longArrayOf(year, m, d, hour, minute, second)
}

private object Math {
    fun floorDivCompat(a: Long, b: Long): Long {
        var q = a / b
        if ((a xor b) < 0L && q * b != a) q -= 1L
        return q
    }
}

private fun formatIsoUtc(epochMs: Long): String {
    val p = epochMsToUtcParts(epochMs)
    return "${p[0]}-${pad2(p[1])}-${pad2(p[2])}T${pad2(p[3])}:${pad2(p[4])}:${pad2(p[5])}Z"
}

private fun formatCompactUtc(epochMs: Long): String {
    val p = epochMsToUtcParts(epochMs)
    return "${p[0]}${pad2(p[1])}${pad2(p[2])}-${pad2(p[3])}${pad2(p[4])}"
}

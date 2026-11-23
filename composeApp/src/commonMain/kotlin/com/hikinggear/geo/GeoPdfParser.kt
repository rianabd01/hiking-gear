package com.hikinggear.geo

import kotlin.math.abs

data class PdfPoint(
    val x: Double,
    val y: Double,
)

data class PdfRect(
    val minX: Double,
    val minY: Double,
    val maxX: Double,
    val maxY: Double,
) {
    val width: Double get() = maxX - minX
    val height: Double get() = maxY - minY
    fun contains(p: PdfPoint): Boolean = p.x in minX..maxX && p.y in minY..maxY
}

/** Ordered PDF BBox [x0, y0, x1, y1] preserving orientation so /LPTS [0..1] maps without flipping Y. */
private data class RawBBox(
    val x0: Double,
    val y0: Double,
    val x1: Double,
    val y1: Double,
) {
    fun toPdfRect(): PdfRect = PdfRect(
        minX = minOf(x0, x1),
        minY = minOf(y0, y1),
        maxX = maxOf(x0, x1),
        maxY = maxOf(y0, y1),
    )

    fun mapNormalized(u: Double, v: Double): PdfPoint = PdfPoint(
        x = x0 + u * (x1 - x0),
        y = y0 + v * (y1 - y0),
    )
}

enum class GeoPdfStandard {
    ISO_32000_1,
    TERRAGO_LGI,
    NONE,
}

/**
 * Affine mapping between WGS84 (lat, lon) and PDF page coordinates (x, y in points, origin bottom-left).
 * ponytail: linear affine fit assumes local hiking topo sheet extents (<= ~1 deg); add PROJ/UTM projection
 * when supporting continental-scale Geospatial PDFs where map curvature exceeds ~10m.
 */
data class AffineTransform(
    // x = ax*lon + bx*lat + cx
    val ax: Double,
    val bx: Double,
    val cx: Double,
    // y = ay*lon + by*lat + cy
    val ay: Double,
    val by: Double,
    val cy: Double,
) {
    fun latLonToPage(coord: LatLon): PdfPoint = PdfPoint(
        x = ax * coord.lon + bx * coord.lat + cx,
        y = ay * coord.lon + by * coord.lat + cy,
    )

    fun pageToLatLon(point: PdfPoint): LatLon? {
        val det = ax * by - bx * ay
        if (abs(det) < 1e-18) return null
        val dx = point.x - cx
        val dy = point.y - cy
        val lon = (dx * by - bx * dy) / det
        val lat = (ax * dy - dx * ay) / det
        return LatLon(lat = lat, lon = lon)
    }

    companion object {
        /** Fits least-squares affine transform from >= 3 (LatLon -> PdfPoint) control pairs. */
        fun fit(pairs: List<Pair<LatLon, PdfPoint>>): AffineTransform? {
            if (pairs.size < 3) return null
            // Normal equations (A^T A) c = A^T b for basis [lon, lat, 1]
            val ata = Array(3) { DoubleArray(3) }
            val atx = DoubleArray(3)
            val aty = DoubleArray(3)

            for ((geo, pdf) in pairs) {
                val row = doubleArrayOf(geo.lon, geo.lat, 1.0)
                for (r in 0..2) {
                    atx[r] += row[r] * pdf.x
                    aty[r] += row[r] * pdf.y
                    for (c in 0..2) {
                        ata[r][c] += row[r] * row[c]
                    }
                }
            }

            val cx = solve3x3(ata, atx) ?: return null
            val cy = solve3x3(ata, aty) ?: return null
            return AffineTransform(cx[0], cx[1], cx[2], cy[0], cy[1], cy[2])
        }

        private fun solve3x3(matrix: Array<DoubleArray>, rhs: DoubleArray): DoubleArray? {
            val a = Array(3) { r -> DoubleArray(4) { c -> if (c < 3) matrix[r][c] else rhs[r] } }
            for (col in 0..2) {
                var pivot = col
                for (r in col + 1..2) {
                    if (abs(a[r][col]) > abs(a[pivot][col])) pivot = r
                }
                if (abs(a[pivot][col]) < 1e-18) return null
                if (pivot != col) {
                    val tmp = a[col]
                    a[col] = a[pivot]
                    a[pivot] = tmp
                }
                val div = a[col][col]
                for (c in col..3) a[col][c] /= div
                for (r in 0..2) {
                    if (r == col) continue
                    val factor = a[r][col]
                    for (c in col..3) a[r][c] -= factor * a[col][c]
                }
            }
            return doubleArrayOf(a[0][3], a[1][3], a[2][3])
        }
    }
}

data class GeoPdfInfo(
    val standard: GeoPdfStandard,
    val mediaBox: PdfRect?,
    val viewportBBox: PdfRect?,
    val transform: AffineTransform?,
    val geoBounds: Bounds?,
    val gcsDescription: String? = null,
    val controlPointCount: Int = 0,
) {
    val isGeoreferenced: Boolean get() = transform != null

    fun latLonToPage(coord: LatLon): PdfPoint? = transform?.latLonToPage(coord)
    fun pageToLatLon(point: PdfPoint): LatLon? = transform?.pageToLatLon(point)
}

/**
 * Pure-Kotlin Geospatial PDF metadata extractor supporting:
 * 1. ISO 32000-1 / Adobe Supplement (/VP, /BBox, /Measure, /GPTS, /LPTS, /GCS)
 * 2. TerraGo GeoPDF (/LGIDict with /Registration pairs)
 *
 * ponytail: scans uncompressed PDF object dictionaries directly (skipping binary stream bodies);
 * compressed ObjStm (/Type /ObjStm) objects need a flate stream decoder if encountered.
 */
object GeoPdfParser {
    private val objStartRegex = Regex("""(?:^|[\r\n])\s*(\d+)\s+\d+\s+obj\b""")
    private val indirectRefRegex = Regex("""(\d+)\s+\d+\s+R\b""")
    private val numberRegex = Regex("""[+-]?(?:\d+\.?\d*|\.\d+)(?:[eE][+-]?\d+)?""")
    private val wktRegex = Regex("""/WKT\s*\(([^)]{1,400})\)""")
    private val epsgRegex = Regex("""/EPSG\s+(\d+)""")

    fun parse(pdfBytes: ByteArray): GeoPdfInfo {
        val text = pdfBytes.decodeLatin1()
        val objects = indexObjects(text)
        // Search only non-stream dictionary text so binary image streams never create false /VP or /GCS matches
        val dictText = if (objects.isNotEmpty()) objects.values.joinToString("\n") else text

        val mediaRaw = extractNumberArray(dictText, "/MediaBox", objects)?.toRawBBox()
            ?: extractNumberArray(dictText, "/CropBox", objects)?.toRawBBox()
        val mediaBox = mediaRaw?.toPdfRect()

        parseIso32000(dictText, objects, mediaRaw, mediaBox)?.let { return it }
        parseTerraGo(dictText, objects, mediaBox)?.let { return it }

        return GeoPdfInfo(
            standard = GeoPdfStandard.NONE,
            mediaBox = mediaBox,
            viewportBBox = null,
            transform = null,
            geoBounds = null,
        )
    }

    private fun parseIso32000(
        dictText: String,
        objects: Map<Int, String>,
        mediaRaw: RawBBox?,
        mediaBox: PdfRect?,
    ): GeoPdfInfo? {
        val vpRaw = extractValueAfterKey(dictText, "/VP", objects) ?: return null
        val vpDict = firstDictionaryIn(vpRaw, objects) ?: vpRaw

        val rawBBox = extractNumberArray(vpDict, "/BBox", objects)?.toRawBBox()
            ?: mediaRaw
            ?: return null

        val measureRaw = extractValueAfterKey(vpDict, "/Measure", objects)
            ?: extractValueAfterKey(dictText, "/Measure", objects)
            ?: return null
        val measureDict = resolveIfRef(measureRaw, objects)

        val gpts = extractNumberArray(measureDict, "/GPTS", objects) ?: return null
        if (gpts.size < 6 || gpts.size % 2 != 0) return null

        // Per ISO 32000-1 Table 261: if /LPTS is omitted, default to the 4 viewport corners [0 1 0 0 1 0 1 1]
        val lpts = extractNumberArray(measureDict, "/LPTS", objects)
            ?: if (gpts.size == 8) listOf(0.0, 1.0, 0.0, 0.0, 1.0, 0.0, 1.0, 1.0) else return null
        if (lpts.size != gpts.size) return null

        val pairs = buildList {
            for (i in gpts.indices step 2) {
                val lat = gpts[i]
                val lon = gpts[i + 1]
                val u = lpts[i]
                val v = lpts[i + 1]
                add(LatLon(lat, lon) to rawBBox.mapNormalized(u, v))
            }
        }

        val transform = AffineTransform.fit(pairs) ?: return null
        val gcsRaw = extractValueAfterKey(measureDict, "/GCS", objects)?.let { resolveIfRef(it, objects) }
        val gcsDesc = gcsRaw?.let { g ->
            epsgRegex.find(g)?.groupValues?.get(1)?.let { "EPSG:$it" }
                ?: wktRegex.find(g)?.groupValues?.get(1)?.take(80)
        }

        return GeoPdfInfo(
            standard = GeoPdfStandard.ISO_32000_1,
            mediaBox = mediaBox,
            viewportBBox = rawBBox.toPdfRect(),
            transform = transform,
            geoBounds = Bounds.from(pairs.map { it.first }),
            gcsDescription = gcsDesc,
            controlPointCount = pairs.size,
        )
    }

    private fun parseTerraGo(
        dictText: String,
        objects: Map<Int, String>,
        mediaBox: PdfRect?,
    ): GeoPdfInfo? {
        val lgiRaw = extractValueAfterKey(dictText, "/LGIDict", objects) ?: return null
        val lgiDict = firstDictionaryIn(lgiRaw, objects) ?: resolveIfRef(lgiRaw, objects)

        val regRaw = extractValueAfterKey(lgiDict, "/Registration", objects) ?: return GeoPdfInfo(
            standard = GeoPdfStandard.TERRAGO_LGI,
            mediaBox = mediaBox,
            viewportBBox = mediaBox,
            transform = null,
            geoBounds = null,
            gcsDescription = "TerraGo LGIDict (no /Registration pairs)",
        )

        // TerraGo /Registration is an array of 4-element arrays: [[x y lon lat] ...]
        val nums = numberRegex.findAll(regRaw).mapNotNull { it.value.toDoubleOrNull() }.toList()
        if (nums.size < 12) return null

        val pairs = buildList {
            var i = 0
            while (i + 3 < nums.size) {
                val x = nums[i]
                val y = nums[i + 1]
                val lon = nums[i + 2]
                val lat = nums[i + 3]
                if (lat in -90.0..90.0 && lon in -180.0..180.0) {
                    add(LatLon(lat, lon) to PdfPoint(x, y))
                }
                i += 4
            }
        }
        val transform = AffineTransform.fit(pairs) ?: return null
        return GeoPdfInfo(
            standard = GeoPdfStandard.TERRAGO_LGI,
            mediaBox = mediaBox,
            viewportBBox = mediaBox,
            transform = transform,
            geoBounds = Bounds.from(pairs.map { it.first }),
            gcsDescription = "TerraGo GeoPDF",
            controlPointCount = pairs.size,
        )
    }

    /** Linearly indexes PDF object headers while skipping large binary `stream ... endstream` payloads. */
    private fun indexObjects(text: String): Map<Int, String> {
        val map = HashMap<Int, String>()
        var pos = 0
        while (pos < text.length) {
            val m = objStartRegex.find(text, pos) ?: break
            val id = m.groupValues[1].toIntOrNull()
            val bodyStart = m.range.last + 1
            val endObjIdx = text.indexOf("endobj", bodyStart)
            if (endObjIdx < 0) break

            val streamIdx = text.indexOf("stream", bodyStart)
            val dictEnd = if (streamIdx in bodyStart until endObjIdx) streamIdx else endObjIdx
            if (id != null) {
                map[id] = text.substring(bodyStart, dictEnd)
            }

            if (streamIdx in bodyStart until endObjIdx) {
                val endStreamIdx = text.indexOf("endstream", streamIdx + 6)
                pos = if (endStreamIdx >= 0) {
                    val afterStreamEndObj = text.indexOf("endobj", endStreamIdx + 9)
                    if (afterStreamEndObj >= 0) afterStreamEndObj + 6 else endStreamIdx + 9
                } else {
                    endObjIdx + 6
                }
            } else {
                pos = endObjIdx + 6
            }
        }
        return map
    }

    private fun resolveIfRef(value: String, objects: Map<Int, String>): String {
        val trimmed = value.trim()
        val ref = indirectRefRegex.matchEntire(trimmed) ?: return trimmed
        val id = ref.groupValues[1].toIntOrNull() ?: return trimmed
        return objects[id]?.trim() ?: trimmed
    }

    private fun firstDictionaryIn(raw: String, objects: Map<Int, String>): String? {
        val resolved = resolveIfRef(raw, objects)
        val inner = if (resolved.startsWith("[")) resolved.removePrefix("[").removeSuffix("]").trim() else resolved
        val ref = indirectRefRegex.find(inner)
        if (ref != null && inner.startsWith(ref.value)) {
            val id = ref.groupValues[1].toIntOrNull()
            if (id != null && objects.containsKey(id)) return objects[id]
        }
        val start = inner.indexOf("<<")
        if (start < 0) return null
        return extractBalanced(inner, start, "<<", ">>")
    }

    private fun extractNumberArray(source: String, key: String, objects: Map<Int, String>): List<Double>? {
        val raw = extractValueAfterKey(source, key, objects) ?: return null
        val resolved = resolveIfRef(raw, objects)
        val nums = numberRegex.findAll(resolved).mapNotNull { it.value.toDoubleOrNull() }.toList()
        return nums.ifEmpty { null }
    }

    private fun extractValueAfterKey(source: String, key: String, objects: Map<Int, String>): String? {
        var idx = source.indexOf(key)
        while (idx >= 0) {
            val afterKey = idx + key.length
            // Ensure exact PDF name token boundary (e.g. /VP doesn't match /VPage)
            if (afterKey < source.length && source[afterKey].isLetterOrDigit()) {
                idx = source.indexOf(key, afterKey)
                continue
            }
            var i = afterKey
            while (i < source.length && source[i].isWhitespace()) i++
            if (i >= source.length) return null

            val value = when {
                source.startsWith("<<", i) -> extractBalanced(source, i, "<<", ">>")
                source[i] == '[' -> extractBalanced(source, i, "[", "]")
                else -> {
                    val rest = source.substring(i, minOf(source.length, i + 40))
                    val ref = indirectRefRegex.find(rest)
                    if (ref != null && ref.range.first == 0) {
                        resolveIfRef(ref.value, objects)
                    } else {
                        rest.takeWhile { !it.isWhitespace() && it != '/' && it != '>' && it != ']' }
                    }
                }
            }
            if (!value.isNullOrBlank()) return value
            idx = source.indexOf(key, afterKey)
        }
        return null
    }

    private fun extractBalanced(s: String, start: Int, open: String, close: String): String? {
        var depth = 0
        var inString = false
        var i = start
        while (i < s.length) {
            val ch = s[i]
            if (inString) {
                if (ch == '\\') {
                    i += 2
                    continue
                }
                if (ch == ')') inString = false
                i++
                continue
            }
            if (ch == '(') {
                inString = true
                i++
                continue
            }
            when {
                s.startsWith(open, i) -> {
                    depth++
                    i += open.length
                }
                s.startsWith(close, i) -> {
                    depth--
                    i += close.length
                    if (depth == 0) return s.substring(start, i)
                }
                else -> i++
            }
        }
        return null
    }

    private fun List<Double>.toRawBBox(): RawBBox? {
        if (size < 4) return null
        return if (abs(this[2] - this[0]) > 0.0 && abs(this[3] - this[1]) > 0.0) {
            RawBBox(this[0], this[1], this[2], this[3])
        } else {
            null
        }
    }

    private fun ByteArray.decodeLatin1(): String {
        val chars = CharArray(size)
        for (i in indices) chars[i] = (this[i].toInt() and 0xFF).toChar()
        return chars.concatToString()
    }
}

package com.hikinggear.geo

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

data class LatLon(
    val lat: Double,
    val lon: Double,
)

data class Bounds(
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double,
) {
    val center: LatLon
        get() = LatLon((minLat + maxLat) / 2.0, (minLon + maxLon) / 2.0)

    fun contains(p: LatLon): Boolean =
        p.lat in minLat..maxLat && p.lon in minLon..maxLon

    fun paddedMeters(marginMeters: Double = 1500.0): Bounds {
        val latPad = marginMeters / 111_320.0
        val cosLat = cos(center.lat * PI / 180.0).coerceAtLeast(0.1)
        val lonPad = marginMeters / (111_320.0 * cosLat)
        return Bounds(
            minLat = (minLat - latPad).coerceIn(-85.0511, 85.0511),
            maxLat = (maxLat + latPad).coerceIn(-85.0511, 85.0511),
            minLon = (minLon - lonPad).coerceIn(-180.0, 180.0),
            maxLon = (maxLon + lonPad).coerceIn(-180.0, 180.0),
        )
    }

    companion object {
        fun from(points: Iterable<LatLon>): Bounds? {
            var minLat = Double.POSITIVE_INFINITY
            var maxLat = Double.NEGATIVE_INFINITY
            var minLon = Double.POSITIVE_INFINITY
            var maxLon = Double.NEGATIVE_INFINITY
            var any = false
            for (p in points) {
                any = true
                if (p.lat < minLat) minLat = p.lat
                if (p.lat > maxLat) maxLat = p.lat
                if (p.lon < minLon) minLon = p.lon
                if (p.lon > maxLon) maxLon = p.lon
            }
            return if (any) Bounds(minLat, maxLat, minLon, maxLon) else null
        }
    }
}

fun estimateSlippyTileCount(bounds: Bounds, minZoom: Int = 11, maxZoom: Int = 15): Long {
    var total = 0L
    for (z in minZoom..maxZoom) {
        val n = 1L shl z
        val xMin = floor((bounds.minLon + 180.0) / 360.0 * n).toLong().coerceIn(0L, n - 1)
        val xMax = floor((bounds.maxLon + 180.0) / 360.0 * n).toLong().coerceIn(0L, n - 1)
        val yTop = latToTileY(bounds.maxLat, z, n)
        val yBot = latToTileY(bounds.minLat, z, n)
        val countX = (xMax - xMin + 1).coerceAtLeast(1L)
        val countY = (yBot - yTop + 1).coerceAtLeast(1L)
        total += countX * countY
    }
    return total
}

private fun latToTileY(lat: Double, zoom: Int, n: Long): Long {
    val clamped = lat.coerceIn(-85.0511, 85.0511)
    val rad = clamped * PI / 180.0
    val y = floor((1.0 - ln(tan(rad) + 1.0 / cos(rad)) / PI) / 2.0 * n).toLong()
    return y.coerceIn(0L, n - 1)
}

data class LiveLocation(
    val coord: LatLon,
    val altitudeMeters: Double? = null,
    val accuracyMeters: Double? = null,
)

private const val EARTH_RADIUS_METERS = 6_371_000.0

fun haversineMeters(a: LatLon, b: LatLon): Double {
    val dLat = (b.lat - a.lat) * PI / 180.0
    val dLon = (b.lon - a.lon) * PI / 180.0
    val lat1 = a.lat * PI / 180.0
    val lat2 = b.lat * PI / 180.0
    val h = sin(dLat / 2.0) * sin(dLat / 2.0) +
        cos(lat1) * cos(lat2) * sin(dLon / 2.0) * sin(dLon / 2.0)
    return 2.0 * EARTH_RADIUS_METERS * atan2(sqrt(h), sqrt((1.0 - h).coerceAtLeast(0.0)))
}

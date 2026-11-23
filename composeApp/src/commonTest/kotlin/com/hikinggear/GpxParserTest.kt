package com.hikinggear

import com.hikinggear.geo.Bounds
import com.hikinggear.geo.GpxParser
import com.hikinggear.geo.estimateSlippyTileCount
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GpxParserTest {
    @Test
    fun parsesTracksWaypointsDistanceAndElevation() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1">
              <metadata><name>Semeru Ridge</name></metadata>
              <wpt lat="-8.0156" lon="112.9503"><ele>2100</ele><name>Ranu Pani</name></wpt>
              <trk>
                <name>Main Trail</name>
                <trkseg>
                  <trkpt lat="-8.0156" lon="112.9503"><ele>2100</ele></trkpt>
                  <trkpt lat="-8.0256" lon="112.9503"><ele>2250</ele></trkpt>
                  <trkpt lat="-8.0356" lon="112.9503"><ele>2200</ele></trkpt>
                </trkseg>
              </trk>
            </gpx>
        """.trimIndent()

        val doc = GpxParser.parse(xml)
        assertEquals("Semeru Ridge", doc.name)
        assertEquals(1, doc.tracks.size)
        assertEquals(1, doc.waypoints.size)
        assertEquals("Ranu Pani", doc.waypoints[0].name)

        val track = doc.tracks[0]
        assertEquals(3, track.points.size)
        assertEquals(150.0, track.elevationGainMeters, 0.01)
        assertEquals(50.0, track.elevationLossMeters, 0.01)
        // ~0.02 degrees latitude ≈ 2224 meters
        assertTrue(abs(track.distanceMeters - 2224.0) < 30.0, "Unexpected distance: ${track.distanceMeters}")
        assertNotNull(doc.bounds)
    }

    @Test
    fun paddedBoundsAndTileEstimateWorkForMountainArea() {
        val lawu = Bounds(minLat = -7.67, maxLat = -7.60, minLon = 111.15, maxLon = 111.23)
        val padded = lawu.paddedMeters(1500.0)
        assertTrue(padded.minLat < lawu.minLat && padded.maxLat > lawu.maxLat)
        assertTrue(padded.minLon < lawu.minLon && padded.maxLon > lawu.maxLon)

        val tileCount = estimateSlippyTileCount(padded, minZoom = 11, maxZoom = 15)
        assertTrue(tileCount in 10L..500L, "Unexpected tile count: $tileCount")
    }
}

package com.hikinggear

import com.hikinggear.geo.ActiveHikeSession
import com.hikinggear.geo.GpxParser
import com.hikinggear.geo.LatLon
import com.hikinggear.geo.LiveLocation
import com.hikinggear.geo.RecordingStatus
import com.hikinggear.geo.formatDurationHms
import com.hikinggear.platform.LocalFileStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HikeRecorderTest {
    @Test
    fun startPauseResumePersistAndExportGpxRoundTrip() {
        val t0 = 1_763_812_800_000L // 2025-11-22T12:00:00Z
        var session = ActiveHikeSession.start(t0, sourceFileId = "gpx__Gunung Lawu.gpx")

        // Append two valid points (>5m apart) and one jitter point (<5m, should be ignored)
        session = session.appendLocation(LiveLocation(LatLon(-7.6250, 111.1920), 2100.0, 8.0), t0 + 10_000)!!
        assertNull(session.appendLocation(LiveLocation(LatLon(-7.62501, 111.19201), 2100.5, 8.0), t0 + 15_000))
        session = session.appendLocation(LiveLocation(LatLon(-7.6260, 111.1920), 2150.0, 10.0), t0 + 60_000)!!

        // Pause after 60s -> GPS points ignored while paused, elapsed time stops growing
        session = session.pause(t0 + 60_000)
        assertEquals(RecordingStatus.PAUSED, session.status)
        assertNull(session.appendLocation(LiveLocation(LatLon(-7.6270, 111.1920), 2200.0, 8.0), t0 + 120_000))
        assertEquals(60_000L, session.elapsedMs(t0 + 300_000))

        // Simulate app close & reopen via LocalFileStore persistence
        assertTrue(LocalFileStore.writeActiveSession(session.serialize()))
        val restoredRaw = LocalFileStore.readActiveSession()
        assertNotNull(restoredRaw)
        val restored = ActiveHikeSession.deserialize(restoredRaw)
        assertNotNull(restored)
        assertEquals(RecordingStatus.PAUSED, restored.status)
        assertEquals(2, restored.pointCount)
        assertEquals("gpx__Gunung Lawu.gpx", restored.sourceFileId)
        assertTrue(LocalFileStore.clearActiveSession())

        // Resume creates a new track segment so pause gap doesn't teleport
        val resumed = restored.resume(t0 + 300_000)
            .appendLocation(LiveLocation(LatLon(-7.6280, 111.1930), 2220.0, 8.0), t0 + 330_000)!!
            .appendLocation(LiveLocation(LatLon(-7.6290, 111.1930), 2260.0, 8.0), t0 + 360_000)!!

        assertEquals(120_000L, resumed.elapsedMs(t0 + 360_000))
        assertEquals("00:02:00", formatDurationHms(resumed.elapsedMs(t0 + 360_000)))

        // Export GPX and parse back with GpxParser
        val gpxXml = resumed.toGpxXml("Test Lawu", "Jalur Cemoro Sewu & kabut")
        val parsed = GpxParser.parse(gpxXml)
        assertEquals("Test Lawu", parsed.name)
        assertEquals("Jalur Cemoro Sewu & kabut", parsed.description)
        assertEquals(350_000L, parsed.durationMs)
        assertEquals(2, parsed.tracks.size)
        assertEquals(4, parsed.tracks.sumOf { it.points.size })
        assertTrue(parsed.totalDistanceMeters > 200.0)
        assertNotNull(parsed.bounds)
    }
}

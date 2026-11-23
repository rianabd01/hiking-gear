package com.hikinggear

import com.hikinggear.platform.AppLanguage
import com.hikinggear.platform.LocalFileStore
import com.hikinggear.platform.MapMode
import com.hikinggear.platform.OfflinePackState
import com.hikinggear.platform.SavedFileKind
import com.hikinggear.platform.downloadedModesFor
import com.hikinggear.platform.downloadingModesFor
import com.hikinggear.platform.offlinePackKey
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalFileStoreTest {
    @Test
    fun saveListReadAndDeleteRoundTrip() {
        val payload = "test-gpx-content".encodeToByteArray()
        val saved = LocalFileStore.save("../evil/test-lawu.gpx", SavedFileKind.GPX, payload)
        assertNotNull(saved)
        try {
            assertEquals("test-lawu.gpx", saved.name)
            assertEquals(SavedFileKind.GPX, saved.kind)
            assertTrue(LocalFileStore.list().any { it.id == saved.id })
            assertContentEquals(payload, LocalFileStore.read(saved.id))
            assertNull(LocalFileStore.read("../etc/passwd"))
            assertFalse(LocalFileStore.delete("../etc/passwd"))
            assertTrue(LocalFileStore.rename(saved.id, "Lawu via Cetho"))
            assertFalse(LocalFileStore.rename(saved.id, "   "))
            assertFalse(LocalFileStore.rename("../etc/passwd", "evil"))
            assertEquals("Lawu via Cetho", LocalFileStore.list().first { it.id == saved.id }.name)
        } finally {
            assertTrue(LocalFileStore.delete(saved.id))
        }
        assertNull(LocalFileStore.read(saved.id))

        val activitySaved = LocalFileStore.save("Pendakian-test.gpx", SavedFileKind.ACTIVITY, payload)
        assertNotNull(activitySaved)
        try {
            assertEquals(SavedFileKind.ACTIVITY, activitySaved.kind)
            assertTrue(activitySaved.id.startsWith("act__"))
            assertTrue(LocalFileStore.list().any { it.id == activitySaved.id && it.kind == SavedFileKind.ACTIVITY })
        } finally {
            assertTrue(LocalFileStore.delete(activitySaved.id))
        }
    }

    @Test
    fun offlinePackKeysTrackPerMapMode() {
        val regionId = "gpx__merbabu.gpx"
        assertEquals(regionId, offlinePackKey(regionId, MapMode.STANDARD))
        assertEquals("gpx__merbabu.gpx::topo", offlinePackKey(regionId, MapMode.TOPO))
        assertEquals("gpx__merbabu.gpx::satellite", offlinePackKey(regionId, MapMode.SATELLITE))

        val states = mapOf(
            offlinePackKey(regionId, MapMode.STANDARD) to OfflinePackState.Downloaded(1024L),
            offlinePackKey(regionId, MapMode.TOPO) to OfflinePackState.Downloading(5L, 10L, 512L),
            offlinePackKey(regionId, MapMode.SATELLITE) to OfflinePackState.Downloaded(4096L),
        )
        assertEquals(listOf(MapMode.STANDARD, MapMode.SATELLITE), downloadedModesFor(states, regionId))
        assertEquals(listOf(MapMode.TOPO), downloadingModesFor(states, regionId))
    }

    @Test
    fun appLanguageDefaultsToIndonesianAndPersists() {
        assertEquals(AppLanguage.INDONESIAN, AppLanguage.fromCode(null))
        assertEquals(AppLanguage.INDONESIAN, AppLanguage.fromCode("unknown"))
        assertEquals(AppLanguage.INDONESIAN, AppLanguage.fromCode("id"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromCode("EN"))

        assertEquals("Aktivitas", stringsFor(AppLanguage.INDONESIAN).tabActivity)
        assertEquals("Kompas", stringsFor(AppLanguage.INDONESIAN).tabCompass)
        assertEquals("Activity", stringsFor(AppLanguage.ENGLISH).tabActivity)
        assertEquals("Compass", stringsFor(AppLanguage.ENGLISH).tabCompass)

        val previous = LocalFileStore.readLanguageCode()
        try {
            assertTrue(LocalFileStore.writeLanguageCode(AppLanguage.ENGLISH.code))
            assertEquals(AppLanguage.ENGLISH, AppLanguage.fromCode(LocalFileStore.readLanguageCode()))
            assertTrue(LocalFileStore.writeLanguageCode(AppLanguage.INDONESIAN.code))
            assertEquals(AppLanguage.INDONESIAN, AppLanguage.fromCode(LocalFileStore.readLanguageCode()))
        } finally {
            LocalFileStore.writeLanguageCode(previous ?: AppLanguage.INDONESIAN.code)
        }
    }
}

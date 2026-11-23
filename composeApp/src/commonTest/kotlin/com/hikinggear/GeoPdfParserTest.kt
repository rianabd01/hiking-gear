package com.hikinggear

import com.hikinggear.geo.GeoPdfParser
import com.hikinggear.geo.GeoPdfStandard
import com.hikinggear.geo.LatLon
import com.hikinggear.geo.PdfPoint
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GeoPdfParserTest {
    @Test
    fun parsesIso32000GeospatialPdfAndMapsCoordinatesAccurately() {
        // Synthetic ISO 32000-1 Geospatial PDF covering Mt. Semeru area (-8.10..-8.00, 112.90..113.00)
        val samplePdf = """
            %PDF-1.7
            1 0 obj
            << /Type /Page /MediaBox [0 0 600 800] /VP [2 0 R] >>
            endobj
            2 0 obj
            << /Type /Viewport /BBox [50 100 550 700] /Measure 3 0 R >>
            endobj
            3 0 obj
            <<
              /Type /Measure /Subtype /GEO
              /GCS 4 0 R
              /GPTS [-8.00 112.90 -8.10 112.90 -8.10 113.00 -8.00 113.00]
              /LPTS [0 1 0 0 1 0 1 1]
            >>
            endobj
            4 0 obj
            << /Type /GEOGCS /EPSG 4326 /WKT (GEOGCS["WGS 84"]) >>
            endobj
            %%EOF
        """.trimIndent().encodeToByteArray()

        val info = GeoPdfParser.parse(samplePdf)
        assertEquals(GeoPdfStandard.ISO_32000_1, info.standard)
        assertTrue(info.isGeoreferenced)
        assertEquals("EPSG:4326", info.gcsDescription)
        assertEquals(4, info.controlPointCount)

        // Center of geo area (-8.05, 112.95) should map to center of viewport BBox (300, 400)
        val centerPdf = assertNotNull(info.latLonToPage(LatLon(-8.05, 112.95)))
        assertTrue(abs(centerPdf.x - 300.0) < 0.01, "x=${centerPdf.x}")
        assertTrue(abs(centerPdf.y - 400.0) < 0.01, "y=${centerPdf.y}")

        // Round-trip inverse check
        val back = assertNotNull(info.pageToLatLon(PdfPoint(300.0, 400.0)))
        assertTrue(abs(back.lat - (-8.05)) < 1e-6)
        assertTrue(abs(back.lon - 112.95) < 1e-6)
    }

    @Test
    fun parsesTopDownBBoxAndIgnoresBinaryStreamDecoys() {
        // Real-world export pattern (like Gunung Lawu GeoPDF):
        // 1) Binary image stream contains decoy "/VP" and "/GCS" bytes
        // 2) /BBox is [xLeft yTop xRight yBottom] with yTop > yBottom
        val lawuLikePdf = """
            %PDF-1.4
            1 0 obj
            << /Length 42 >>
            stream
            binaryjunk/VP[not_real]/GCS/garbage
            endstream
            endobj
            140 0 obj
            <</Type/GEOGCS/WKT(GEOGCS["GCS_WGS_1984"])>>
            endobj
            141 0 obj
            <</Type/Measure/Subtype/GEO/GPTS[-7.69988 111.13113 -7.55599 111.13113 -7.55599 111.23006 -7.69988 111.23006]/LPTS[0 1 0 0 1 0 1 1]/GCS 140 0 R>>
            endobj
            142 0 obj
            <<
            /Type /Page
            /MediaBox [0 0 842.25827 1190.23937]
            /Contents 1 0 R
            /VP[<</Type/Viewport/BBox[28.56509 1161.91714 808.22389 28.06803]/Name (Layers)/Measure 141 0 R >>]
            >>
            endobj
            %%EOF
        """.trimIndent().encodeToByteArray()

        val info = GeoPdfParser.parse(lawuLikePdf)
        assertEquals(GeoPdfStandard.ISO_32000_1, info.standard)
        assertTrue(info.isGeoreferenced)

        // South-West (-7.69988, 111.13113) has LPTS (0, 1) -> must land at bottom-left (28.565, 28.068), NOT top-left!
        val sw = assertNotNull(info.latLonToPage(LatLon(-7.69988, 111.13113)))
        assertTrue(abs(sw.x - 28.56509) < 0.01, "sw.x=${sw.x}")
        assertTrue(abs(sw.y - 28.06803) < 0.01, "sw.y=${sw.y}")

        // North-West (-7.55599, 111.13113) has LPTS (0, 0) -> must land at top-left (28.565, 1161.917)
        val nw = assertNotNull(info.latLonToPage(LatLon(-7.55599, 111.13113)))
        assertTrue(abs(nw.x - 28.56509) < 0.01, "nw.x=${nw.x}")
        assertTrue(abs(nw.y - 1161.91714) < 0.01, "nw.y=${nw.y}")
    }

    @Test
    fun detectsPlainNonGeoreferencedPdf() {
        val plainPdf = "%PDF-1.7\n1 0 obj\n<< /Type /Page /MediaBox [0 0 612 792] >>\nendobj\n%%EOF".encodeToByteArray()
        val info = GeoPdfParser.parse(plainPdf)
        assertEquals(GeoPdfStandard.NONE, info.standard)
        assertFalse(info.isGeoreferenced)
        assertNotNull(info.mediaBox)
    }
}

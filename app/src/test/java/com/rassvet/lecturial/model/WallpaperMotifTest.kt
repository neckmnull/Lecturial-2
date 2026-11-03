package com.rassvet.lecturial.model

import com.rassvet.lecturial.data.local.encodeAppDisplayOptions
import com.rassvet.lecturial.data.local.parseAppDisplayOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperMotifTest {
    @Test
    fun `a stroke is thinned, keeps its end and a tap becomes a dot`() {
        val wobble = (0..100).map { MotifPoint(0.1f + it * 0.0001f, 0.5f) } + MotifPoint(0.9f, 0.5f)
        val motif = WallpaperMotif().plusStroke(wobble).plusStroke(listOf(MotifPoint(0.3f, 0.3f)))

        assertEquals(2, motif.strokes.size)
        assertTrue(motif.strokes[0].size < wobble.size)
        assertEquals(MotifPoint(0.9f, 0.5f), motif.strokes[0].last())
        assertEquals(2, motif.strokes[1].size)
        assertFalse(motif.isEmpty)
        assertEquals(1, motif.withoutLastStroke().strokes.size)
        assertTrue(WallpaperMotif().isEmpty)
    }

    @Test
    fun `points outside the square are kept on its edge`() {
        val motif = WallpaperMotif().plusStroke(listOf(MotifPoint(-0.2f, 0.5f), MotifPoint(1.4f, 0.5f)))

        assertEquals(listOf(MotifPoint(0f, 0.5f), MotifPoint(1f, 0.5f)), motif.strokes.single())
    }

    @Test
    fun `a full motif takes no more strokes`() {
        val line = listOf(MotifPoint(0f, 0f), MotifPoint(1f, 1f))
        val full = (1..WallpaperMotif.MAX_STROKES).fold(WallpaperMotif()) { motif, _ -> motif.plusStroke(line) }

        assertTrue(full.isFull)
        assertSame(full, full.plusStroke(line))
    }

    @Test
    fun `a small drawing in a corner is stretched over the whole square`() {
        val corner = WallpaperMotif().plusStroke(listOf(MotifPoint(0.1f, 0.1f), MotifPoint(0.3f, 0.2f)))

        val fitted = corner.fitted().strokes.single()

        // The longer side fills the square; the shorter one is centred.
        assertEquals(0f, fitted.first().x, 1e-5f)
        assertEquals(0.25f, fitted.first().y, 1e-5f)
        assertEquals(1f, fitted.last().x, 1e-5f)
        assertEquals(0.75f, fitted.last().y, 1e-5f)
    }

    @Test
    fun `copies turn like a pinwheel so their inner corners meet`() {
        val inner = MotifPoint(1f, 1f)
        // Top-left, top-right, bottom-right and bottom-left copies of a 2×2 block.
        val placed = listOf(0 to 0, 0 to 1, 1 to 1, 1 to 0).map { (row, column) -> inner.turned(pinwheelTurns(row, column)) }

        assertEquals(listOf(MotifPoint(1f, 1f), MotifPoint(0f, 1f), MotifPoint(0f, 0f), MotifPoint(1f, 0f)), placed)
        assertEquals(pinwheelTurns(0, 0), pinwheelTurns(2, 2))
        assertEquals(MotifPoint(0.2f, 0.7f), MotifPoint(0.2f, 0.7f).turned(4))
    }

    @Test
    fun `the motif survives saving the display options`() {
        val motif = WallpaperMotif()
            .plusStroke(listOf(MotifPoint(0.1f, 0.2f), MotifPoint(0.5f, 0.8f), MotifPoint(0.9f, 0.2f)))
            .plusStroke(listOf(MotifPoint(0.5f, 0.5f)))
        val options = AppDisplayOptions(backgroundStyle = AppBackgroundStyle.Drawn, backgroundMotif = motif)

        val restored = parseAppDisplayOptions(encodeAppDisplayOptions(options))

        assertEquals(AppBackgroundStyle.Drawn, restored.backgroundStyle)
        assertEquals(encodeMotif(motif), encodeMotif(restored.backgroundMotif))
        assertTrue(parseMotif("garbage;1,2 x").isEmpty)
        assertTrue(parseAppDisplayOptions("""{"backgroundStyle":"drawn"}""").backgroundMotif.isEmpty)
    }
}

package com.rassvet.lecturial.model

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperPatternsTest {
    private val tile = 192

    @Test
    fun `every pattern draws a moderate amount of lines`() {
        LinePattern.entries.forEach { pattern ->
            val field = if (pattern == LinePattern.Maze) {
                WallpaperPatterns.maze(density = 0.6f, seed = 7, steps = 2500)
            } else {
                WallpaperPatterns.field(pattern, density = 1f, seed = 7)
            }
            val mask = WallpaperPatterns.render(field, tile, lineWidthPx = 2f)
            val covered = mask.count { (it.toInt() and 0xFF) > 128 }.toFloat() / mask.size
            assertTrue("$pattern covers $covered", covered in 0.03f..0.6f)
            dump(pattern.name, mask)
            dumpGrowth(pattern.name, mask, WallpaperPatterns.renderArrival(field, tile))
        }
    }

    @Test
    fun `tiles are seamless`() {
        LinePattern.entries.filter { it != LinePattern.Maze }.forEach { pattern ->
            val field = WallpaperPatterns.field(pattern, density = 1f, seed = 3)
            val n = field.n
            // The field wraps: the last column continues smoothly into the first one.
            val jump = (0 until n).maxOf { y -> kotlin.math.abs(field.values[y * n + n - 1] - field.values[y * n]) }
            val typical = (0 until n).maxOf { y -> kotlin.math.abs(field.values[y * n + 1] - field.values[y * n]) }
            assertTrue("$pattern wraps with jump $jump vs step $typical", jump <= typical * 1.6f + 1e-4f)
        }
    }

    @Test
    fun `lines are drawn gradually, not all at once`() {
        LinePattern.entries.forEach { pattern ->
            val field = if (pattern == LinePattern.Maze) {
                WallpaperPatterns.maze(density = 0.6f, seed = 7, steps = 2500)
            } else {
                WallpaperPatterns.field(pattern, density = 1f, seed = 7)
            }
            val mask = WallpaperPatterns.render(field, tile, lineWidthPx = 2f)
            val order = WallpaperPatterns.renderArrival(field, tile)
            fun drawnAt(progress: Float) = mask.indices.count { i ->
                (mask[i].toInt() and 0xFF) > 128 && (order[i].toInt() and 0xFF) <= progress * 255
            }
            val total = drawnAt(1f).toFloat()
            assertTrue("$pattern draws early", drawnAt(0.15f) / total < 0.45f)
            assertTrue("$pattern draws by the middle", drawnAt(0.6f) / total > 0.25f)
        }
    }

    @Test
    fun `the same seed gives the same pattern`() {
        val first = WallpaperPatterns.render(WallpaperPatterns.field(LinePattern.Fingerprint, 1f, seed = 11), 64, 2f)
        val second = WallpaperPatterns.render(WallpaperPatterns.field(LinePattern.Fingerprint, 1f, seed = 11), 64, 2f)
        assertArrayEquals(first, second)
    }

    private fun dumpGrowth(name: String, mask: ByteArray, order: ByteArray) {
        val dir = System.getenv("LECTURIAL_PATTERN_DUMP") ?: return
        val frames = listOf(0.25f, 0.5f, 0.75f)
        val image = BufferedImage(tile * frames.size, tile, BufferedImage.TYPE_INT_RGB)
        frames.forEachIndexed { frame, progress ->
            for (y in 0 until tile) for (x in 0 until tile) {
                val i = y * tile + x
                val shown = (order[i].toInt() and 0xFF) <= progress * 255
                val shade = if (shown) 255 - (mask[i].toInt() and 0xFF) else 255
                image.setRGB(frame * tile + x, y, (shade shl 16) or (shade shl 8) or shade)
            }
        }
        ImageIO.write(image, "png", File(dir, "${name}_growth.png"))
    }

    /** Set LECTURIAL_PATTERN_DUMP to a folder to look at the generated tiles. */
    private fun dump(name: String, mask: ByteArray) {
        val dir = System.getenv("LECTURIAL_PATTERN_DUMP") ?: return
        val image = BufferedImage(tile, tile, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until tile) for (x in 0 until tile) {
            val shade = 255 - (mask[y * tile + x].toInt() and 0xFF)
            image.setRGB(x, y, (shade shl 16) or (shade shl 8) or shade)
        }
        ImageIO.write(image, "png", File(dir, "$name.png"))
    }
}

package com.rassvet.lecturial.data.local

import android.content.Context
import com.rassvet.lecturial.model.LinePattern
import com.rassvet.lecturial.model.PatternField
import com.rassvet.lecturial.model.WallpaperPatterns
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/**
 * Line wallpaper fields shared by the app background and the widget. The labyrinth is a
 * slow simulation, so its field is kept on disk; the other patterns are quick formulas.
 * Blocking: call it off the main thread.
 */
internal object WallpaperFieldCache {
    private const val SEED = 2026
    private val lock = Any()

    fun field(context: Context, pattern: LinePattern, density: Float): PatternField {
        if (pattern != LinePattern.Maze) return WallpaperPatterns.field(pattern, density, SEED)
        // The app and the widget can ask at the same time; only one of them simulates.
        synchronized(lock) {
            val file = File(
                context.cacheDir,
                "wallpaper/maze-v2-${(density * 100).toInt()}-$SEED-${WallpaperPatterns.MAZE_STEPS}.bin",
            )
            read(file)?.let { return it }
            val field = WallpaperPatterns.field(pattern, density, SEED)
            write(file, field)
            return field
        }
    }

    private fun read(file: File): PatternField? = runCatching {
        DataInputStream(file.inputStream().buffered()).use { input ->
            val n = input.readInt()
            val level = input.readFloat()
            val values = FloatArray(n * n) { input.readFloat() }
            val arrival = FloatArray(n * n) { input.readFloat() }
            PatternField(n, values, level, spacing = null, arrival = arrival)
        }
    }.getOrNull()

    private fun write(file: File, field: PatternField) {
        runCatching {
            file.parentFile?.mkdirs()
            // Written aside and renamed, so a reader never sees half a file.
            val partial = File(file.parentFile, file.name + ".part")
            DataOutputStream(partial.outputStream().buffered()).use { output ->
                output.writeInt(field.n)
                output.writeFloat(field.level)
                field.values.forEach(output::writeFloat)
                field.arrival.forEach(output::writeFloat)
            }
            partial.renameTo(file)
        }
    }
}

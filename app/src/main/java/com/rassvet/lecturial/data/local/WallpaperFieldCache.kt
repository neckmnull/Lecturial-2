package com.rassvet.lecturial.data.local

import android.content.Context
import com.rassvet.lecturial.model.LinePattern
import com.rassvet.lecturial.model.PatternField
import com.rassvet.lecturial.model.WallpaperPatterns
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.SecureRandom

/**
 * Line wallpaper fields shared by the app background and the widget. The labyrinth is a
 * slow simulation, so its field is kept on disk; the other patterns are quick formulas.
 * Blocking: call it off the main thread.
 */
internal object WallpaperFieldCache {
    private val lock = Any()

    fun field(context: Context, pattern: LinePattern, density: Float): PatternField {
        val seed = seed(context)
        if (pattern != LinePattern.Maze) return WallpaperPatterns.field(pattern, density, seed)
        // The app and the widget can ask at the same time; only one of them simulates.
        synchronized(lock) {
            val file = File(
                context.cacheDir,
                "wallpaper/maze-v3-${(density * 100).toInt()}-$seed-${WallpaperPatterns.MAZE_STEPS}.bin",
            )
            read(file)?.let { return it }
            val field = WallpaperPatterns.field(pattern, density, seed)
            write(file, field)
            return field
        }
    }

    /**
     * This install's pattern seed, picked at random the first time: every phone draws its
     * own labyrinth, fingerprint, relief and waves, and keeps them from then on.
     */
    fun seed(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        synchronized(lock) {
            if (prefs.contains(KEY_SEED)) return prefs.getInt(KEY_SEED, 0)
            val seed = SecureRandom().nextInt()
            prefs.edit().putInt(KEY_SEED, seed).commit()
            return seed
        }
    }

    private const val PREFS = "wallpaper"
    private const val KEY_SEED = "pattern_seed"

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

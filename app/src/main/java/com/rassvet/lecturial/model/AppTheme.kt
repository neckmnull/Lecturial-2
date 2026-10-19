package com.rassvet.lecturial.model

enum class AppTheme(val storageId: String) {
    /** Material You: colours taken from the wallpaper, Android 12 and newer. */
    Dynamic("dynamic"),
    Sirius("sirius"),
    Ocean("ocean"),
    Forest("forest"),
    Sunset("sunset"),
    Amethyst("amethyst"),
    Burgundy("sakura"),
    Amber("amber"),
    Graphite("graphite"),
    Neon("neon"),
    Fuchsia("fuchsia"),
    Electric("electric"),
    Custom("custom");

    companion object {
        val default = Sirius

        fun fromStorageId(value: String?): AppTheme =
            entries.firstOrNull { it.storageId == value } ?: default
    }
}
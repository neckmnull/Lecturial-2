package com.rassvet.lecturial.model

enum class AppTheme(val storageId: String) {
    Sirius("sirius"),
    Ocean("ocean"),
    Forest("forest"),
    Sunset("sunset"),
    Amethyst("amethyst"),
    Sakura("sakura"),
    Amber("amber"),
    Graphite("graphite"),
    Custom("custom");

    companion object {
        val default = Sirius

        fun fromStorageId(value: String?): AppTheme =
            entries.firstOrNull { it.storageId == value } ?: default
    }
}
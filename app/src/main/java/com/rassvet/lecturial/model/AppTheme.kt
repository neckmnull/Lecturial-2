package com.rassvet.lecturial.model

enum class AppTheme(val storageId: String) {
    Sirius("sirius"),
    Ocean("ocean"),
    Forest("forest"),
    Sunset("sunset");

    companion object {
        val default = Sirius

        fun fromStorageId(value: String?): AppTheme =
            entries.firstOrNull { it.storageId == value } ?: default
    }
}
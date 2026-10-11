package com.rassvet.lecturial.model

sealed interface AppUpdateState {
    data object Idle : AppUpdateState
    data object Checking : AppUpdateState
    data class UpToDate(val latestVersion: String) : AppUpdateState
    data class Available(val version: String, val releaseUrl: String) : AppUpdateState
    data class Error(val message: String) : AppUpdateState
}
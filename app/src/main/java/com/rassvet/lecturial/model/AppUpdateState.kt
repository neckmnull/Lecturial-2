package com.rassvet.lecturial.model

sealed interface AppUpdateState {
    data object Idle : AppUpdateState
    data object Checking : AppUpdateState
    data class UpToDate(val latestVersion: String) : AppUpdateState
    data class Available(
        val version: String,
        val releaseUrl: String,
        val apkUrl: String,
        val apkName: String,
    ) : AppUpdateState
    data class Downloading(
        val update: Available,
        val progressPercent: Int?,
    ) : AppUpdateState
    data class ReadyToInstall(
        val version: String,
        val releaseUrl: String,
        val apkPath: String,
    ) : AppUpdateState
    data class DownloadError(
        val update: Available,
        val message: String,
    ) : AppUpdateState
    data class InstallationError(
        val update: ReadyToInstall,
        val message: String,
    ) : AppUpdateState
    data class Error(val message: String) : AppUpdateState
}
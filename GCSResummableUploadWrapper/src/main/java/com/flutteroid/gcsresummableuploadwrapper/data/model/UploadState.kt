package com.flutteroid.gcsresummableuploadwrapper.data.model

sealed class UploadState {
    object Idle : UploadState()
    object Uploading : UploadState()
    object Paused : UploadState()
    object Aborted : UploadState()
    object Completed : UploadState()
    data class Failed(var message: String) : UploadState()
    object Offline : UploadState()
}
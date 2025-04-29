package com.flutteroid.gcsresummableuploadwrapper.data

interface UploadStateCallback {
    fun onProgress(progress: Long)
    fun onSuccess()
    fun onFailure(error: String)
}
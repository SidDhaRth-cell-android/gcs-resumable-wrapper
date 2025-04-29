package com.flutteroid.gcsresummableuploadwrapper.internal.utils

import android.util.Log

var isLoggingEnabled = false

internal object Logger {


    private const val DEFAULT_TAG = "GCSResumableUploadWrapper"

    fun enableLogging(enable: Boolean) {
        isLoggingEnabled = enable
    }

    fun d(message: String?, tag: String = DEFAULT_TAG) {
        if (isLoggingEnabled) {
            Log.d(tag, message.orEmpty())
        }
    }

    fun e(message: String?, throwable: Throwable? = null, tag: String = DEFAULT_TAG) {
        if (isLoggingEnabled) {
            Log.e(tag, message, throwable)
        }
    }

    fun i(message: String?, tag: String = DEFAULT_TAG) {
        if (isLoggingEnabled) {
            Log.i(tag, message.orEmpty())
        }
    }

    fun w(message: String?, tag: String = DEFAULT_TAG) {
        if (isLoggingEnabled) {
            Log.w(tag, message.orEmpty())
        }
    }
}
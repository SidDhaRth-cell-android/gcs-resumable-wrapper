package com.flutteroid.gcsresummableuploadwrapper.internal.upload

import android.content.Context
import com.flutteroid.gcsresummableuploadwrapper.api.ApiClient
import com.flutteroid.gcsresummableuploadwrapper.data.UploadStateCallback
import com.flutteroid.gcsresummableuploadwrapper.data.model.CancelReason
import com.flutteroid.gcsresummableuploadwrapper.data.model.ChunkSession
import com.flutteroid.gcsresummableuploadwrapper.data.model.UploadState
import com.flutteroid.gcsresummableuploadwrapper.internal.utils.Logger
import com.flutteroid.gcsresummableuploadwrapper.internal.utils.NetworkHandler
import com.flutteroid.gcsresummableuploadwrapper.internal.utils.StreamingFileRequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.ceil

// UploadManager.kt

class UploadManager private constructor(
    val context: Context,
    private val file: File,
    private val accessToken: String,
    private val bucketName: String,
    private val chunkSize: Int,
    private val callback: UploadStateCallback,
    private val enableLogging: Boolean
) {
    @Volatile
    private var chunkSession = ChunkSession()
    private val _currentState = AtomicReference<UploadState>(UploadState.Idle)
    val currentState: UploadState
        get() = _currentState.get()
    private var lastNonOfflineState: UploadState = UploadState.Idle
    private var uploadCall: Call<Unit>? = null

    @Volatile
    private var lastCancelReason: CancelReason = CancelReason.NONE
    private var networkHandler: NetworkHandler = NetworkHandler.getInstance(context)
    private val apiClient by lazy {
        ApiClient.create()
    }

    fun startUpload() {
        try {
            handleNetworkState()
            GcsSessionCreator.createSessionURI(
                bucketId = bucketName,
                fileName = file.name,
                accessToken = accessToken,
                onCompleted = { success, sessionUriOrErrorMessage ->
                    if (success) {
                        chunkSession.sessionURI = sessionUriOrErrorMessage
                        prepareForUploading()
                    } else {
                        callback.onFailure(sessionUriOrErrorMessage.orEmpty())
                    }
                })
        } catch (ex: Exception) {
            Logger.e(ex.message, ex.cause)
            callback.onFailure(ex.message.orEmpty())
        }
    }

    @Synchronized
    private fun setState(newState: UploadState) {
        val oldState = _currentState.get()
        if (oldState == newState) {
            return
        }
        _currentState.set(newState)
        if (newState !is UploadState.Offline) {
            lastNonOfflineState = newState
        }
    }

    private fun prepareForUploading() {
        // Ensure thread-safe state handling for the initial upload state transition
        if (_currentState.get() != UploadState.Idle) {
            setState(UploadState.Uploading)
            // If the state is not "Initializing", return early to prevent re-entry.
            return
        }

        chunkSession.startTime = System.currentTimeMillis()
        chunkSession.fileSize = file.length()
        chunkSession.chunkSize = chunkSize
        chunkSession.totalChunks =
            ceil(chunkSession.fileSize.toDouble() / chunkSession.chunkSize.toDouble()).toInt()
        Logger.i("Total Chunks: ${chunkSession.totalChunks}")
        setState(UploadState.Uploading)
        if (chunkSession.totalChunks == 1) {
            chunkSession.isOnlyChunk = true
            uploadSingleChunk()
        } else {
            beginUploading()
        }
    }

    private fun uploadSingleChunk() {
        try {
            val requestBody = file
                .asRequestBody("application/octet-stream".toMediaTypeOrNull())
            uploadCall = apiClient.uploadSingleChunk(
                chunkSession.sessionURI,
                chunkSession.fileSize,
                requestBody
            )
            uploadCall?.enqueue(object : Callback<Unit> {
                override fun onResponse(
                    call: Call<Unit?>,
                    response: Response<Unit?>
                ) {
                    if (response.isSuccessful) {
                        Logger.i("Single Chunk Upload Successful")
                        callback.onSuccess()
                    } else {
                        Logger.e("Single Chunk Upload Failure. Cause: ${response.message()}")
                        callback.onFailure(response.message())
                    }
                }

                override fun onFailure(call: Call<Unit?>, throwable: Throwable) {
                    callback.onFailure(throwable.message.orEmpty())
                }

            })
        } catch (ex: Exception) {
            callback.onFailure(ex.message.orEmpty())
        }
    }

    private fun beginUploading(uploadedBytes: Long? = null) {
        try {
            // Ensure that upload isn't paused, aborted, or offline before proceeding
            if (currentState is UploadState.Paused
                || currentState is UploadState.Aborted
                || currentState is UploadState.Offline
            ) {
                return
            }
            val fileLength = chunkSession.fileSize
            uploadedBytes?.let {
                chunkSession.nextChunkRangeStart = uploadedBytes
            }
            val start = chunkSession.nextChunkRangeStart
            val end = minOf(
                (chunkSession.chunkSize * (chunkSession.chunkOffset + 1)).toLong(),
                fileLength
            )
            val totalSize = file.length()
            val requestBody = StreamingFileRequestBody(
                file = file,
                mediaType = "application/octet-stream".toMediaType(),
                startByte = start,
                endByte = end,
                onProgress = { written, total ->
                    val currentUploaded = start + written
                    val progressCount = (currentUploaded * 100) / chunkSession.fileSize
                    callback.onProgress(progressCount)

                }
            )
            val contentRange = "bytes $start-${end - 1}/$totalSize"
            uploadCall = apiClient.uploadChunk(
                chunkSession.sessionURI,
                chunkSession.chunkSize,
                contentRange,
                requestBody
            )
            uploadCall?.enqueue(object : Callback<Unit> {
                override fun onResponse(
                    p0: Call<Unit>,
                    response: Response<Unit>
                ) {
                    Logger.i("Chunk ${chunkSession.chunkOffset} has been uploaded")
                    if (response.code() == 308) {
                        chunkSession.chunkOffset++
                        Logger.i("Chunk ${chunkSession.chunkOffset} added to queue")
                        chunkSession.successiveChunkCount++
                        chunkSession.nextChunkRangeStart = end
                        beginUploading()
                    } else if (response.isSuccessful) {
                        callback.onSuccess()
                    } else {
                        if (networkHandler.isConnectedToInternet) {
                            checkForUploadStatus()
                        } else {
                            callback.onFailure("Network Lost, Waiting For Network To Available")
                        }

                    }
                }

                override fun onFailure(
                    p0: Call<Unit>,
                    p1: Throwable
                ) {
                    when (lastCancelReason) {
                        CancelReason.USER_PAUSED,
                        CancelReason.INTERNET_LOST -> {
                            // Ignore — expected cancellation
                            lastCancelReason = CancelReason.NONE
                            return
                        }

                        else -> {
                            callback.onFailure(p1.message.orEmpty())
                        }
                    }
                }
            }
            )
        } catch (ex: java.lang.Exception) {
            when (lastCancelReason) {
                CancelReason.USER_PAUSED,
                CancelReason.INTERNET_LOST -> {
                    // Ignore — expected cancellation
                    lastCancelReason = CancelReason.NONE
                    return
                }

                else -> {
                    if (networkHandler.isConnectedToInternet) {
                        checkForUploadStatus()
                    } else {
                        callback.onFailure("Network Lost, Waiting For Network To Available")
                    }
                }
            }

        }
    }

    fun isUploading(): Boolean {
        return _currentState.get() == UploadState.Uploading
    }

    fun abort() {
        Logger.i("Upload Abort")
        if (_currentState == UploadState.Uploading
            || _currentState == UploadState.Paused
            || _currentState == UploadState.Offline
        ) {
            uploadCall?.cancel()
            setState(UploadState.Aborted)
            unregister()
        }
    }

    private fun unregister() {
        Logger.i("SDK Unregister Successfully")
        uploadCall?.cancel()
        chunkSession = ChunkSession()
        networkHandler.removeListeners()
        setState(UploadState.Idle)
    }

    fun pause() {
        Logger.i("Upload Paused")
        if (_currentState.get() == UploadState.Uploading) {
            setState(UploadState.Paused)
            lastCancelReason = CancelReason.USER_PAUSED
            uploadCall?.cancel()
        }
    }

    fun resume() {
        Logger.i("Upload Resume")
        val notComplete = chunkSession.successiveChunkCount != chunkSession.totalChunks
        if (_currentState.get() == UploadState.Paused && notComplete) {
            setState(UploadState.Uploading)
            checkForUploadStatus()
        }
    }


    private fun checkForUploadStatus() {
        val objectSize = file.length()
        val contentRange = "bytes */$objectSize"
        val apiCall = apiClient.uploadStatus(
            sessionUri = chunkSession.sessionURI,
            contentRange = contentRange
        )

        apiCall.enqueue(object : Callback<Unit> {
            override fun onResponse(
                p0: Call<Unit>,
                response: Response<Unit>
            ) {
                if (response.code() == 308) {
                    val uploadedBytes = response.headers()["Range"]
                        ?.substringAfter("bytes=0-")
                        ?.toLongOrNull()
                        ?.plus(1) ?: 0L
                    beginUploading(uploadedBytes)
                } else if (response.isSuccessful) {
                    callback.onSuccess()
                } else {
                    if (networkHandler.isConnectedToInternet) {
                        checkForUploadStatus()
                    } else {
                        callback.onFailure("Network Lost, Waiting For Network To Available")
                    }
                }
            }

            override fun onFailure(
                p0: Call<Unit>,
                p1: Throwable
            ) {
                callback.onFailure(p1.message.orEmpty())
            }
        })
    }

    private fun handleNetworkState() {
        networkHandler
            .setNetworkListener(object : NetworkHandler.NetworkListener {
                override fun onAvailable() {
                    Logger.i("Network Available")
                    val current = _currentState.get()
                    if (current is UploadState.Paused || current is UploadState.Aborted) {
                        return // Respect user choice
                    }

                    if (current !is UploadState.Offline) {
                        return // Already resumed or not in a resumable state
                    }
                    when (lastNonOfflineState) {
                        is UploadState.Paused -> {
                            setState(UploadState.Paused)
                        }

                        is UploadState.Uploading -> {
                            setState(UploadState.Uploading)
                            if (networkHandler.isConnectedToInternet) {
                                checkForUploadStatus()
                            } else {
                                callback.onFailure("Network Lost, Waiting For Network To Available")
                            }

                        }

                        else -> {
                            setState(UploadState.Uploading)
                            if (networkHandler.isConnectedToInternet) {
                                checkForUploadStatus()
                            } else {
                                callback.onFailure("Network Lost, Waiting For Network To Available")
                            }
                        }
                    }
                }

                override fun onLost() {
                    Logger.i("Network Lost")
                    if (_currentState.get() is UploadState.Uploading ||
                        _currentState.get() is UploadState.Paused
                    ) {
                        setState(UploadState.Offline)
                        lastCancelReason = CancelReason.INTERNET_LOST
                    }
                }
            })
    }

    class Builder(private val context: Context) {
        private var file: File? = null
        private var accessToken: String? = null
        private var bucketName: String? = null
        private var chunkSize: Int = DEFAULT_CHUNK_SIZE
        private var callback: UploadStateCallback? = null
        private var enableLogging: Boolean = false

        fun setFile(file: File) = apply {
            this.file = file
        }

        fun setAccessToken(accessToken: String) = apply {
            this.accessToken = accessToken
        }

        fun setBucketName(bucketName: String) = apply {
            this.bucketName = bucketName
        }

        fun setChunkSize(chunkSizeInBytes: Int) = apply {
            require(chunkSizeInBytes >= MIN_CHUNK_SIZE) {
                "Chunk size must be at least ${MIN_CHUNK_SIZE / (1024 * 1024)} MB."
            }
            this.chunkSize = chunkSizeInBytes
        }

        fun setCallback(callback: UploadStateCallback) = apply {
            this.callback = callback
        }

        fun enableLogging(enable: Boolean) = apply {
            this.enableLogging = enable
        }

        fun build(): UploadManager {
            val file = this.file ?: throw IllegalArgumentException("File must be provided.")
            val accessToken = this.accessToken?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("Access token must not be blank.")
            val bucketName = this.bucketName?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("Bucket name must not be blank.")
            val callback = this.callback
                ?: throw IllegalArgumentException("UploadStateCallback must be provided.")
            Logger.enableLogging(enableLogging)
            Logger.i("GCSResumable Uploads Initialized")
            Logger.i("File: $file")
            Logger.i("Access Token: $accessToken")
            Logger.i("Bucket Name: $bucketName")
            Logger.i("Chunk Size: $chunkSize")
            return UploadManager(
                context = context,
                file = file,
                accessToken = accessToken,
                bucketName = bucketName,
                chunkSize = chunkSize,
                callback = callback,
                enableLogging = enableLogging
            )
        }

        companion object {
            private const val MIN_CHUNK_SIZE = 15 * 1024 * 1024 // 15MB
            private const val DEFAULT_CHUNK_SIZE = 30 * 1024 * 1024 // 30MB
        }
    }
}
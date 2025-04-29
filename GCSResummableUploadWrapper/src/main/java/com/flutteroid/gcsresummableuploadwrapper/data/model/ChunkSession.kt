package com.flutteroid.gcsresummableuploadwrapper.data.model

data class ChunkSession(
    var totalChunks: Int = 0,
    var chunkSize: Int = 15 * 1024 * 1024,
    var isOnlyChunk: Boolean = false,
    var objectName: String? = null,
    var chunkOffset: Int = 0,
    var fileSize: Long = 0L,
    var nextChunkRangeStart: Long = 0,
    var successiveChunkCount: Int = 0,
    var sessionURI: String? = null,
    var startTime: Long? = null
)
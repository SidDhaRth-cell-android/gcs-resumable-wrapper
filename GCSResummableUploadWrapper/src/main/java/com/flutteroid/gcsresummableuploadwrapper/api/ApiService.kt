package com.flutteroid.gcsresummableuploadwrapper.api

import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

interface ApiService {


    @POST("upload/storage/v1/b/{bucket}/o?uploadType=resumable")
    fun createSessionURI(
        @Path("bucket") bucketName: String,
        @Header("Authorization") authHeader: String,
        @Query("name") objectName: String
    ): Call<Unit>

    @PUT
    fun uploadChunk(
        @Url sessionUri: String?,
        @Header("Content-Length") contentLength: Int,
        @Header("Content-Range") contentRange: String,
        @Body body: RequestBody
    ): Call<Unit>

    @PUT
    fun uploadStatus(
        @Url sessionUri: String?,
        @Header("Content-Length") contentLength: Long = 0,
        @Header("Content-Range") contentRange: String,
    ): Call<Unit>

    @PUT
    fun uploadSingleChunk(
        @Url sessionUri: String?,
        @Header("Content-Length") contentLength: Long,
        @Body body: RequestBody
    ): Call<Unit>
}
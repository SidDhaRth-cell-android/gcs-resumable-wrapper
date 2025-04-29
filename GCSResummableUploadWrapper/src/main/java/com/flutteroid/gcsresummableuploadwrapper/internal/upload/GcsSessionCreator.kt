package com.flutteroid.gcsresummableuploadwrapper.internal.upload

import com.flutteroid.gcsresummableuploadwrapper.api.ApiClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

object GcsSessionCreator {

    private val apiClient by lazy {
        ApiClient.create()
    }

    fun createSessionURI(
        bucketId: String,
        fileName: String,
        accessToken: String,
        onCompleted: (Boolean, String?) -> Unit
    ) {
        val auth = "Bearer $accessToken"
        try {
            val apiCall = apiClient.createSessionURI(bucketId, auth, fileName)
            apiCall.enqueue(object : Callback<Unit> {
                override fun onResponse(
                    call: Call<Unit>,
                    response: Response<Unit>
                ) {
                    if (response.isSuccessful) {
                        val sessionURI = response.headers()["Location"]
                        onCompleted.invoke(true, sessionURI)
                    } else {
                        onCompleted.invoke(false, response.message())
                    }
                }

                override fun onFailure(
                    p0: Call<Unit>,
                    p1: Throwable
                ) {
                    onCompleted.invoke(false, p1.message)
                }
            })

        } catch (ex: Exception) {
            onCompleted.invoke(false, ex.message)
        }
    }

}
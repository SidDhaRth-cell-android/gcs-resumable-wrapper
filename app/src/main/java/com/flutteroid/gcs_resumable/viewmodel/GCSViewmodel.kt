package com.flutteroid.gcs_resumable.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.auth.oauth2.GoogleCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream

class GCSViewmodel : ViewModel() {

    var savedToken: String? = null
        private set
    var file: File? = null

    fun getAccessToken(inputStream: InputStream) {
        viewModelScope.launch(Dispatchers.IO) {
            val credentials = GoogleCredentials.fromStream(inputStream)
                .createScoped("https://www.googleapis.com/auth/devstorage.read_write")
            credentials.refreshIfExpired()
            val token = credentials.accessToken.tokenValue
            savedToken = token
        }
    }
}
package com.flutteroid.gcs_resumable

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.flutteroid.gcs_resumable.databinding.ActivityMainBinding
import com.flutteroid.gcs_resumable.viewmodel.GCSViewmodel
import com.flutteroid.gcsresummableuploadwrapper.data.UploadStateCallback
import com.flutteroid.gcsresummableuploadwrapper.internal.upload.UploadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.util.logging.Level

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var gcsViewmodel: GCSViewmodel
    private lateinit var uploadManager: UploadManager

    private val galleryContract =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                handleVideoUri(it)
            }
        }


    private fun handleVideoUri(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            val file = getFileFromUri(uri)
            withContext(Dispatchers.Main) {
                callToGCSUploader(file)
            }
        }
    }

    fun getFileFromUri(uri: Uri): File? {
        try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val fileName = getFileName(this, uri)
            val tempFile = File(cacheDir, fileName)
            tempFile.outputStream().use { fileOut ->
                inputStream.copyTo(fileOut)
            }
            return tempFile
        } catch (e: FileNotFoundException) {
            Log.e(Level.SEVERE.name, e.message.toString())
            return null
        }
    }

    fun getFileName(context: Context, uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    result = it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != -1 && cut != null) {
                result = result.substring(cut + 1)
            }
        }
        return result ?: "temp_file"
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        gcsViewmodel = ViewModelProvider(this)[GCSViewmodel::class.java]
        fetchAccessToken()
        binding.uploadVideo.setOnClickListener {
            galleryContract.launch("video/*")
        }

        binding.pauseUpload.setOnClickListener {
            if (uploadManager.isUploading()) {
                uploadManager.pause()
                binding.pauseUpload.text = "Resume"
            } else {
                uploadManager.resume()
                binding.pauseUpload.text = "Pause"
            }
        }

    }

    private fun fetchAccessToken() {
        /**
         * Add Your Service Account JSON to raw folder and get the token like this
         */
        // val inputStream = resources.openRawResource(R.raw.your_service_account_json)
        // gcsViewmodel.getAccessToken(inputStream)
    }

    private fun callToGCSUploader(file: File?) {
        file?.let {
            uploadManager = UploadManager.Builder(this)
                .setFile(it)
                .setAccessToken(gcsViewmodel.savedToken.orEmpty())
                .setBucketName("YOUR-BUCKET-ID")
                .setCallback(object : UploadStateCallback {
                    override fun onProgress(progress: Long) {
                        runOnUiThread {
                            binding.progressIndicator.progress = progress.toInt()
                        }
                    }

                    override fun onSuccess() {
                        Log.d("TAG", "onSuccess: =============> ")
                    }

                    override fun onFailure(error: String) {
                        Log.e("TAG", "onFailure: =============> $error")
                    }

                }).enableLogging(true)
                .build()
            uploadManager.startUpload();
        }

    }
}
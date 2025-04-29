# 📦 GCS Resumable Upload Wrapper for Android

This library provides a simple and efficient wrapper for performing **resumable uploads to Google Cloud Storage (GCS)** in Android using a session URI. It supports large file uploads, automatic retry, progress callbacks, and easy integration with your app’s UI or business logic.

## ✨ Features

- ✅ Google Cloud Storage (GCS) **resumable upload** support  
- ✅ Supports large file uploads in **chunks** (Default Chunk Size: 30MBs)
- ✅ Easy integration with just a few lines of code  
- ✅ Upload progress tracking  
- ✅ Java/Kotlin compatible  

---

## 🛠️ Installation

Add the GitHub Packages Maven repository to your `setting.gradle.kts`:
```groovy
maven(url ="https://maven.pkg.github.com/SidDhaRth-cell-android/gcs-resumable-wrapper") {  
  credentials {  
	  username = "YOUR_GITHUB_USER_NAME"  
	  password = "YOUR_GITHUB_TOKEN"  
  }  
}
```
Then add the dependency in your app-level `build.gradle.kts`:
```groovy
dependencies {
    implementation("com.flutteroid:gcsresummableuploadwrapper:1.0.0")
}
```
## **🚀 Usage**
```kotlin
private lateinit var uploadManager: UploadManager

uploadManager = UploadManager.Builder(context = this)
    .setFile(file) // File to upload
    .setAccessToken("GCS_OAUTH_ACCESS_TOKEN") // OAuth 2.0 token with storage write scope
    .setBucketName("your-gcs-bucket-id")
    .setCallback(object : UploadStateCallback {
        override fun onProgress(progress: Long) {
            Log.d("Upload", "Progress Update $progress")
        }

        override fun onSuccess() {
            Log.d("Upload", "Upload completed successfully")
        }

        override fun onFailure(error: String) {
            Log.e("Upload", "Upload failed: $error")
        }
    })
    .enableLogging(true) // Optional
    .build()

uploadManager.startUpload()
```
## **⏸️ Pausing, Resuming, and Aborting**
You can control the upload process with the following methods:
```kotlin
uploadManager.pauseUpload()   // Pauses the upload
uploadManager.resumeUpload()  // Resumes the upload
uploadManager.abortUpload()   // Aborts and cancels the upload session
```
## **✅ Requirements**

-   Android API 21+
-   A valid Google Cloud OAuth 2.0 access token with https://www.googleapis.com/auth/devstorage.read_write scope
-   GCS bucket with proper permissions

## 📄 License
This project is licensed under the [MIT License](LICENSE).

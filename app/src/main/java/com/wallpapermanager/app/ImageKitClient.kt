package com.wallpapermanager.app

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** ImageKit client. Private key and upload signature generation stay on the backend. */
class ImageKitClient(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val http: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(90, TimeUnit.SECONDS)
        .build(),
) {
    suspend fun upload(context: Context, file: File, fileName: String, folder: String): ImageKitUpload = withContext(Dispatchers.IO) {
        val authUrl = BuildConfig.IMAGEKIT_AUTH_URL
        require(!authUrl.contains("YOUR_BACKEND_HOST")) { "Configure IMAGEKIT_AUTH_URL before admin upload" }
        val idToken = auth.currentUser?.getIdToken(true)?.await()?.token ?: error("Authentication required")
        val authRequest = Request.Builder().url(authUrl).header("Authorization", "Bearer $idToken").get().build()
        val authJson = http.newCall(authRequest).execute().use { response ->
            if (!response.isSuccessful) throw IOException("ImageKit auth failed: HTTP ${response.code}")
            JSONObject(response.body?.string() ?: error("Empty ImageKit auth response"))
        }
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", fileName, file.asRequestBody("image/jpeg".toMediaType()))
            .addFormDataPart("fileName", fileName)
            .addFormDataPart("publicKey", ImageKitConfig.PUBLIC_KEY)
            .addFormDataPart("token", authJson.getString("token"))
            .addFormDataPart("expire", authJson.getLong("expire").toString())
            .addFormDataPart("signature", authJson.getString("signature"))
            .addFormDataPart("folder", folder)
            .addFormDataPart("useUniqueFileName", "false")
            .build()
        val request = Request.Builder().url(ImageKitConfig.UPLOAD_URL).post(body).build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("ImageKit upload failed: HTTP ${response.code} $text")
            val json = JSONObject(text)
            ImageKitUpload(json.getString("url"), json.getString("fileId"))
        }
    }

    suspend fun delete(fileId: String) = withContext(Dispatchers.IO) {
        val idToken = auth.currentUser?.getIdToken(true)?.await()?.token ?: error("Authentication required")
        val url = BuildConfig.IMAGEKIT_DELETE_URL.trimEnd('/') + "/" + java.net.URLEncoder.encode(fileId, "UTF-8")
        val request = Request.Builder().url(url).header("Authorization", "Bearer $idToken").delete().build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("ImageKit delete failed: HTTP ${response.code}")
        }
    }

    suspend fun downloadBytes(url: String, maxBytes: Long): ByteArray = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).get().build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("ImageKit download failed: HTTP ${response.code}")
            val body = response.body ?: error("Empty ImageKit response")
            require(body.contentLength() <= maxBytes || body.contentLength() == -1L) { "Image exceeds safe size limit" }
            val bytes = body.bytes()
            require(bytes.size.toLong() <= maxBytes) { "Image exceeds safe size limit" }
            bytes
        }
    }
}

data class ImageKitUpload(val url: String, val fileId: String)

object ImageKitConfig {
    const val IMAGEKIT_ID = "enpw7tyyr"
    const val ENDPOINT = "https://ik.imagekit.io/enpw7tyyr"
    const val PUBLIC_KEY = "public_/gytdHMHubFq6eR9gsR4HPw2Tys="
    const val UPLOAD_URL = "https://upload.imagekit.io/api/v1/files/upload"
}

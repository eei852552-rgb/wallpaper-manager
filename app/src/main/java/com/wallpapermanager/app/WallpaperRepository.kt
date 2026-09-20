package com.wallpapermanager.app

import android.content.Context
import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import java.io.File

data class Wallpaper(
    val id: String = "", val title: String = "", val category: String = "Nature",
    val imageUrl: String = "", val fileId: String = "", val isVip: Boolean = false,
    val isFeatured: Boolean = false, val isPublished: Boolean = true,
    val downloadsCount: Long = 0, val uploadedAt: Any? = null,
)
data class UserProfile(val uid: String = "", val email: String = "", val isAdmin: Boolean = false, val isVip: Boolean = false)

/** Firebase Auth/Firestore remain unchanged; only the image layer uses ImageKit. */
class WallpaperRepository {
    private val auth get() = FirebaseAuth.getInstance()
    private val db get() = FirebaseFirestore.getInstance()
    private val imageKit = ImageKitClient()

    suspend fun wallpapers(): List<Wallpaper> = withContext(Dispatchers.IO) {
        val user = profile()
        val base = db.collection("wallpapers").whereEqualTo("isPublished", true)
        val query = if (user?.isVip == true || user?.isAdmin == true) base else base.whereEqualTo("isVip", false)
        query.orderBy("uploadedAt", Query.Direction.DESCENDING).get().await().toObjects(Wallpaper::class.java)
    }

    suspend fun profile(): UserProfile? = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext null
        db.collection("users").document(uid).get().await().toObject(UserProfile::class.java)
    }

    suspend fun isFavorite(id: String): Boolean = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext false
        db.collection("users").document(uid).collection("favorites").document(id).get().await().exists()
    }

    suspend fun setFavorite(id: String, yes: Boolean) = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext
        val ref = db.collection("users").document(uid).collection("favorites").document(id)
        if (yes) ref.set(mapOf("wallpaperId" to id, "createdAt" to FieldValue.serverTimestamp())).await()
        else ref.delete().await()
    }

    /** Admin is enforced by the backend's Firebase-token + Firestore-role check. */
    suspend fun upload(context: Context, uri: Uri, title: String, category: String, vip: Boolean, featured: Boolean, published: Boolean) = withContext(Dispatchers.IO) {
        require(title.trim().isNotEmpty()) { "Title is required" }
        val id = db.collection("wallpapers").document().id
        val temp = safeTempImage(File.createTempFile("wallpaper_", ".jpg", context.cacheDir))
        try {
            ImageCompression.compressToJpeg(context.contentResolver, uri, temp)
            val uploaded = imageKit.upload(context, temp, "$id.jpg", "/wallpapers")
            db.collection("wallpapers").document(id).set(mapOf(
                "id" to id, "title" to title.trim(), "category" to category,
                "imageUrl" to uploaded.url, "fileId" to uploaded.fileId,
                "isVip" to vip, "isFeatured" to featured, "isPublished" to published,
                "downloadsCount" to 0L, "uploadedAt" to FieldValue.serverTimestamp(),
            )).await()
        } finally { temp.delete() }
    }

    suspend fun loadImageBytes(wallpaper: Wallpaper, maxBytes: Long = ImageCompression.MAX_UPLOAD_BYTES): ByteArray = withContext(Dispatchers.IO) {
        require(wallpaper.imageUrl.isNotBlank()) { "Wallpaper is missing its ImageKit URL" }
        imageKit.downloadBytes(wallpaper.imageUrl, maxBytes)
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val doc = db.collection("wallpapers").document(id).get().await()
        val fileId = doc.getString("fileId")
        if (!fileId.isNullOrBlank()) imageKit.delete(fileId)
        doc.reference.delete().await()
    }
}

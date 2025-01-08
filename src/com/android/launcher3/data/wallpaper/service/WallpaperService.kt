package com.android.launcher3.data.wallpaper.service

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.Log

import androidx.room.withTransaction

import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.data.AppDatabase
import com.android.launcher3.data.wallpaper.Wallpaper
import com.android.launcher3.util.DaggerSingletonObject
import com.android.launcher3.util.SafeCloseable

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.inject.Inject

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@LauncherAppSingleton
class WallpaperService @Inject constructor(
    @ApplicationContext private val context: Context,
) : SafeCloseable {

    private val rankMutex = Mutex()

    private val roomDb by lazy { AppDatabase.INSTANCE.get(context) }
    private val dao by lazy { roomDb.wallpaperDao() }

    suspend fun saveWallpaper(wallpaperManager: WallpaperManager) {
        runCatching {
            val wallpaperDrawable = wallpaperManager.drawable as? BitmapDrawable
            val currentBitmap = wallpaperDrawable?.bitmap ?: return
            val byteArray = bitmapToByteArray(currentBitmap)
            saveWallpaper(byteArray)
        }.onFailure {
            Log.e("WallpaperChange", "Error detecting wallpaper change: ${it.message}", it)
        }
    }

    private fun calculateChecksum(imageData: ByteArray): String {
        return MessageDigest.getInstance("MD5")
            .digest(imageData)
            .joinToString("") { "%02x".format(it) }
    }

    private suspend fun saveWallpaper(imageData: ByteArray) = withContext(Dispatchers.IO) {
        rankMutex.withLock {
            val timestamp = System.currentTimeMillis()
            val checksum = calculateChecksum(imageData)

            val existingWallpapers = dao.getTopWallpapers()

            val matched = existingWallpapers.firstOrNull { it.checksum == checksum }
            if (matched != null) {
                Log.d("WallpaperService", "Wallpaper already exists with checksum: $checksum")
                promoteToRank0(matched.id, timestamp)
                return@withContext
            }

            if (existingWallpapers.size >= 4) {
                val toRemove = existingWallpapers.minByOrNull { it.timestamp }
                if (toRemove != null) {
                    dao.deleteWallpaper(toRemove.id)
                    deleteWallpaperFile(toRemove.imagePath)
                }
            }

            val topWallpapers = dao.getTopWallpapers()

            if (topWallpapers.any { it.rank == 0 }) {
                dao.bumpAllRanks()
            }

            val imagePath = saveImageToAppStorage(imageData)
            dao.insert(
                Wallpaper(
                    imagePath = imagePath,
                    rank = 0,
                    timestamp = timestamp,
                    checksum = checksum
                )
            )
        }
    }

    private suspend fun promoteToRank0(id: Long, timestamp: Long) {
        roomDb.withTransaction {
            dao.updateWallpaper(id, rank = 0, timestamp = timestamp)

            val others = dao.getTopWallpapersExcluding(excludeId = id)
            others.forEachIndexed { index, w ->
                dao.setRankOnly(w.id, rank = index + 1)
            }
        }
    }

    suspend fun updateWallpaperRank(selectedWallpaper: Wallpaper) = withContext(Dispatchers.IO) {
        rankMutex.withLock {
            val now = System.currentTimeMillis()
            promoteToRank0(selectedWallpaper.id, now)
        }
    }

    suspend fun getTopWallpapers(): List<Wallpaper> = withContext(Dispatchers.IO) {
        dao.getTopWallpapers()
    }

    fun getTopWallpapersBlocking(): List<Wallpaper> {
        return runBlocking {
            withContext(Dispatchers.IO) { dao.getTopWallpapers() }
        }
    }

    private fun deleteWallpaperFile(imagePath: String) {
        runCatching {
            val file = File(imagePath)
            if (file.exists()) file.delete()
        }
    }

    private fun saveImageToAppStorage(imageData: ByteArray): String {
        val storageDir = File(context.filesDir, "wallpapers")
        if (!storageDir.exists()) storageDir.mkdirs()

        val imageHash = imageData.hashCode().toString()
        val imageFile = File(storageDir, "wallpaper_$imageHash.jpg")

        if (!imageFile.exists()) {
            runCatching {
                FileOutputStream(imageFile).use { it.write(imageData) }
            }.onFailure {
                Log.e("WallpaperService", "Error saving image: ${it.message}", it)
            }
        }
        return imageFile.absolutePath
    }

    override fun close() {
        TODO("Not yet implemented")
    }

    private fun bitmapToByteArray(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }

    companion object {
        @JvmField
        val INSTANCE = DaggerSingletonObject(LauncherAppComponent::getWallpaperService)
    }
}

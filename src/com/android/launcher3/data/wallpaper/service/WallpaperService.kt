package com.android.launcher3.data.wallpaper.service

import android.app.WallpaperManager
import android.app.WallpaperManager.FLAG_LOCK
import android.app.WallpaperManager.FLAG_SYSTEM
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory 
import android.graphics.drawable.BitmapDrawable
import android.util.Log

import androidx.room.withTransaction

import com.android.launcher3.LauncherPrefs
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.data.AppDatabase
import com.android.launcher3.data.wallpaper.Wallpaper
import com.android.launcher3.util.DaggerSingletonObject
import com.android.launcher3.util.SafeCloseable

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
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

    @Volatile
    private var lastHandledWallpaperId: Int = -1 

    suspend fun saveWallpaper(wallpaperManager: WallpaperManager) {
        if (wallpaperManager.wallpaperInfo != null) return

        runCatching {
            withContext(Dispatchers.IO) {
                rankMutex.withLock {
                    val wallpaperId = wallpaperManager.getWallpaperId(FLAG_SYSTEM)
                    if (wallpaperId == lastHandledWallpaperId) return@withLock

                    val bytes = readSystemWallpaperBytes(wallpaperManager) ?: return@withLock
                    saveWallpaperLocked(bytes)
                    lastHandledWallpaperId = wallpaperId
                }
            }
        }.onFailure {
            Log.e("WallpaperService", "Error reading current wallpaper: ${it.message}", it)
        }
    }

    private suspend fun saveWallpaperLocked(imageData: ByteArray) {
        val timestamp = System.currentTimeMillis()
        val checksum = calculateChecksum(imageData)

        val existingWallpapers = dao.getTopWallpapers()
        existingWallpapers.firstOrNull { it.checksum == checksum }?.let {
            promoteToRank0(it.id, timestamp)
            return
        }

        if (existingWallpapers.size >= 4) {
            existingWallpapers.minByOrNull { it.timestamp }?.let {
                dao.deleteWallpaper(it.id)
                deleteWallpaperFile(it.imagePath)
            }
        }

        if (dao.getTopWallpapers().any { it.rank == 0 }) {
            dao.bumpAllRanks()
        }

        val imagePath = saveImageToAppStorage(imageData, checksum)
        dao.insert(Wallpaper(imagePath = imagePath, rank = 0, timestamp = timestamp, checksum = checksum))
    }

    suspend fun applyWallpaper(
        wallpaper: Wallpaper,
        wallpaperManager: WallpaperManager,
    ): Boolean = withContext(Dispatchers.IO) {
        rankMutex.withLock {
            runCatching {
                val bmp = BitmapFactory.decodeFile(wallpaper.imagePath) ?: return@runCatching false
                val newId = wallpaperManager.setBitmap(bmp, null, true, FLAG_SYSTEM)
                if (newId == 0) return@runCatching false
                if (LauncherPrefs.WALLPAPER_CAROUSEL_LOCKSCREEN.get(context)) {
                    wallpaperManager.setBitmap(bmp, null, true, FLAG_LOCK)
                }
                promoteToRank0(wallpaper.id, System.currentTimeMillis())
                lastHandledWallpaperId = newId
                true
            }.getOrDefault(false)
        }
    }

    private fun readSystemWallpaperBytes(wm: WallpaperManager): ByteArray? {
        return wm.getWallpaperFile(FLAG_SYSTEM)?.use { pfd ->
            FileInputStream(pfd.fileDescriptor).use { it.readBytes() }
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

            val imagePath = saveImageToAppStorage(imageData, checksum)
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

    private fun saveImageToAppStorage(imageData: ByteArray, checksum: String): String {
        val storageDir = File(context.filesDir, "wallpapers").apply { if (!exists()) mkdirs() }
        val imageFile = File(storageDir, "wallpaper_$checksum.jpg")
        if (!imageFile.exists()) {
            runCatching { FileOutputStream(imageFile).use { it.write(imageData) } }
                .onFailure { Log.e("WallpaperService", "Error saving image: ${it.message}", it) }
        }
        return imageFile.absolutePath
    }

    override fun close() {
        // Backed by an app-scoped DB singleton; nothing to release.
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

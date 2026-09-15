package com.android.launcher3.data.wallpaper.service

import android.app.WallpaperManager
import android.app.WallpaperManager.FLAG_LOCK
import android.app.WallpaperManager.FLAG_SYSTEM
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
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

    /** Guards the DB, the stored image files and [lastHandledWallpaperId]. */
    private val rankMutex = Mutex()

    private val roomDb by lazy { AppDatabase.INSTANCE.get(context) }
    private val dao by lazy { roomDb.wallpaperDao() }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * System wallpaper id that is already reflected in the carousel DB.
     */
    private var lastHandledWallpaperId: Int
        get() = prefs.getInt(KEY_LAST_HANDLED_ID, NO_ID)
        set(value) {
            prefs.edit().putInt(KEY_LAST_HANDLED_ID, value).apply()
        }

    /**
     * Records the current system wallpaper in the carousel. Call this whenever the wallpaper may
     * have changed; it is a no-op if this wallpaper id has already been handled.
     */
    suspend fun saveWallpaper(wallpaperManager: WallpaperManager) {
        if (wallpaperManager.wallpaperInfo != null) return

        runCatching {
            withContext(Dispatchers.IO) {
                rankMutex.withLock {
                    val wallpaperId = wallpaperManager.getWallpaperId(WallpaperManager.FLAG_SYSTEM)
                    if (wallpaperId == lastHandledWallpaperId) return@withLock

                    val currentBitmap = captureWallpaperBitmap(wallpaperManager) ?: run {
                        Log.w(TAG, "Unable to capture current wallpaper bitmap")
                        return@withLock
                    }
                    saveWallpaperLocked(bitmapToByteArray(currentBitmap))
                    lastHandledWallpaperId = wallpaperId
                }
            }
        }.onFailure {
            Log.e(TAG, "Error reading current wallpaper: ${it.message}", it)
        }
    }

    private fun captureWallpaperBitmap(wallpaperManager: WallpaperManager): Bitmap? {
        wallpaperManager.drawable?.let { drawable ->
            when (drawable) {
                is BitmapDrawable -> drawable.bitmap?.takeIf { !it.isRecycled }
                else ->
                    runCatching {
                            val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: return@runCatching null
                            val height =
                                drawable.intrinsicHeight.takeIf { it > 0 } ?: return@runCatching null
                            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bmp ->
                                val canvas = Canvas(bmp)
                                drawable.setBounds(0, 0, width, height)
                                drawable.draw(canvas)
                            }
                        }
                        .getOrNull()
            }
        }?.let {
            return it
        }

        return runCatching {
                wallpaperManager.getWallpaperFile(WallpaperManager.FLAG_SYSTEM)?.use { pfd ->
                    BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor)
                }
            }
            .onFailure { Log.w(TAG, "getWallpaperFile failed: ${it.message}") }
            .getOrNull()
    }

    private fun calculateChecksum(imageData: ByteArray): String {
        return MessageDigest.getInstance("MD5")
            .digest(imageData)
            .joinToString("") { "%02x".format(it) }
    }

    private suspend fun saveWallpaperLocked(imageData: ByteArray) {
        val timestamp = System.currentTimeMillis()
        val checksum = calculateChecksum(imageData)

        val existingWallpapers = dao.getTopWallpapers()

        val matched = existingWallpapers.firstOrNull { it.checksum == checksum }
        if (matched != null) {
            Log.d(TAG, "Wallpaper already exists with checksum: $checksum")
            promoteToRank0(matched.id, timestamp)
            return
        }

        if (existingWallpapers.size >= MAX_WALLPAPERS) {
            val toRemove = existingWallpapers.minByOrNull { it.timestamp }
            if (toRemove != null) {
                dao.deleteWallpaper(toRemove.id)
                deleteWallpaperFile(toRemove.imagePath)
            }
        }

        if (dao.getTopWallpapers().any { it.rank == 0 }) {
            dao.bumpAllRanks()
        }

        // Write the file first so a failed write never leaves a row pointing at nothing.
        val imagePath = saveImageToAppStorage(imageData, checksum) ?: return

        dao.insert(
            Wallpaper(
                imagePath = imagePath,
                rank = 0,
                timestamp = timestamp,
                checksum = checksum
            )
        )
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

    suspend fun applyWallpaper(
        wallpaper: Wallpaper,
        wallpaperManager: WallpaperManager,
    ): Boolean { 
        return runCatching {
            withContext(Dispatchers.IO) {
                rankMutex.withLock {
                    val bmp = BitmapFactory.decodeFile(wallpaper.imagePath) ?: return@withLock false
                    val newId =
                        wallpaperManager.setBitmap(bmp, null, true, FLAG_SYSTEM)
                    if (newId == 0) return@withLock false
                    promoteToRank0(wallpaper.id, System.currentTimeMillis())
                    lastHandledWallpaperId = newId
                    if (LauncherPrefs.WALLPAPER_CAROUSEL_LOCKSCREEN.get(context)) {
                        wallpaperManager.setBitmap(
                            bmp, null, true, FLAG_LOCK
                        )
                    }
                    true
                }
            }
        }.getOrDefault(false)
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

    /** Returns the stored file's path, or null if it could not be written. */
    private fun saveImageToAppStorage(imageData: ByteArray, checksum: String): String? {
        val storageDir = File(context.filesDir, "wallpapers").apply { if (!exists()) mkdirs() }
    
        val imageFile = File(storageDir, "wallpaper_$checksum.jpg")

        if (!imageFile.exists()) {
            try {
                FileOutputStream(imageFile).use { it.write(imageData) }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving image: ${e.message}", e)
                imageFile.delete()
                return null
            }
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
        private const val TAG = "WallpaperService"
        private const val PREFS_NAME = "wallpaper_carousel"
        private const val KEY_LAST_HANDLED_ID = "last_handled_system_wallpaper_id"
        private const val NO_ID = -1
        private const val MAX_WALLPAPERS = 4

        @JvmField
        val INSTANCE = DaggerSingletonObject(LauncherAppComponent::getWallpaperService)
    }
}

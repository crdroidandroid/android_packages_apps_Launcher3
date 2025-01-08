package com.android.launcher3.data.wallpaper.service

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery

import com.android.launcher3.data.wallpaper.Wallpaper

@Dao
interface WallpaperDao {

    @Query("SELECT * FROM wallpapers ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getTopWallpapers(limit: Int = 4): List<Wallpaper>

    @Query("SELECT * FROM wallpapers WHERE checksum = :checksum LIMIT 1")
    suspend fun getByChecksum(checksum: String): Wallpaper?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(wallpaper: Wallpaper): Long

    @Query("DELETE FROM wallpapers WHERE id = :id")
    suspend fun deleteWallpaper(id: Long): Int

    @Query("UPDATE wallpapers SET rank = rank + 1 WHERE rank >= 0")
    suspend fun bumpAllRanks()

    @Query("SELECT * FROM wallpapers WHERE id != :excludeId ORDER BY timestamp DESC")
    suspend fun getTopWallpapersExcluding(excludeId: Long): List<Wallpaper>

    @Query("UPDATE wallpapers SET rank = :rank WHERE id = :id")
    suspend fun setRankOnly(id: Long, rank: Int): Int

    @Query("UPDATE wallpapers SET rank = :rank, timestamp = :timestamp WHERE id = :id")
    suspend fun updateWallpaper(id: Long, rank: Int, timestamp: Long): Int

    @RawQuery
    suspend fun checkpoint(supportSQLiteQuery: SupportSQLiteQuery): Int
}

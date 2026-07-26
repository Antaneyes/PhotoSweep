package com.josh.photosweep.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.nio.ByteBuffer
import java.security.MessageDigest

class MediaDatabase(context: Context) :
    SQLiteOpenHelper(context, "photosweep.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE media (
                media_key TEXT PRIMARY KEY,
                dedup_key TEXT NOT NULL,
                thumbnail_url TEXT NOT NULL,
                width INTEGER NOT NULL,
                height INTEGER NOT NULL,
                timestamp INTEGER NOT NULL,
                duration_ms INTEGER NOT NULL,
                shuffle_rank INTEGER NOT NULL,
                status INTEGER NOT NULL DEFAULT 0,
                stream_url TEXT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_media_status_rank ON media(status, shuffle_rank)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun upsert(items: List<MediaItem>) {
        writableDatabase.beginTransaction()
        try {
            items.forEach { item ->
                val values = ContentValues().apply {
                    put("media_key", item.mediaKey)
                    put("dedup_key", item.dedupKey)
                    put("thumbnail_url", item.thumbnailUrl)
                    put("width", item.width)
                    put("height", item.height)
                    put("timestamp", item.timestamp)
                    put("duration_ms", item.durationMs)
                    put("shuffle_rank", item.shuffleRank)
                }
                writableDatabase.insertWithOnConflict(
                    "media",
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_IGNORE
                )
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun list(status: ReviewStatus, limit: Int = 10_000): List<MediaItem> {
        val result = mutableListOf<MediaItem>()
        readableDatabase.query(
            "media",
            COLUMNS,
            "status = ?",
            arrayOf(status.value.toString()),
            null,
            null,
            "shuffle_rank ASC",
            limit.toString()
        ).use { cursor ->
            while (cursor.moveToNext()) result += cursor.toItem()
        }
        return result
    }

    fun randomList(status: ReviewStatus, limit: Int = 10_000): List<MediaItem> {
        val result = mutableListOf<MediaItem>()
        readableDatabase.query(
            "media",
            COLUMNS,
            "status = ?",
            arrayOf(status.value.toString()),
            null,
            null,
            "RANDOM()",
            limit.toString()
        ).use { cursor ->
            while (cursor.moveToNext()) result += cursor.toItem()
        }
        return result
    }

    fun updateStatus(mediaKey: String, status: ReviewStatus) {
        writableDatabase.update(
            "media",
            ContentValues().apply { put("status", status.value) },
            "media_key = ?",
            arrayOf(mediaKey)
        )
    }

    fun updateStatuses(mediaKeys: Collection<String>, status: ReviewStatus) {
        writableDatabase.beginTransaction()
        try {
            mediaKeys.forEach { updateStatus(it, status) }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun updateStreamUrl(mediaKey: String, url: String) {
        writableDatabase.update(
            "media",
            ContentValues().apply { put("stream_url", url) },
            "media_key = ?",
            arrayOf(mediaKey)
        )
    }

    fun counts(): Map<ReviewStatus, Int> = ReviewStatus.entries.associateWith { status ->
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM media WHERE status = ?",
            arrayOf(status.value.toString())
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }
    }

    fun resetHistory() {
        writableDatabase.execSQL(
            "UPDATE media SET status = ? WHERE status IN (?, ?)",
            arrayOf(ReviewStatus.UNSEEN.value, ReviewStatus.KEPT.value, ReviewStatus.FAILED.value)
        )
    }

    private fun android.database.Cursor.toItem() = MediaItem(
        mediaKey = getString(0),
        dedupKey = getString(1),
        thumbnailUrl = getString(2),
        width = getInt(3),
        height = getInt(4),
        timestamp = getLong(5),
        durationMs = getLong(6),
        shuffleRank = getLong(7),
        status = ReviewStatus.from(getInt(8)),
        streamUrl = getString(9)
    )

    companion object {
        private val COLUMNS = arrayOf(
            "media_key", "dedup_key", "thumbnail_url", "width", "height",
            "timestamp", "duration_ms", "shuffle_rank", "status", "stream_url"
        )

        fun shuffleRank(mediaKey: String): Long {
            val bytes = MessageDigest.getInstance("SHA-256")
                .digest("photosweep-v1:$mediaKey".toByteArray())
            return ByteBuffer.wrap(bytes, 0, Long.SIZE_BYTES).long and Long.MAX_VALUE
        }
    }
}

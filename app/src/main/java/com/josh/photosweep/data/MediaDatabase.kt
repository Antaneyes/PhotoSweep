package com.josh.photosweep.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.nio.ByteBuffer
import java.security.MessageDigest

class MediaDatabase(context: Context) :
    SQLiteOpenHelper(context, "photosweep.db", null, 2) {

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
                stream_url TEXT,
                source INTEGER NOT NULL DEFAULT 0,
                content_uri TEXT,
                mime_type TEXT,
                available INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_media_source_status_rank ON media(source, status, available, shuffle_rank)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE media ADD COLUMN source INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE media ADD COLUMN content_uri TEXT")
            db.execSQL("ALTER TABLE media ADD COLUMN mime_type TEXT")
            db.execSQL("ALTER TABLE media ADD COLUMN available INTEGER NOT NULL DEFAULT 1")
            db.execSQL("CREATE INDEX idx_media_source_status_rank ON media(source, status, available, shuffle_rank)")
        }
    }

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
                    put("source", item.source.value)
                    put("content_uri", item.contentUri)
                    put("mime_type", item.mimeType)
                    put("available", if (item.available) 1 else 0)
                }
                writableDatabase.insertWithOnConflict(
                    "media",
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_IGNORE
                )
                if (item.source == MediaSource.DEVICE) {
                    writableDatabase.update(
                        "media",
                        ContentValues().apply {
                            put("thumbnail_url", item.thumbnailUrl)
                            put("width", item.width)
                            put("height", item.height)
                            put("timestamp", item.timestamp)
                            put("duration_ms", item.durationMs)
                            put("content_uri", item.contentUri)
                            put("mime_type", item.mimeType)
                            put("available", 1)
                        },
                        "media_key = ?",
                        arrayOf(item.mediaKey)
                    )
                }
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun list(source: MediaSource, status: ReviewStatus, limit: Int = 10_000): List<MediaItem> {
        val result = mutableListOf<MediaItem>()
        readableDatabase.query(
            "media",
            COLUMNS,
            "source = ? AND status = ? AND available = 1",
            arrayOf(source.value.toString(), status.value.toString()),
            null,
            null,
            "shuffle_rank ASC",
            limit.toString()
        ).use { cursor ->
            while (cursor.moveToNext()) result += cursor.toItem()
        }
        return result
    }

    fun randomList(source: MediaSource, status: ReviewStatus, limit: Int = 10_000): List<MediaItem> {
        val result = mutableListOf<MediaItem>()
        readableDatabase.query(
            "media",
            COLUMNS,
            "source = ? AND status = ? AND available = 1",
            arrayOf(source.value.toString(), status.value.toString()),
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

    fun counts(source: MediaSource): Map<ReviewStatus, Int> = ReviewStatus.entries.associateWith { status ->
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM media WHERE source = ? AND status = ? AND available = 1",
            arrayOf(source.value.toString(), status.value.toString())
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }
    }

    fun resetHistory(source: MediaSource) {
        writableDatabase.execSQL(
            "UPDATE media SET status = ? WHERE source = ? AND status IN (?, ?)",
            arrayOf(ReviewStatus.UNSEEN.value, source.value, ReviewStatus.KEPT.value, ReviewStatus.FAILED.value)
        )
    }

    fun markSourceUnavailable(source: MediaSource) {
        writableDatabase.update(
            "media",
            ContentValues().apply { put("available", 0) },
            "source = ?",
            arrayOf(source.value.toString())
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
        streamUrl = getString(9),
        source = MediaSource.from(getInt(10)),
        contentUri = getString(11),
        mimeType = getString(12),
        available = getInt(13) != 0
    )

    companion object {
        private val COLUMNS = arrayOf(
            "media_key", "dedup_key", "thumbnail_url", "width", "height",
            "timestamp", "duration_ms", "shuffle_rank", "status", "stream_url",
            "source", "content_uri", "mime_type", "available"
        )

        fun shuffleRank(mediaKey: String): Long {
            val bytes = MessageDigest.getInstance("SHA-256")
                .digest("photosweep-v1:$mediaKey".toByteArray())
            return ByteBuffer.wrap(bytes, 0, Long.SIZE_BYTES).long and Long.MAX_VALUE
        }
    }
}

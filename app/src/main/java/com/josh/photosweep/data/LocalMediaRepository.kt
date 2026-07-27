package com.josh.photosweep.data

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore

class LocalMediaRepository(private val context: Context) {
    fun scan(): List<MediaItem> {
        val result = ArrayList<MediaItem>()
        runCatching {
            scanCollection(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false, result)
        }
        runCatching {
            scanCollection(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, result)
        }
        return result
    }

    private fun scanCollection(
        collection: android.net.Uri,
        video: Boolean,
        result: MutableList<MediaItem>
    ) {
        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.WIDTH)
            add(MediaStore.MediaColumns.HEIGHT)
            add(MediaStore.MediaColumns.DATE_ADDED)
            add(MediaStore.MediaColumns.MIME_TYPE)
            if (video) add(MediaStore.Video.VideoColumns.DURATION)
        }.toTypedArray()
        val selection = if (Build.VERSION.SDK_INT >= 30) {
            "${MediaStore.MediaColumns.IS_TRASHED} = 0 AND ${MediaStore.MediaColumns.IS_PENDING} = 0"
        } else null
        context.contentResolver.query(
            collection,
            projection,
            selection,
            null,
            "${MediaStore.MediaColumns.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
            val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val durationColumn = if (video) {
                cursor.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.DURATION)
            } else -1
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val uri = ContentUris.withAppendedId(collection, id).toString()
                val key = "device:${if (video) "video" else "image"}:$id"
                result += MediaItem(
                    mediaKey = key,
                    dedupKey = "",
                    thumbnailUrl = uri,
                    width = cursor.getInt(widthColumn),
                    height = cursor.getInt(heightColumn),
                    timestamp = cursor.getLong(dateColumn) * 1_000L,
                    durationMs = if (video) cursor.getLong(durationColumn) else 0L,
                    shuffleRank = MediaDatabase.shuffleRank(key),
                    status = ReviewStatus.UNSEEN,
                    source = MediaSource.DEVICE,
                    contentUri = uri,
                    mimeType = cursor.getString(mimeColumn)
                )
            }
        }
    }
}

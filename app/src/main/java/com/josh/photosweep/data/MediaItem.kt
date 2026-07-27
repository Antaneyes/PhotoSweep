package com.josh.photosweep.data

enum class MediaSource(val value: Int) {
    GOOGLE_PHOTOS(0),
    DEVICE(1);

    companion object {
        fun from(value: Int) = entries.firstOrNull { it.value == value } ?: GOOGLE_PHOTOS
    }
}

enum class ReviewStatus(val value: Int) {
    UNSEEN(0),
    KEPT(1),
    BASKET(2),
    TRASHED(3),
    FAILED(4);

    companion object {
        fun from(value: Int) = entries.firstOrNull { it.value == value } ?: UNSEEN
    }
}

data class MediaItem(
    val mediaKey: String,
    val dedupKey: String,
    val thumbnailUrl: String,
    val width: Int,
    val height: Int,
    val timestamp: Long,
    val durationMs: Long,
    val shuffleRank: Long,
    val status: ReviewStatus,
    val streamUrl: String? = null,
    val source: MediaSource = MediaSource.GOOGLE_PHOTOS,
    val contentUri: String? = null,
    val mimeType: String? = null,
    val available: Boolean = true
) {
    val isVideo: Boolean get() = durationMs > 0
    val imageModel: Any?
        get() = if (source == MediaSource.DEVICE) contentUri else null
    val playableUri: String?
        get() = if (source == MediaSource.DEVICE) contentUri else streamUrl
    val displayThumbnail: String
        get() = if (thumbnailUrl.contains("=")) thumbnailUrl
        else "$thumbnailUrl=w1200-h1600-no?authuser=0"
}

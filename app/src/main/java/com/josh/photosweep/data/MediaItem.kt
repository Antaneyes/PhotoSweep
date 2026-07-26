package com.josh.photosweep.data

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
    val streamUrl: String? = null
) {
    val isVideo: Boolean get() = durationMs > 0
    val displayThumbnail: String
        get() = if (thumbnailUrl.contains("=")) thumbnailUrl
        else "$thumbnailUrl=w1200-h1600-no?authuser=0"
}

package com.josh.photosweep

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.video.VideoFrameDecoder
import com.josh.photosweep.gecko.GeckoBridge

class PhotoSweepApplication : Application(), SingletonImageLoader.Factory {
    val bridge: GeckoBridge by lazy { GeckoBridge(this) }

    override fun newImageLoader(context: android.content.Context): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()
}

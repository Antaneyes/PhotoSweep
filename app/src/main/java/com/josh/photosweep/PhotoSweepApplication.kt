package com.josh.photosweep

import android.app.Application
import com.josh.photosweep.gecko.GeckoBridge

class PhotoSweepApplication : Application() {
    val bridge: GeckoBridge by lazy { GeckoBridge(this) }
}

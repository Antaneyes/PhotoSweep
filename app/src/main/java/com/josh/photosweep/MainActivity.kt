package com.josh.photosweep

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.josh.photosweep.ui.PhotoSweepApp
import com.josh.photosweep.ui.PhotoSweepTheme

class MainActivity : ComponentActivity() {
    private val bridge get() = (application as PhotoSweepApplication).bridge

    private val viewModel: PhotoSweepViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return PhotoSweepViewModel(application as Application, bridge) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PhotoSweepTheme {
                PhotoSweepApp(viewModel = viewModel, bridge = bridge)
            }
        }
    }
}

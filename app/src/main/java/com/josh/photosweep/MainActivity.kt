package com.josh.photosweep

import android.app.Application
import android.Manifest
import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.IntentSender
import android.os.Build
import android.provider.MediaStore
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.josh.photosweep.ui.PhotoSweepApp
import com.josh.photosweep.ui.PhotoSweepTheme
import com.josh.photosweep.data.MediaItem
import com.josh.photosweep.data.MediaSource

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
    private var pendingTrash = emptyList<MediaItem>()
    private val succeededTrash = mutableListOf<String>()
    private val failedTrash = mutableListOf<String>()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (hasDeviceMediaAccess()) {
            viewModel.setLocalAccessPartial(hasPartialDeviceAccess())
            viewModel.selectSource(MediaSource.DEVICE)
            viewModel.syncDevice()
        } else {
            viewModel.showMessage("PhotoSweep necesita permiso para leer la galería")
        }
    }

    private val trashLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (Build.VERSION.SDK_INT >= 30) {
            if (result.resultCode == Activity.RESULT_OK) {
                viewModel.completeLocalTrash(pendingTrash.map { it.mediaKey }, emptyList())
            } else {
                viewModel.setLocalTrashing(false)
            }
            pendingTrash = emptyList()
        } else {
            val current = pendingTrash.firstOrNull()
            if (current != null && result.resultCode != Activity.RESULT_OK) {
                failedTrash += current.mediaKey
                pendingTrash = pendingTrash.drop(1)
            }
            deleteNextLegacy()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PhotoSweepTheme {
                PhotoSweepApp(
                    viewModel = viewModel,
                    bridge = bridge,
                    onSelectDevice = ::openDeviceGallery,
                    onTrashDevice = ::trashDeviceItems
                )
            }
        }
    }

    private fun openDeviceGallery() {
        if (hasDeviceMediaAccess() && !hasPartialDeviceAccess()) {
            viewModel.setLocalAccessPartial(hasPartialDeviceAccess())
            viewModel.selectSource(MediaSource.DEVICE)
            viewModel.syncDevice()
        } else {
            permissionLauncher.launch(
                when {
                    Build.VERSION.SDK_INT >= 34 -> arrayOf(
                        Manifest.permission.READ_MEDIA_IMAGES,
                        Manifest.permission.READ_MEDIA_VIDEO,
                        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                    )
                    Build.VERSION.SDK_INT >= 33 -> arrayOf(
                        Manifest.permission.READ_MEDIA_IMAGES,
                        Manifest.permission.READ_MEDIA_VIDEO
                    )
                    Build.VERSION.SDK_INT <= 28 -> arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            )
        }
    }

    private fun hasDeviceMediaAccess(): Boolean = when {
        Build.VERSION.SDK_INT >= 34 ->
            checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        Build.VERSION.SDK_INT >= 33 ->
            checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        else -> checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun hasPartialDeviceAccess(): Boolean =
        Build.VERSION.SDK_INT >= 34 &&
            checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun trashDeviceItems(items: List<MediaItem>) {
        if (items.isEmpty()) return
        pendingTrash = items
        succeededTrash.clear()
        failedTrash.clear()
        viewModel.setLocalTrashing(true)
        if (Build.VERSION.SDK_INT >= 30) {
            val request = MediaStore.createTrashRequest(
                contentResolver,
                items.mapNotNull { it.contentUri?.let(android.net.Uri::parse) },
                true
            )
            trashLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
        } else {
            deleteNextLegacy()
        }
    }

    private fun deleteNextLegacy() {
        val item = pendingTrash.firstOrNull()
        if (item == null) {
            viewModel.completeLocalTrash(succeededTrash.toList(), failedTrash.toList())
            return
        }
        val uri = item.contentUri?.let(android.net.Uri::parse)
        if (uri == null) {
            failedTrash += item.mediaKey
            pendingTrash = pendingTrash.drop(1)
            deleteNextLegacy()
            return
        }
        try {
            if (contentResolver.delete(uri, null, null) > 0) {
                succeededTrash += item.mediaKey
            } else {
                failedTrash += item.mediaKey
            }
            pendingTrash = pendingTrash.drop(1)
            deleteNextLegacy()
        } catch (error: SecurityException) {
            if (Build.VERSION.SDK_INT == 29 && error is RecoverableSecurityException) {
                trashLauncher.launch(
                    IntentSenderRequest.Builder(
                        error.userAction.actionIntent.intentSender
                    ).build()
                )
            } else {
                failedTrash += item.mediaKey
                pendingTrash = pendingTrash.drop(1)
                deleteNextLegacy()
            }
        }
    }
}

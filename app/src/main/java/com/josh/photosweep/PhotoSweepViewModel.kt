package com.josh.photosweep

import android.app.Application
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.josh.photosweep.data.MediaDatabase
import com.josh.photosweep.data.MediaItem
import com.josh.photosweep.data.MediaSource
import com.josh.photosweep.data.LocalMediaRepository
import com.josh.photosweep.data.ReviewStatus
import com.josh.photosweep.gecko.BridgeStatus
import com.josh.photosweep.gecko.GeckoBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

enum class Screen {
    LOADING, HOME, SWIPE, BASKET, KEPT, LOGIN
}

data class UiState(
    val screen: Screen = Screen.LOADING,
    val connectionSlow: Boolean = false,
    val scanning: Boolean = false,
    val scanCount: Int = 0,
    val scanComplete: Boolean = false,
    val deck: List<MediaItem> = emptyList(),
    val basket: List<MediaItem> = emptyList(),
    val kept: List<MediaItem> = emptyList(),
    val counts: Map<ReviewStatus, Int> = emptyMap(),
    val lastAction: Pair<String, ReviewStatus>? = null,
    val trashing: Boolean = false,
    val trashProgress: Pair<Int, Int>? = null,
    val thumbnailKey: String? = null,
    val thumbnailBytes: ByteArray? = null,
    val thumbnailCache: Map<String, ByteArray> = emptyMap(),
    val message: String? = null,
    val source: MediaSource = MediaSource.GOOGLE_PHOTOS,
    val localAccessPartial: Boolean = false
)

class PhotoSweepViewModel(
    application: Application,
    private val bridge: GeckoBridge
) : AndroidViewModel(application) {
    private val reviewHistory = MediaSource.entries.associateWith { ArrayDeque<MediaItem>() }
    private val thumbnailRequests = mutableSetOf<String>()
    private val sessionDeckKeys = mutableMapOf<MediaSource, List<String>>()
    private val database = MediaDatabase(application)
    private val localMedia = LocalMediaRepository(application)
    private val preferences = application.getSharedPreferences("photosweep", 0)
    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()
    val bridgeStatus: StateFlow<BridgeStatus> = bridge.status.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        BridgeStatus.STARTING
    )

    init {
        val savedSource = MediaSource.from(preferences.getInt("media_source", 0))
        _uiState.value = _uiState.value.copy(source = savedSource)
        viewModelScope.launch {
            refreshLists(Screen.HOME)
            if (savedSource == MediaSource.DEVICE && hasLocalAccess()) syncDevice()
        }
        viewModelScope.launch {
            delay(6_000)
            if (_uiState.value.screen == Screen.LOADING) {
                _uiState.value = _uiState.value.copy(connectionSlow = true)
            }
        }
        viewModelScope.launch {
            bridge.messages.collect(::handleBridgeMessage)
        }
        viewModelScope.launch {
            bridge.status.collect { status ->
                if (status == BridgeStatus.READY) {
                    if (_uiState.value.screen == Screen.LOGIN) {
                        refreshLists(Screen.HOME)
                    }
                }
            }
        }
    }

    fun show(screen: Screen) {
        if (screen == Screen.BASKET || screen == Screen.KEPT ||
            screen == Screen.SWIPE || screen == Screen.HOME
        ) {
            viewModelScope.launch { refreshLists(screen) }
        } else {
            _uiState.value = _uiState.value.copy(screen = screen)
        }
    }

    fun reloadLogin() = bridge.reload()

    fun selectSource(source: MediaSource) {
        preferences.edit().putInt("media_source", source.value).apply()
        _uiState.value = _uiState.value.copy(source = source, scanComplete = false)
        viewModelScope.launch { refreshLists(Screen.HOME) }
    }

    fun syncDevice() {
        _uiState.value = _uiState.value.copy(scanning = true, scanCount = 0, message = null)
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val items = localMedia.scan()
                database.markSourceUnavailable(MediaSource.DEVICE)
                database.upsert(items)
                sessionDeckKeys.remove(MediaSource.DEVICE)
                items.size
            }.onSuccess { count ->
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        scanning = false, scanComplete = true, scanCount = count
                    )
                    refreshLists(Screen.HOME)
                }
            }.onFailure { error ->
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        scanning = false,
                        message = "No se pudo leer la galería: ${error.message}"
                    )
                }
            }
        }
    }

    fun startScan() {
        _uiState.value = _uiState.value.copy(
            scanning = true,
            scanCount = 0,
            scanComplete = false,
            message = null
        )
        bridge.scan()
    }

    fun review(item: MediaItem, status: ReviewStatus) {
        viewModelScope.launch(Dispatchers.IO) {
            database.updateStatus(item.mediaKey, status)
            val source = item.source
            withContext(Dispatchers.Main) {
                reviewHistory.getValue(source).addLast(item)
                val current = _uiState.value
                _uiState.value = current.copy(
                    deck = current.deck.filterNot { it.mediaKey == item.mediaKey },
                    counts = database.counts(source),
                    lastAction = item.mediaKey to item.status
                )
            }
        }
    }

    fun undo() {
        val history = reviewHistory.getValue(_uiState.value.source)
        val item = history.removeLastOrNull() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            database.updateStatus(item.mediaKey, item.status)
            val counts = database.counts(item.source)
            withContext(Dispatchers.Main) {
                val current = _uiState.value
                _uiState.value = current.copy(
                    deck = listOf(item) + current.deck.filterNot {
                        it.mediaKey == item.mediaKey
                    },
                    counts = counts,
                    lastAction = history.lastOrNull()?.let {
                        it.mediaKey to it.status
                    }
                )
            }
        }
    }

    fun returnToDeck(item: MediaItem) {
        viewModelScope.launch(Dispatchers.IO) {
            database.updateStatus(item.mediaKey, ReviewStatus.UNSEEN)
            refreshLists(Screen.BASKET)
        }
    }

    fun requestPreview(item: MediaItem) {
        if (item.source == MediaSource.GOOGLE_PHOTOS) bridge.preview(item.mediaKey)
    }

    fun requestThumbnail(item: MediaItem) {
        if (item.source == MediaSource.DEVICE) return
        if (_uiState.value.thumbnailCache.containsKey(item.mediaKey) ||
            !thumbnailRequests.add(item.mediaKey)
        ) return
        bridge.thumbnail(item.mediaKey, item.thumbnailUrl)
    }

    fun trashGoogleBasket() {
        val basket = _uiState.value.basket
        if (basket.isEmpty() || _uiState.value.trashing) return
        _uiState.value = _uiState.value.copy(
            trashing = true,
            trashProgress = 0 to basket.size,
            message = null
        )
        bridge.moveToTrash(
            UUID.randomUUID().toString(),
            basket.map { it.mediaKey to it.dedupKey }
        )
    }

    fun completeLocalTrash(succeeded: List<String>, failed: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            database.updateStatuses(succeeded, ReviewStatus.TRASHED)
            database.updateStatuses(failed, ReviewStatus.FAILED)
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    trashing = false,
                    trashProgress = null,
                    message = if (failed.isEmpty()) {
                        "${succeeded.size} elementos procesados"
                    } else {
                        "${succeeded.size} eliminados; ${failed.size} siguen en la cesta"
                    }
                )
                refreshLists(Screen.BASKET)
            }
        }
    }

    fun setLocalTrashing(active: Boolean) {
        _uiState.value = _uiState.value.copy(
            trashing = active,
            trashProgress = if (active) 0 to _uiState.value.basket.size else null
        )
    }

    fun setLocalAccessPartial(partial: Boolean) {
        _uiState.value = _uiState.value.copy(localAccessPartial = partial)
    }

    fun showMessage(message: String) {
        _uiState.value = _uiState.value.copy(message = message)
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    fun resetHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            database.resetHistory(_uiState.value.source)
            refreshLists(Screen.HOME, clearUndo = true)
        }
    }

    private suspend fun handleBridgeMessage(message: JSONObject) {
        when (message.optString("type")) {
            "scanPage" -> {
                val jsonItems = message.optJSONArray("items") ?: return
                val items = buildList {
                    for (index in 0 until jsonItems.length()) {
                        val json = jsonItems.optJSONObject(index) ?: continue
                        if (!json.optBoolean("isOwned", true)) continue
                        val mediaKey = json.optString("mediaKey")
                        if (mediaKey.isBlank()) continue
                        add(
                            MediaItem(
                                mediaKey = mediaKey,
                                dedupKey = json.optString("dedupKey"),
                                thumbnailUrl = json.optString("thumbnailUrl"),
                                width = json.optInt("width"),
                                height = json.optInt("height"),
                                timestamp = json.optLong("timestamp"),
                                durationMs = json.optLong("durationMs"),
                                shuffleRank = MediaDatabase.shuffleRank(mediaKey),
                                status = ReviewStatus.UNSEEN
                            )
                        )
                    }
                }
                withContext(Dispatchers.IO) { database.upsert(items) }
                _uiState.value = _uiState.value.copy(scanCount = message.optInt("count"))
            }

            "scanComplete" -> {
                _uiState.value = _uiState.value.copy(
                    scanning = false,
                    scanComplete = true,
                    scanCount = message.optInt("count")
                )
                // La primera carga de HOME puede ocurrir antes de que exista ningún
                // elemento local y dejar fijado un mazo vacío para toda la sesión.
                // Tras indexar hay que sortearlo de nuevo con la fototeca actual.
                sessionDeckKeys.remove(MediaSource.GOOGLE_PHOTOS)
                refreshLists(Screen.HOME)
            }

            "preview" -> {
                val mediaKey = message.optString("mediaKey")
                val streamUrl = message.optString("streamUrl")
                if (mediaKey.isNotBlank() && streamUrl.isNotBlank()) {
                    withContext(Dispatchers.IO) {
                        database.updateStreamUrl(mediaKey, streamUrl)
                    }
                    refreshLists(_uiState.value.screen)
                }
            }

            "thumbnail" -> {
                val mediaKey = message.optString("mediaKey")
                val dataUrl = message.optString("dataUrl")
                thumbnailRequests.remove(mediaKey)
                if (mediaKey.isNotBlank() && dataUrl.startsWith("data:")) {
                    val encoded = dataUrl.substringAfter(',', "")
                    runCatching { Base64.decode(encoded, Base64.DEFAULT) }
                        .onSuccess { bytes ->
                            val cache = LinkedHashMap(_uiState.value.thumbnailCache)
                            cache[mediaKey] = bytes
                            while (cache.size > 24) {
                                cache.remove(cache.keys.first())
                            }
                            _uiState.value = _uiState.value.copy(
                                thumbnailKey = mediaKey,
                                thumbnailBytes = bytes,
                                thumbnailCache = cache
                            )
                        }
                }
            }

            "trashProgress" -> {
                _uiState.value = _uiState.value.copy(
                    trashProgress = message.optInt("done") to message.optInt("total")
                )
            }

            "trashResult" -> {
                val succeeded = message.stringList("succeeded")
                val failed = message.stringList("failed")
                withContext(Dispatchers.IO) {
                    database.updateStatuses(succeeded, ReviewStatus.TRASHED)
                    database.updateStatuses(failed, ReviewStatus.FAILED)
                }
                _uiState.value = _uiState.value.copy(
                    trashing = false,
                    trashProgress = null,
                    message = if (failed.isEmpty()) {
                        "${succeeded.size} elementos movidos a la papelera"
                    } else {
                        "${succeeded.size} borrados; ${failed.size} siguen en la cesta"
                    }
                )
                refreshLists(Screen.BASKET)
            }

            "verifyAbsentResult" -> {
                val checked = message.optInt("checked")
                val present = message.stringList("present")
                _uiState.value = _uiState.value.copy(
                    message = if (present.isEmpty()) {
                        "Verificado: $checked elementos ya no están en la fototeca principal"
                    } else {
                        "Aviso: ${present.size} de $checked elementos aún aparecen en la fototeca"
                    }
                )
            }

            "bridgeError" -> {
                if (message.optString("operation") == "thumbnail") {
                    thumbnailRequests.clear()
                }
                _uiState.value = _uiState.value.copy(
                    scanning = false,
                    trashing = false,
                    message = message.optString("message", "Error de Google Photos")
                )
            }
        }
    }

    private suspend fun refreshLists(
        screen: Screen,
        clearUndo: Boolean = false
    ) = withContext(Dispatchers.IO) {
        val source = _uiState.value.source
        val unseenByKey = database
            .list(source, ReviewStatus.UNSEEN, Int.MAX_VALUE)
            .associateBy(MediaItem::mediaKey)
        var deckKeys = sessionDeckKeys[source]
        if (deckKeys == null || deckKeys.isEmpty() && unseenByKey.isNotEmpty()) {
            deckKeys = database
                .randomList(source, ReviewStatus.UNSEEN)
                .map(MediaItem::mediaKey)
            sessionDeckKeys[source] = deckKeys
        }
        val deck = deckKeys.mapNotNull(unseenByKey::get)
        val basket = database.list(source, ReviewStatus.BASKET) +
            database.list(source, ReviewStatus.FAILED)
        val kept = database.list(source, ReviewStatus.KEPT)
        val counts = database.counts(source)
        withContext(Dispatchers.Main) {
            if (clearUndo) reviewHistory.getValue(source).clear()
            _uiState.value = _uiState.value.copy(
                screen = screen,
                deck = deck,
                basket = basket,
                kept = kept,
                counts = counts,
                lastAction = if (clearUndo) null else _uiState.value.lastAction
            )
            if (screen == Screen.HOME) {
                deck.take(3).forEach(::requestThumbnail)
            }
        }
    }

    private fun JSONObject.stringList(name: String): List<String> {
        val array = optJSONArray(name) ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                array.optString(index).takeIf(String::isNotBlank)?.let(::add)
            }
        }
    }

    private fun hasLocalAccess(): Boolean {
        val context = getApplication<Application>()
        return when {
            Build.VERSION.SDK_INT >= 34 -> listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            ).any { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
            Build.VERSION.SDK_INT >= 33 -> listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            ).any { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
            else -> context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }
    }
}

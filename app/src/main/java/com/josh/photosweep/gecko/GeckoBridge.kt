package com.josh.photosweep.gecko

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebExtension
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

enum class BridgeStatus {
    STARTING, PAGE_LOADED, READY, ERROR
}

class GeckoBridge(context: Context) {
    private val appContext = context.applicationContext
    private val runtime = GeckoRuntime.create(
        appContext,
        GeckoRuntimeSettings.Builder()
            .consoleOutput(true)
            .build()
    )
    val session = GeckoSession()

    private val _status = MutableStateFlow(BridgeStatus.STARTING)
    val status = _status.asStateFlow()

    private val _messages = MutableSharedFlow<JSONObject>(extraBufferCapacity = 64)
    val messages = _messages.asSharedFlow()

    private var port: WebExtension.Port? = null
    private var attachedView: GeckoView? = null

    init {
        session.settings.userAgentMode = GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
        session.settings.viewportMode = GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
        session.contentDelegate = object : GeckoSession.ContentDelegate {}
        session.open(runtime)
        runtime.webExtensionController.setTabActive(session, true)
        installBridge()
        session.loadUri(LOGIN_URL)
    }

    fun attach(view: GeckoView) {
        if (attachedView === view) return
        attachedView?.releaseSession()
        attachedView = view
        if (view.session == null) view.setSession(session)
    }

    fun detach(view: GeckoView) {
        if (attachedView !== view) return
        view.releaseSession()
        attachedView = null
        runtime.webExtensionController.setTabActive(session, true)
    }

    fun reload() {
        _status.value = BridgeStatus.STARTING
        session.loadUri(LOGIN_URL)
    }

    fun scan() = send(JSONObject().put("type", "scan"))

    fun stopScan() = send(JSONObject().put("type", "stopScan"))

    fun preview(mediaKey: String) = send(
        JSONObject()
            .put("type", "preview")
            .put("mediaKey", mediaKey)
    )

    fun thumbnail(mediaKey: String, url: String) = send(
        JSONObject()
            .put("type", "thumbnail")
            .put("mediaKey", mediaKey)
            .put("url", url)
    )

    fun verifyAbsent(keys: List<String>) = send(
        JSONObject()
            .put("type", "verifyAbsent")
            .put("keys", JSONArray(keys))
    )

    fun moveToTrash(requestId: String, items: List<Pair<String, String>>) {
        val payload = JSONArray()
        items.forEach { (mediaKey, dedupKey) ->
            payload.put(
                JSONObject()
                    .put("mediaKey", mediaKey)
                    .put("dedupKey", dedupKey)
            )
        }
        send(
            JSONObject()
                .put("type", "trash")
                .put("requestId", requestId)
                .put("items", payload)
        )
    }

    fun close() {
        port?.disconnect()
        session.close()
        runtime.shutdown()
    }

    private fun send(message: JSONObject) {
        val activePort = port
        if (activePort == null) {
            _status.value = BridgeStatus.ERROR
            _messages.tryEmit(
                JSONObject()
                    .put("type", "bridgeError")
                    .put("operation", "native")
                    .put("message", "El puente de Google Photos no está conectado")
            )
            return
        }
        activePort.postMessage(message)
    }

    private fun installBridge() {
        runtime.webExtensionController
            .ensureBuiltIn(EXTENSION_PATH, EXTENSION_ID)
            .accept(
                { extension ->
                    if (extension == null) {
                        _status.value = BridgeStatus.ERROR
                        return@accept
                    }
                    Handler(Looper.getMainLooper()).post {
                        session.webExtensionController.setMessageDelegate(
                            extension,
                            object : WebExtension.MessageDelegate {
                            override fun onConnect(newPort: WebExtension.Port) {
                                port = newPort
                                newPort.setDelegate(object : WebExtension.PortDelegate {
                                    override fun onPortMessage(
                                        message: Any,
                                        source: WebExtension.Port
                                    ) {
                                        val json = message as? JSONObject ?: return
                                        when (json.optString("type")) {
                                            "ready" -> _status.value = BridgeStatus.READY
                                            "pageLoaded" -> _status.value = BridgeStatus.PAGE_LOADED
                                            "bridgeError" -> Log.w(TAG, json.toString())
                                            "verifyAbsentResult" -> Log.i(TAG, json.toString())
                                        }
                                        _messages.tryEmit(json)
                                    }

                                    override fun onDisconnect(source: WebExtension.Port) {
                                        if (port === source) port = null
                                        if (_status.value == BridgeStatus.READY) {
                                            _status.value = BridgeStatus.PAGE_LOADED
                                        }
                                    }
                                })
                            }

                            override fun onMessage(
                                nativeApp: String,
                                message: Any,
                                sender: WebExtension.MessageSender
                            ): GeckoResult<Any>? = null
                            },
                            NATIVE_APP
                        )
                    }
                },
                { error ->
                    Log.e(TAG, "No se pudo instalar la extensión interna", error)
                    _status.value = BridgeStatus.ERROR
                    _messages.tryEmit(
                        JSONObject()
                            .put("type", "bridgeError")
                            .put("operation", "install")
                            .put("message", error?.message ?: error?.toString() ?: "Error desconocido")
                    )
                }
            )
    }

    companion object {
        private const val TAG = "PhotoSweepBridge"
        private const val PHOTOS_URL = "https://photos.google.com/"
        private const val LOGIN_URL =
            "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fphotos.google.com%2F"
        private const val EXTENSION_PATH = "resource://android/assets/photosweep/"
        private const val EXTENSION_ID = "bridge@photosweep.local"
        private const val NATIVE_APP = "photosweep"
    }
}

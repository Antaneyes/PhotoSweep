package com.josh.photosweep.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem as PlayerMediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.josh.photosweep.PhotoSweepViewModel
import com.josh.photosweep.R
import com.josh.photosweep.Screen
import com.josh.photosweep.data.MediaItem
import com.josh.photosweep.data.MediaSource
import com.josh.photosweep.data.ReviewStatus
import com.josh.photosweep.gecko.BridgeStatus
import com.josh.photosweep.gecko.GeckoBridge
import org.mozilla.geckoview.GeckoView
import java.text.DateFormat
import java.util.Date
import kotlin.math.abs
import kotlin.math.max

@Composable
fun PhotoSweepApp(
    viewModel: PhotoSweepViewModel,
    bridge: GeckoBridge,
    onSelectDevice: () -> Unit,
    onTrashDevice: (List<MediaItem>) -> Unit
) {
    val ui by viewModel.uiState.collectAsState()
    val bridgeStatus by viewModel.bridgeStatus.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(ui.message) {
        ui.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    BackHandler(
        enabled = ui.screen != Screen.HOME && ui.screen != Screen.LOADING
    ) {
        viewModel.show(Screen.HOME)
    }

    Scaffold(
        containerColor = Ink,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) }
    ) { scaffoldPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
        ) {
            // Gecko suspende las páginas que no tienen una vista asociada. Mantener una
            // superficie mínima evita que la paginación y sus temporizadores se congelen
            // mientras mostramos la interfaz nativa.
            if (ui.screen != Screen.LOGIN) {
                AndroidView(
                    modifier = Modifier
                        .size(1.dp)
                        .align(Alignment.BottomEnd),
                    factory = { context ->
                        GeckoView(context).also(bridge::attach)
                    },
                    onRelease = bridge::detach
                )
            }
            when (ui.screen) {
                Screen.LOADING -> LoadingScreen(
                    connectionSlow = ui.connectionSlow,
                    onLogin = { viewModel.show(Screen.LOGIN) }
                )
                Screen.LOGIN -> LoginScreen(
                    bridge = bridge,
                    status = bridgeStatus,
                    onReload = viewModel::reloadLogin
                )
                Screen.HOME -> HomeScreen(
                    ui = ui,
                    googleReady = bridgeStatus == BridgeStatus.READY,
                    onScan = viewModel::startScan,
                    onSyncDevice = viewModel::syncDevice,
                    onSelectGoogle = { viewModel.selectSource(MediaSource.GOOGLE_PHOTOS) },
                    onSelectDevice = onSelectDevice,
                    onSwipe = { viewModel.show(Screen.SWIPE) },
                    onBasket = { viewModel.show(Screen.BASKET) },
                    onKept = { viewModel.show(Screen.KEPT) },
                    onLogin = { viewModel.show(Screen.LOGIN) },
                    onReset = viewModel::resetHistory
                )
                Screen.SWIPE -> SwipeScreen(
                    deck = ui.deck,
                    thumbnailCache = ui.thumbnailCache,
                    basketCount = ui.basket.size,
                    canUndo = ui.lastAction != null,
                    onBack = { viewModel.show(Screen.HOME) },
                    onBasket = { viewModel.show(Screen.BASKET) },
                    onReview = viewModel::review,
                    onUndo = viewModel::undo,
                    onPreview = viewModel::requestPreview,
                    onThumbnail = viewModel::requestThumbnail
                )
                Screen.BASKET -> BasketScreen(
                    items = ui.basket,
                    thumbnailCache = ui.thumbnailCache,
                    trashing = ui.trashing,
                    progress = ui.trashProgress,
                    onBack = { viewModel.show(Screen.HOME) },
                    onReturn = viewModel::returnToDeck,
                    onThumbnail = viewModel::requestThumbnail,
                    onPreview = viewModel::requestPreview,
                    onTrash = {
                        if (ui.source == MediaSource.DEVICE) onTrashDevice(ui.basket)
                        else viewModel.trashGoogleBasket()
                    },
                    source = ui.source
                )
                Screen.KEPT -> KeptScreen(
                    items = ui.kept,
                    thumbnailCache = ui.thumbnailCache,
                    onBack = { viewModel.show(Screen.HOME) },
                    onThumbnail = viewModel::requestThumbnail,
                    onPreview = viewModel::requestPreview
                )
            }
        }
    }
}

@Composable
private fun LoadingScreen(
    connectionSlow: Boolean,
    onLogin: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(WindowInsets.statusBars.asPaddingValues()),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(Modifier.size(30.dp), strokeWidth = 3.dp)
            Spacer(Modifier.height(14.dp))
            Text(
                if (connectionSlow) "La conexión está tardando más de lo normal"
                else "Preparando tu fototeca",
                color = Muted
            )
            if (connectionSlow) {
                Spacer(Modifier.height(16.dp))
                Button(onClick = onLogin) {
                    Text("Abrir inicio de sesión")
                }
            }
        }
        Image(
            painter = painterResource(R.drawable.ic_loading_logo),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .size(54.dp)
        )
    }
}

@Composable
private fun LoginScreen(
    bridge: GeckoBridge,
    status: BridgeStatus,
    onReload: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Surface)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Conecta Google Photos", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                Text(
                    when (status) {
                        BridgeStatus.STARTING -> "Preparando el motor privado…"
                        BridgeStatus.PAGE_LOADED -> "Inicia sesión y abre tu fototeca"
                        BridgeStatus.READY -> "Sesión detectada"
                        BridgeStatus.ERROR -> "No se pudo conectar el puente"
                    },
                    color = Muted,
                    fontSize = 13.sp
                )
            }
            IconButton(onClick = onReload) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Recargar")
            }
        }
        Text(
            "Google puede identificar este navegador interno como Linux porque usa el modo " +
                "escritorio. La sesión permanece guardada únicamente en este dispositivo.",
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceHigh)
                .padding(horizontal = 20.dp, vertical = 10.dp),
            color = Muted,
            fontSize = 12.sp
        )
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.White),
            factory = { context ->
                GeckoView(context).also(bridge::attach)
            },
            onRelease = bridge::detach
        )
    }
}

@Composable
private fun HomeScreen(
    ui: com.josh.photosweep.UiState,
    googleReady: Boolean,
    onScan: () -> Unit,
    onSyncDevice: () -> Unit,
    onSelectGoogle: () -> Unit,
    onSelectDevice: () -> Unit,
    onSwipe: () -> Unit,
    onBasket: () -> Unit,
    onKept: () -> Unit,
    onLogin: () -> Unit,
    onReset: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(WindowInsets.statusBars.asPaddingValues())
            .padding(horizontal = 24.dp, vertical = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("PhotoSweep", fontSize = 32.sp, fontWeight = FontWeight.Black)
                Text("Tu fototeca, una decisión cada vez", color = Muted)
            }
            if (ui.source == MediaSource.DEVICE || googleReady) {
                FilledIconButton(
                    onClick = if (ui.source == MediaSource.DEVICE) onSelectDevice else onLogin
                ) {
                    Icon(
                        Icons.Rounded.Refresh,
                        contentDescription = if (ui.source == MediaSource.DEVICE) {
                            "Actualizar acceso"
                        } else {
                            "Cuenta de Google Photos"
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onSelectGoogle,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (ui.source == MediaSource.GOOGLE_PHOTOS) Mint else SurfaceHigh,
                    contentColor = if (ui.source == MediaSource.GOOGLE_PHOTOS) Ink else Sand
                )
            ) { Text("Google Photos") }
            Button(
                onClick = onSelectDevice,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (ui.source == MediaSource.DEVICE) Mint else SurfaceHigh,
                    contentColor = if (ui.source == MediaSource.DEVICE) Ink else Sand
                )
            ) { Text("Este dispositivo") }
        }
        if (ui.source == MediaSource.DEVICE && ui.localAccessPartial) {
            Spacer(Modifier.height(10.dp))
            Surface(
                onClick = onSelectDevice,
                color = SurfaceHigh,
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    "Acceso limitado: toca para elegir más fotos y vídeos",
                    modifier = Modifier.padding(12.dp),
                    color = Sand,
                    fontSize = 12.sp
                )
            }
        }
        if (ui.source == MediaSource.GOOGLE_PHOTOS && !googleReady) {
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onLogin,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Mint,
                    contentColor = Ink
                )
            ) {
                Text("Iniciar sesión en Google Photos", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("ESTADO", color = Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(
                label = "Pendientes",
                value = ui.counts[ReviewStatus.UNSEEN] ?: 0,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = "Conservadas",
                value = ui.counts[ReviewStatus.KEPT] ?: 0,
                modifier = Modifier.weight(1f),
                onClick = onKept
            )
            StatCard(
                label = "Cesta",
                value = ui.basket.size,
                modifier = Modifier.weight(1f),
                accent = Coral,
                onClick = onBasket
            )
        }

        Spacer(Modifier.height(26.dp))
        if (ui.scanning) {
            Surface(shape = RoundedCornerShape(24.dp), color = Surface) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            if (ui.source == MediaSource.DEVICE) "Leyendo la galería del dispositivo"
                            else "Explorando toda la fototeca",
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("${ui.scanCount} elementos encontrados", color = Muted)
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        } else {
            Button(
                onClick = if (ui.source == MediaSource.DEVICE) onSyncDevice else onScan,
                enabled = ui.source == MediaSource.DEVICE || googleReady,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text(
                    if (ui.source == MediaSource.DEVICE) "Sincronizar galería"
                    else if (ui.scanComplete) "Sincronizar de nuevo"
                    else "Indexar toda la fototeca"
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Button(
            onClick = onSwipe,
            enabled = ui.deck.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            shape = RoundedCornerShape(22.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Mint,
                contentColor = Ink
            )
        ) {
            Icon(Icons.Rounded.Shuffle, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Empezar a deslizar", fontWeight = FontWeight.Black, fontSize = 18.sp)
                Text("${ui.deck.size} elementos en orden aleatorio", fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onBasket,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SurfaceHigh,
                contentColor = Sand
            )
        ) {
            Icon(Icons.Rounded.GridView, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text("Revisar cesta (${ui.basket.size})")
        }

        Spacer(Modifier.weight(1f))
        TextButton(onClick = onReset, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Icon(Icons.Rounded.History, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Restablecer fotos conservadas")
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: Int,
    modifier: Modifier,
    accent: Color = Mint,
    onClick: (() -> Unit)? = null
) {
    val content: @Composable () -> Unit = {
        Column(Modifier.padding(14.dp)) {
            Text(value.toString(), color = accent, fontWeight = FontWeight.Black, fontSize = 25.sp)
            Text(label, color = Muted, fontSize = 11.sp, maxLines = 1)
        }
    }
    if (onClick == null) {
        Surface(modifier = modifier, shape = RoundedCornerShape(18.dp), color = Surface, content = content)
    } else {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = RoundedCornerShape(18.dp),
            color = Surface,
            content = content
        )
    }
}

@Composable
private fun SwipeScreen(
    deck: List<MediaItem>,
    thumbnailCache: Map<String, ByteArray>,
    basketCount: Int,
    canUndo: Boolean,
    onBack: () -> Unit,
    onBasket: () -> Unit,
    onReview: (MediaItem, ReviewStatus) -> Unit,
    onUndo: () -> Unit,
    onPreview: (MediaItem) -> Unit,
    onThumbnail: (MediaItem) -> Unit
) {
    val item = deck.firstOrNull()
    LaunchedEffect(deck.take(3).map(MediaItem::mediaKey)) {
        deck.take(3).forEach(onThumbnail)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
            .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver")
            }
            Text(
                if (deck.isEmpty()) "Sesión terminada" else "${deck.size} pendientes",
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onBasket) {
                Box {
                    Icon(Icons.Rounded.GridView, contentDescription = "Cesta")
                    if (basketCount > 0) {
                        Text(
                            basketCount.toString(),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .background(Coral, CircleShape)
                                .padding(horizontal = 4.dp),
                            color = Color.White,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (item == null) {
                EmptyDeck(onBack)
            } else {
                SwipeCard(
                    item = item,
                    thumbnailBytes = thumbnailCache[item.mediaKey],
                    onKeep = { onReview(item, ReviewStatus.KEPT) },
                    onBasket = { onReview(item, ReviewStatus.BASKET) },
                    onPreview = { onPreview(item) }
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 34.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledIconButton(
                onClick = { item?.let { onReview(it, ReviewStatus.BASKET) } },
                enabled = item != null,
                modifier = Modifier.size(64.dp),
                colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                    containerColor = SurfaceHigh,
                    contentColor = Coral
                )
            ) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = "A la cesta", Modifier.size(30.dp))
            }
            IconButton(onClick = onUndo, enabled = canUndo) {
                Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = "Deshacer")
            }
            FilledIconButton(
                onClick = { item?.let { onReview(it, ReviewStatus.KEPT) } },
                enabled = item != null,
                modifier = Modifier.size(64.dp),
                colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                    containerColor = Mint,
                    contentColor = Ink
                )
            ) {
                Icon(Icons.Rounded.Check, contentDescription = "Conservar", Modifier.size(32.dp))
            }
        }
    }
}

@Composable
private fun SwipeCard(
    item: MediaItem,
    thumbnailBytes: ByteArray?,
    onKeep: () -> Unit,
    onBasket: () -> Unit,
    onPreview: () -> Unit
) {
    var offset by remember(item.mediaKey) { mutableStateOf(Offset.Zero) }
    var zoom by remember(item.mediaKey) { mutableStateOf(1f) }
    var imagePan by remember(item.mediaKey) { mutableStateOf(Offset.Zero) }
    var videoMuted by remember(item.mediaKey) { mutableStateOf(false) }
    var viewportSize by remember(item.mediaKey) { mutableStateOf(IntSize.Zero) }
    val imageAspect = item.width.toFloat() / item.height.coerceAtLeast(1)
    val viewportAspect = viewportSize.width.toFloat() / viewportSize.height.coerceAtLeast(1)
    val coverZoom = if (viewportSize == IntSize.Zero) 1f else {
        max(imageAspect / viewportAspect, viewportAspect / imageAspect).coerceAtLeast(1f)
    }
    val isDefaultZoom = abs(zoom - coverZoom) < 0.03f
    LaunchedEffect(item.mediaKey, coverZoom) {
        zoom = coverZoom
        imagePan = Offset.Zero
    }
    val directionColor = if (offset.x >= 0) Mint else Coral
    val directionText = if (offset.x >= 0) "CONSERVAR" else "CESTA"

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .shadow(18.dp, RoundedCornerShape(28.dp))
            .graphicsLayer {
                translationX = offset.x
                translationY = offset.y * 0.15f
                rotationZ = offset.x / 45f
            }
            .pointerInput(item.mediaKey) {
                detectDragGestures(
                    onDragEnd = {
                        if (!isDefaultZoom) {
                            offset = Offset.Zero
                            return@detectDragGestures
                        }
                        when {
                            offset.x > 170f -> onKeep()
                            offset.x < -170f -> onBasket()
                            else -> offset = Offset.Zero
                        }
                    },
                    onDragCancel = { offset = Offset.Zero },
                    onDrag = { change, dragAmount ->
                        if (!isDefaultZoom || change.pressed.not()) return@detectDragGestures
                        change.consume()
                        offset += dragAmount
                    }
                )
            },
        shape = RoundedCornerShape(28.dp),
        color = Surface
    ) {
        Box(Modifier.fillMaxSize()) {
            if (item.isVideo && !item.playableUri.isNullOrBlank()) {
                VideoPlayer(item.playableUri!!, muted = videoMuted)
            } else {
                AsyncImage(
                    model = thumbnailBytes ?: item.imageModel,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { viewportSize = it }
                        .graphicsLayer {
                            scaleX = zoom
                            scaleY = zoom
                            translationX = imagePan.x
                            translationY = imagePan.y
                        }
                        .pointerInput(item.mediaKey) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.changes.count { it.pressed } >= 2) {
                                        val nextZoom = (zoom * event.calculateZoom())
                                            .coerceIn(1f, max(5f, coverZoom))
                                        zoom = nextZoom
                                        imagePan = if (nextZoom <= 1.01f) {
                                            Offset.Zero
                                        } else {
                                            imagePan + event.calculatePan()
                                        }
                                        event.changes.forEach { it.consume() }
                                    }
                                    if (event.changes.none { it.pressed }) break
                                }
                            }
                        }
                )
            }
            if (item.isVideo && !item.playableUri.isNullOrBlank()) {
                IconButton(
                    onClick = { videoMuted = !videoMuted },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(14.dp)
                        .background(Color(0xB307110E), CircleShape)
                ) {
                    Icon(
                        if (videoMuted) {
                            Icons.AutoMirrored.Rounded.VolumeOff
                        } else {
                            Icons.AutoMirrored.Rounded.VolumeUp
                        },
                        contentDescription = if (videoMuted) "Activar sonido" else "Silenciar"
                    )
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.28f)
                    .align(Alignment.BottomCenter)
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xE6000000))
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(22.dp)
            ) {
                if (item.isVideo) {
                    TextButton(onClick = onPreview) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        Text(if (item.playableUri == null) "Reproducir vídeo" else "Vídeo")
                    }
                }
                Text(
                    DateFormat.getDateInstance(DateFormat.LONG).format(Date(item.timestamp)),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text("${item.width} × ${item.height}", color = Muted, fontSize = 13.sp)
            }
            AnimatedVisibility(
                visible = abs(offset.x) > 55f,
                modifier = Modifier
                    .align(if (offset.x >= 0) Alignment.TopStart else Alignment.TopEnd)
                    .padding(24.dp)
            ) {
                Text(
                    directionText,
                    color = directionColor,
                    fontWeight = FontWeight.Black,
                    fontSize = 26.sp,
                    modifier = Modifier
                        .background(Color(0xCC07110E), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun VideoPlayer(url: String, muted: Boolean) {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(PlayerMediaItem.fromUri(url))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = if (muted) 0f else 1f
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    LaunchedEffect(player, muted) {
        player.volume = if (muted) 0f else 1f
    }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                useController = true
                this.player = player
            }
        },
        update = { it.player = player }
    )
}

@Composable
private fun EmptyDeck(onBack: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.Check, contentDescription = null, tint = Mint, modifier = Modifier.size(72.dp))
        Spacer(Modifier.height(16.dp))
        Text("No quedan fotos pendientes", fontSize = 23.sp, fontWeight = FontWeight.Black)
        Text("Puedes revisar la cesta o sincronizar de nuevo.", color = Muted)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onBack) { Text("Volver al inicio") }
    }
}

@Composable
private fun KeptScreen(
    items: List<MediaItem>,
    thumbnailCache: Map<String, ByteArray>,
    onBack: () -> Unit,
    onThumbnail: (MediaItem) -> Unit,
    onPreview: (MediaItem) -> Unit
) {
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver")
            }
            Column {
                Text("Conservadas", fontWeight = FontWeight.Black, fontSize = 24.sp)
                Text("${items.size} elementos", color = Muted, fontSize = 12.sp)
            }
        }

        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Todavía no has conservado ninguna foto", color = Muted)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(items, key = { _, item -> item.mediaKey }) { index, item ->
                    LaunchedEffect(item.mediaKey) { onThumbnail(item) }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Surface)
                            .clickable { selectedIndex = index }
                    ) {
                        AsyncImage(
                            model = thumbnailCache[item.mediaKey] ?: item.imageModel,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (item.isVideo) {
                            Icon(
                                Icons.Rounded.PlayArrow,
                                contentDescription = "Vídeo",
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                }
            }
        }
    }
    selectedIndex?.let { index ->
        MediaViewer(
            items = items,
            initialIndex = index,
            thumbnailCache = thumbnailCache,
            onThumbnail = onThumbnail,
            onPreview = onPreview,
            onDismiss = { selectedIndex = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BasketScreen(
    items: List<MediaItem>,
    thumbnailCache: Map<String, ByteArray>,
    trashing: Boolean,
    progress: Pair<Int, Int>?,
    onBack: () -> Unit,
    onReturn: (MediaItem) -> Unit,
    onThumbnail: (MediaItem) -> Unit,
    onPreview: (MediaItem) -> Unit,
    onTrash: () -> Unit,
    source: MediaSource
) {
    var confirm by remember { mutableStateOf(false) }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Cesta", fontWeight = FontWeight.Black, fontSize = 24.sp)
                Text("${items.size} elementos por revisar", color = Muted, fontSize = 12.sp)
            }
        }

        if (items.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("La cesta está vacía", color = Muted)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(items, key = { _, item -> item.mediaKey }) { index, item ->
                    LaunchedEffect(item.mediaKey) { onThumbnail(item) }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Surface)
                            .clickable { selectedIndex = index }
                    ) {
                        AsyncImage(
                            model = thumbnailCache[item.mediaKey] ?: item.imageModel,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        IconButton(
                            onClick = { onReturn(item) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .background(Color(0xB307110E), CircleShape)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.Undo,
                                contentDescription = "Sacar de la cesta"
                            )
                        }
                        if (item.isVideo) {
                            Icon(
                                Icons.Rounded.PlayArrow,
                                contentDescription = "Vídeo",
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                }
            }
        }

        Surface(color = Surface, tonalElevation = 8.dp) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
                    .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
            ) {
                if (trashing && progress != null) {
                    Text("Moviendo a la papelera: ${progress.first}/${progress.second}")
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { if (progress.second == 0) 0f else progress.first.toFloat() / progress.second },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        when {
                            source == MediaSource.GOOGLE_PHOTOS ->
                                "Nada se borra hasta confirmar. Google Photos conservará los elementos en su papelera."
                            android.os.Build.VERSION.SDK_INT >= 30 ->
                                "Nada se borra hasta confirmar. Android enviará los elementos a su papelera."
                            else ->
                                "En esta versión de Android los elementos se borrarán definitivamente."
                        },
                        color = Muted,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { confirm = true },
                        enabled = items.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Coral,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Mover ${items.size} a la papelera", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            icon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = Coral) },
            title = { Text("¿Mover ${items.size} elementos?") },
            text = {
                Text(
                    when {
                        source == MediaSource.GOOGLE_PHOTOS ->
                            "Esta acción usa una API no oficial. Los elementos se moverán a la papelera de Google Photos."
                        android.os.Build.VERSION.SDK_INT >= 30 ->
                            "Android solicitará permiso para mover estos elementos a la papelera del dispositivo."
                        else ->
                            "Esta versión de Android no ofrece una papelera estándar. Los elementos se borrarán definitivamente."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirm = false
                        onTrash()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Coral)
                ) {
                    Text("Confirmar")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text("Cancelar") }
            }
        )
    }
    selectedIndex?.let { index ->
        MediaViewer(
            items = items,
            initialIndex = index,
            thumbnailCache = thumbnailCache,
            onThumbnail = onThumbnail,
            onPreview = onPreview,
            onDismiss = { selectedIndex = null }
        )
    }
}

@Composable
private fun MediaViewer(
    items: List<MediaItem>,
    initialIndex: Int,
    thumbnailCache: Map<String, ByteArray>,
    onThumbnail: (MediaItem) -> Unit,
    onPreview: (MediaItem) -> Unit,
    onDismiss: () -> Unit
) {
    if (items.isEmpty()) return
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(items.indices),
        pageCount = { items.size }
    )
    LaunchedEffect(pagerState.currentPage, items) {
        val current = items[pagerState.currentPage]
        onThumbnail(current)
        if (current.isVideo && current.playableUri.isNullOrBlank()) onPreview(current)
        listOf(pagerState.currentPage - 1, pagerState.currentPage + 1)
            .filter { it in items.indices }
            .forEach { onThumbnail(items[it]) }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Ink)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                ViewerPage(
                    item = items[page],
                    thumbnailBytes = thumbnailCache[items[page].mediaKey]
                )
            }
            Text(
                "${pagerState.currentPage + 1} / ${items.size}",
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(WindowInsets.statusBars.asPaddingValues())
                    .padding(top = 20.dp)
                    .background(Color(0xB307110E), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(WindowInsets.statusBars.asPaddingValues())
                    .padding(12.dp)
                    .background(Color(0xB307110E), CircleShape)
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Cerrar")
            }
        }
    }
}

@Composable
private fun ViewerPage(item: MediaItem, thumbnailBytes: ByteArray?) {
    var zoom by remember(item.mediaKey) { mutableStateOf(1f) }
    var pan by remember(item.mediaKey) { mutableStateOf(Offset.Zero) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(item.mediaKey) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.count { it.pressed } >= 2) {
                            zoom = (zoom * event.calculateZoom()).coerceIn(1f, 5f)
                            pan = if (zoom <= 1.01f) Offset.Zero else pan + event.calculatePan()
                            event.changes.forEach { it.consume() }
                        }
                        if (event.changes.none { it.pressed }) break
                    }
                }
            }
    ) {
        if (item.isVideo && !item.playableUri.isNullOrBlank()) {
            VideoPlayer(item.playableUri!!, muted = false)
        } else {
            AsyncImage(
                model = thumbnailBytes ?: item.imageModel,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = zoom
                        scaleY = zoom
                        translationX = pan.x
                        translationY = pan.y
                    }
            )
            if (item.isVideo) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

package com.mpls.salattv

import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ---- Configuration ----------------------------------------------------------

// Official Saudi Quran TV (Saudi Broadcasting Authority) — carries the Kaaba /
// Masjid al-Haram live 24/7. Akamai-backed HLS = reliable for always-on use.
private const val MECCA_HLS_PRIMARY =
    "https://cdn-globecast.akamaized.net/live/eds/saudi_quran/hls_roku/index.m3u8"

// Secondary fallback (Saudi live network) used if the primary errors out.
private const val MECCA_HLS_FALLBACK =
    "http://m.live.net.sa:1935/live/quran/playlist.m3u8"

// Egyptian Adhan (same source as the original app).
private const val ADHAN_URL = "https://www.islamcan.com/audio/adhan/azan4.mp3"

private const val ADHAN_DURATION_MS = 20L * 60L * 1000L // 20 minutes

// ---- Theme colors -----------------------------------------------------------

private val Amber = Color(0xFFF59E0B)
private val AmberLight = Color(0xFFFBBF24)
private val CardBg = Color(0xFF1A1A1A)
private val CardBg2 = Color(0xFF2A2A2A)

// Prayers that trigger the adhan (Sunrise excluded, matching the original).
private val ADHAN_PRAYERS = listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")
private val NEXT_PRAYER_CANDIDATES = listOf("Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha")

class MainActivity : ComponentActivity() {

    private lateinit var insetsController: WindowInsetsControllerCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Never let the screen sleep — this is a 24/7 wall display.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Immersive, edge-to-edge fullscreen.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        insetsController = WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        forceImmersive()

        setContent { MarhabaApp() }
    }

    private fun forceImmersive() {
        try {
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        } catch (_: Exception) {}
        window.decorView.postDelayed({
            try { insetsController.hide(WindowInsetsCompat.Type.systemBars()) } catch (_: Exception) {}
        }, 250)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) forceImmersive()
    }
}

// ---- App --------------------------------------------------------------------

@Composable
fun MarhabaApp() {
    var prayerData by remember { mutableStateOf<PrayerData?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableStateOf(Date()) }
    var isAdhanPlaying by remember { mutableStateOf(false) }
    var userSoundEnabled by remember { mutableStateOf(false) } // stream starts muted; adhan still audible

    // Tick the clock every second (only minute resolution is shown).
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            kotlinx.coroutines.delay(1000L)
        }
    }

    // Load prayer data, then reload shortly after midnight, forever.
    LaunchedEffect(Unit) {
        while (true) {
            try {
                prayerData = PrayerRepository.fetchPrayerData()
                errorMsg = null
            } catch (e: Exception) {
                if (prayerData == null) errorMsg = "Failed to load prayer times."
            }
            // Sleep until 00:00:30 tomorrow (or retry in 60s if we have nothing yet).
            val delayMs = if (prayerData == null) 60_000L else millisUntilAfterMidnight()
            kotlinx.coroutines.delay(delayMs)
        }
    }

    // Adhan trigger: when the current HH:mm matches a prayer time.
    val data = prayerData
    LaunchedEffect(now, data) {
        if (data == null) return@LaunchedEffect
        if (!isAdhanPlaying) {
            val cal = Calendar.getInstance().apply { time = now }
            val cur = String.format(
                Locale.US, "%02d:%02d",
                cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE)
            )
            if (ADHAN_PRAYERS.any { data.timings[it] == cur }) {
                isAdhanPlaying = true
            }
        }
    }
    // Safety cap: if the adhan audio never reports completion, force the window
    // closed after ADHAN_DURATION_MS so the overlay can't get stuck.
    LaunchedEffect(isAdhanPlaying) {
        if (isAdhanPlaying) {
            kotlinx.coroutines.delay(ADHAN_DURATION_MS)
            isAdhanPlaying = false
        }
    }

    val isStreamMuted = !userSoundEnabled || isAdhanPlaying

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        when {
            errorMsg != null && data == null -> ErrorScreen(errorMsg!!)
            data == null -> LoadingScreen()
            else -> MainScreen(
                data = data,
                now = now,
                isAdhanPlaying = isAdhanPlaying,
                isStreamMuted = isStreamMuted,
                onToggleMute = { userSoundEnabled = !userSoundEnabled },
                onAdhanFinished = { isAdhanPlaying = false }
            )
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Loading Prayer Times…", color = Amber, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ErrorScreen(msg: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Error Loading Data", color = Color(0xFFEF4444), fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(msg, color = Color(0xFF9CA3AF), fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            Text("Retrying automatically…", color = Color(0xFF6B7280), fontSize = 14.sp)
        }
    }
}

@Composable
private fun MainScreen(
    data: PrayerData,
    now: Date,
    isAdhanPlaying: Boolean,
    isStreamMuted: Boolean,
    onToggleMute: () -> Unit,
    onAdhanFinished: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Header(data = data, now = now, modifier = Modifier.fillMaxWidth().weight(0.18f))
        Row(
            modifier = Modifier.fillMaxWidth().weight(0.82f).padding(horizontal = 28.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            Box(Modifier.weight(0.45f).fillMaxHeight()) {
                PrayerListView(data = data, now = now)
            }
            Box(Modifier.weight(0.55f).fillMaxHeight()) {
                StreamView(
                    isAdhanPlaying = isAdhanPlaying,
                    isStreamMuted = isStreamMuted,
                    onToggleMute = onToggleMute,
                    onAdhanFinished = onAdhanFinished
                )
            }
        }
    }
}

// ---- Header (logo / holiday banner + clock) ---------------------------------

@Composable
private fun Header(data: PrayerData, now: Date, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(horizontal = 28.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // LEFT: Holiday banner when active, otherwise the Marhaba Grill logo.
        val holiday = PrayerRepository.getHolidayMessage(data.hijriMonthNumber, data.hijriDay)
        if (holiday != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Image(
                    painter = painterResource(R.drawable.ic_lantern),
                    contentDescription = null,
                    modifier = Modifier.size(56.dp)
                )
                Column {
                    Text(holiday.en, color = Amber, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text(holiday.ar, color = AmberLight, fontSize = 22.sp)
                }
            }
        } else {
            Image(
                painter = painterResource(R.drawable.marhaba_logo),
                contentDescription = "Marhaba Grill",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxHeight().width(300.dp)
            )
        }

        // RIGHT: clock + dates
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    fmt(now, "h:mm a"),
                    color = Amber, fontSize = 52.sp, fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(fmt(now, "EEEE").uppercase(Locale.US), color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("|", color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp)
                    Text(fmtLocale(now, "EEEE", Locale("ar")), color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp)
                }
            }
            Box(
                Modifier
                    .width(1.dp)
                    .height(54.dp)
                    .background(Color.White.copy(alpha = 0.1f))
            )
            Column(horizontalAlignment = Alignment.End) {
                Text(fmt(now, "MMMM d"), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text("${data.hijriDay} ${data.hijriMonthAr}", color = Amber, fontSize = 18.sp)
            }
        }
    }
}

// ---- Prayer list ------------------------------------------------------------

private data class PrayerRow(val key: String, val label: String, val ar: String, val id: Int)

private val MAIN_PRAYERS = listOf(
    PrayerRow("Fajr", "Fajr", "الفجر", 1),
    PrayerRow("Dhuhr", "Dhuhr", "الظهر", 2),
    PrayerRow("Asr", "Asr", "العصر", 3),
    PrayerRow("Maghrib", "Maghrib", "المغرب", 4),
    PrayerRow("Isha", "Isha", "العشاء", 5),
)

@Composable
private fun PrayerListView(data: PrayerData, now: Date) {
    val next = computeNextPrayer(data.timings, now)
    val countdown = next?.let { formatCountdown(it.diffMs) } ?: ""

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Sunrise bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(CardBg)
                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(10.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Sunrise", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("الشروق", color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp)
            }
            Text(to12Hour(data.timings["Sunrise"]), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }

        // 5 daily prayers; highlight the next one (larger card + countdown).
        MAIN_PRAYERS.forEach { p ->
            val isNext = next?.name == p.key
            val time = to12Hour(data.timings[p.key])
            if (isNext) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(2.5f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(CardBg)
                        .border(2.dp, Amber.copy(alpha = 0.8f), RoundedCornerShape(20.dp))
                        .padding(20.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            NumberBadge(p.id, big = true)
                            Text(p.label, color = Amber, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                            Text(p.ar, color = Color.White.copy(alpha = 0.5f), fontSize = 22.sp)
                        }
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) { Text("NEXT", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                    }
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                        Text(time, color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.Bold)
                        if (countdown.isNotEmpty()) {
                            Text("Starts in $countdown", color = Amber, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardBg)
                        .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NumberBadge(p.id, big = false)
                        Text(p.label, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(p.ar, color = Color.White.copy(alpha = 0.3f), fontSize = 17.sp)
                    }
                    Text(time, color = Color.White.copy(alpha = 0.6f), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun NumberBadge(n: Int, big: Boolean) {
    Box(
        modifier = Modifier
            .size(if (big) 32.dp else 24.dp)
            .clip(RoundedCornerShape(if (big) 8.dp else 6.dp))
            .background(if (big) Amber else CardBg2),
        contentAlignment = Alignment.Center
    ) {
        Text(
            n.toString(),
            color = if (big) Color.Black else Color.White.copy(alpha = 0.4f),
            fontWeight = FontWeight.Bold,
            fontSize = if (big) 16.sp else 12.sp
        )
    }
}

// ---- Live stream ------------------------------------------------------------

@OptIn(UnstableApi::class)
@Composable
private fun StreamView(
    isAdhanPlaying: Boolean,
    isStreamMuted: Boolean,
    onToggleMute: () -> Unit,
    onAdhanFinished: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Resilient live player: on error it flips to the fallback URL and re-prepares,
    // retrying indefinitely so the stream self-heals over a 24/7 run.
    val urlIndex = remember { intArrayOf(0) }
    val urls = remember { listOf(MECCA_HLS_PRIMARY, MECCA_HLS_FALLBACK) }
    val handler = remember { Handler(Looper.getMainLooper()) }

    val livePlayer = remember {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("MarhabaTV/1.0")
            .setAllowCrossProtocolRedirects(true)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .build().apply {
                repeatMode = Player.REPEAT_MODE_ALL
                playWhenReady = true
                setMediaItem(MediaItem.fromUri(urls[0]))
                prepare()
            }
    }

    val adhanPlayer = remember { ExoPlayer.Builder(context).build() }

    // Error recovery for the live stream.
    DisposableEffect(livePlayer) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                handler.postDelayed({
                    try {
                        urlIndex[0] = (urlIndex[0] + 1) % urls.size
                        livePlayer.stop()
                        livePlayer.clearMediaItems()
                        livePlayer.setMediaItem(MediaItem.fromUri(urls[urlIndex[0]]))
                        livePlayer.prepare()
                        livePlayer.playWhenReady = true
                    } catch (_: Exception) {}
                }, 4000)
            }
        }
        livePlayer.addListener(listener)
        onDispose {
            livePlayer.removeListener(listener)
            livePlayer.release()
            adhanPlayer.release()
        }
    }

    // Pause/resume with the activity lifecycle (saves resources, avoids stale buffers).
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME -> {
                    livePlayer.playWhenReady = true
                    livePlayer.play()
                }
                Lifecycle.Event.ON_STOP -> livePlayer.pause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Mute follows app state (muted by default, and during the adhan).
    LaunchedEffect(isStreamMuted) {
        livePlayer.volume = if (isStreamMuted) 0f else 1f
    }

    // When the adhan audio finishes playing, close the adhan window immediately
    // (so the stream returns) instead of waiting out the safety-cap timer.
    DisposableEffect(adhanPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) onAdhanFinished()
            }
        }
        adhanPlayer.addListener(listener)
        onDispose { adhanPlayer.removeListener(listener) }
    }

    // Play the adhan aloud during the adhan window.
    LaunchedEffect(isAdhanPlaying) {
        if (isAdhanPlaying) {
            try {
                adhanPlayer.setMediaItem(MediaItem.fromUri(ADHAN_URL))
                adhanPlayer.volume = 1f
                adhanPlayer.prepare()
                adhanPlayer.playWhenReady = true
                adhanPlayer.play()
            } catch (_: Exception) {}
        } else {
            try {
                adhanPlayer.stop()
                adhanPlayer.clearMediaItems()
            } catch (_: Exception) {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black)
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = livePlayer
                    useController = false
                    setShutterBackgroundColor(AndroidColor.BLACK)
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    setKeepContentOnPlayerReset(true)
                }
            }
        )

        // LIVE badge (top-left)
        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFFB91C1C))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) { Text("LIVE", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }

        // Makkah Live label (bottom-left)
        Row(
            Modifier.align(Alignment.BottomStart).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF22C55E)))
            Text("MAKKAH LIVE", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }

        // Mute / unmute (top-right)
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f))
                .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape)
                .clickable { onToggleMute() },
            contentAlignment = Alignment.Center
        ) {
            SpeakerIcon(muted = isStreamMuted)
        }

        // Adhan overlay
        if (isAdhanPlaying) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.9f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Adhan", color = Amber, fontSize = 60.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Call to Prayer", color = Color.White.copy(alpha = 0.6f), fontSize = 22.sp)
                }
            }
        }
    }
}

// Simple speaker glyph drawn with Canvas (no extra icon dependency).
@Composable
private fun SpeakerIcon(muted: Boolean) {
    val c = Color.White.copy(alpha = 0.8f)
    Canvas(modifier = Modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        // speaker body
        drawRect(c, topLeft = androidx.compose.ui.geometry.Offset(w * 0.10f, h * 0.35f),
            size = androidx.compose.ui.geometry.Size(w * 0.18f, h * 0.30f))
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.28f, h * 0.35f)
            lineTo(w * 0.50f, h * 0.18f)
            lineTo(w * 0.50f, h * 0.82f)
            lineTo(w * 0.28f, h * 0.65f)
            close()
        }
        drawPath(path, c)
        if (muted) {
            drawLine(
                Color(0xFFEF4444),
                start = androidx.compose.ui.geometry.Offset(w * 0.60f, h * 0.30f),
                end = androidx.compose.ui.geometry.Offset(w * 0.90f, h * 0.70f),
                strokeWidth = w * 0.07f
            )
        } else {
            // two "sound" arcs
            drawLine(c, androidx.compose.ui.geometry.Offset(w * 0.62f, h * 0.40f),
                androidx.compose.ui.geometry.Offset(w * 0.62f, h * 0.60f), strokeWidth = w * 0.05f)
            drawLine(c, androidx.compose.ui.geometry.Offset(w * 0.78f, h * 0.32f),
                androidx.compose.ui.geometry.Offset(w * 0.78f, h * 0.68f), strokeWidth = w * 0.05f)
        }
    }
}

// ---- Helpers ----------------------------------------------------------------

private data class NextPrayer(val name: String, val diffMs: Long)

private fun computeNextPrayer(timings: Map<String, String>, now: Date): NextPrayer? {
    var best: NextPrayer? = null
    for (name in NEXT_PRAYER_CANDIDATES) {
        val t = timings[name] ?: continue
        val parts = t.split(":")
        if (parts.size < 2) continue
        val h = parts[0].toIntOrNull() ?: continue
        val m = parts[1].toIntOrNull() ?: continue
        val cal = Calendar.getInstance().apply {
            time = now
            set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val diff = cal.timeInMillis - now.time
        if (diff > 0 && (best == null || diff < best!!.diffMs)) {
            best = NextPrayer(name, diff)
        }
    }
    // After Isha all of today's times have passed — roll over to tomorrow's Fajr.
    if (best == null) {
        val fajr = timings["Fajr"]?.split(":")
        val h = fajr?.getOrNull(0)?.toIntOrNull()
        val m = fajr?.getOrNull(1)?.toIntOrNull()
        if (h != null && m != null) {
            val cal = Calendar.getInstance().apply {
                time = now
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            best = NextPrayer("Fajr", cal.timeInMillis - now.time)
        }
    }
    return best
}

private fun formatCountdown(ms: Long): String {
    val h = ms / (1000 * 60 * 60)
    val m = (ms % (1000 * 60 * 60)) / (1000 * 60)
    return "${h}h ${m}m"
}

private fun to12Hour(time24: String?): String {
    if (time24.isNullOrBlank()) return "--:--"
    val parts = time24.split(":")
    val h = parts.getOrNull(0)?.toIntOrNull() ?: return "--:--"
    val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val period = if (h >= 12) "PM" else "AM"
    val h12 = h % 12
    val hh = if (h12 == 0) 12 else h12
    return String.format(Locale.US, "%d:%02d %s", hh, m, period)
}

private fun millisUntilAfterMidnight(): Long {
    val now = Calendar.getInstance()
    val next = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 30); set(Calendar.MILLISECOND, 0)
    }
    return (next.timeInMillis - now.timeInMillis).coerceAtLeast(60_000L)
}

private fun fmt(date: Date, pattern: String): String =
    SimpleDateFormat(pattern, Locale.US).format(date)

private fun fmtLocale(date: Date, pattern: String, locale: Locale): String =
    SimpleDateFormat(pattern, locale).format(date)

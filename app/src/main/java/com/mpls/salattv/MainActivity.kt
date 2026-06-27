package com.mpls.salattv

import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

private const val MECCA_HLS_PRIMARY =
    "https://cdn-globecast.akamaized.net/live/eds/saudi_quran/hls_roku/index.m3u8"
private const val MECCA_HLS_FALLBACK =
    "http://m.live.net.sa:1935/live/quran/playlist.m3u8"
// The 24/7 Quran audio = the uploaded MP3s, hosted on a GitHub Release and streamed
// IN ORDER (no shuffle), looping. The Makkah video itself is always muted.
private const val QURAN_BASE =
    "https://github.com/marhabamediterranean-png/marhaba-grill-tv/releases/download/quran-audio/"

private data class Surah(val file: String, val name: String)

// The exact 38 files you provided, in order, with surah names for the now-playing bar.
private val QURAN_TRACKS = listOf(
    Surah("001.mp3", "Al-Fatihah"), Surah("002.mp3", "Al-Baqarah"), Surah("003.mp3", "Aal-E-Imran"),
    Surah("004.mp3", "An-Nisa"), Surah("005.mp3", "Al-Ma'idah"), Surah("006.mp3", "Al-An'am"),
    Surah("012.mp3", "Yusuf"), Surah("014.mp3", "Ibrahim"), Surah("017.mp3", "Al-Isra"),
    Surah("018.mp3", "Al-Kahf"), Surah("019.mp3", "Maryam"), Surah("020.mp3", "Ta-Ha"),
    Surah("023.mp3", "Al-Mu'minun"), Surah("025.mp3", "Al-Furqan"), Surah("030.mp3", "Ar-Rum"),
    Surah("032.mp3", "As-Sajdah"), Surah("033.mp3", "Al-Ahzab"), Surah("035.mp3", "Fatir"),
    Surah("036.mp3", "Ya-Sin"), Surah("039.mp3", "Az-Zumar"), Surah("049.mp3", "Al-Hujurat"),
    Surah("050.mp3", "Qaf"), Surah("055.mp3", "Ar-Rahman"), Surah("059.mp3", "Al-Hashr"),
    Surah("066.mp3", "At-Tahrim"), Surah("069.mp3", "Al-Haqqah"), Surah("073.mp3", "Al-Muzzammil"),
    Surah("085.mp3", "Al-Buruj"), Surah("086.mp3", "At-Tariq"), Surah("087.mp3", "Al-A'la"),
    Surah("089.mp3", "Al-Fajr"), Surah("090.mp3", "Al-Balad"), Surah("091.mp3", "Ash-Shams"),
    Surah("092.mp3", "Al-Layl"), Surah("093.mp3", "Ad-Duha"), Surah("097.mp3", "Al-Qadr"),
    Surah("099.mp3", "Az-Zalzalah"), Surah("108.mp3", "Al-Kawthar")
)
// At each prayer time, ALL audio is muted for this long (adhan + prayer), then Quran resumes.
private const val PRAYER_MUTE_WINDOW_MS = 15L * 60L * 1000L
private const val UPDATE_CHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L // every 6 hours

// ---- Theme colors -----------------------------------------------------------

private val Amber = Color(0xFFF59E0B)
private val AmberLight = Color(0xFFFBBF24)
private val CardBg = Color(0xFF1A1A1A)
private val CardBg2 = Color(0xFF2A2A2A)

private val ADHAN_PRAYERS = listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")
private val NEXT_PRAYER_CANDIDATES = listOf("Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha")

class MainActivity : ComponentActivity() {

    private lateinit var insetsController: WindowInsetsControllerCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        insetsController = WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        forceImmersive()
        setContent { MarhabaApp() }
    }

    private fun forceImmersive() {
        try { insetsController.hide(WindowInsetsCompat.Type.systemBars()) } catch (_: Exception) {}
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
    val context = LocalContext.current
    var prayerData by remember { mutableStateOf<PrayerData?>(null) }
    var weather by remember { mutableStateOf<WeatherData?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableStateOf(Date()) }
    // Quran audio is ON by default; the user can mute it. Video is always silent.
    var userSoundEnabled by remember { mutableStateOf(true) }
    // Epoch millis until which ALL audio stays muted after a prayer time.
    var muteUntil by remember { mutableStateOf(0L) }

    // Clock tick (minute resolution displayed).
    LaunchedEffect(Unit) {
        while (true) { now = Date(); kotlinx.coroutines.delay(1000L) }
    }

    // Prayer data: load now, reload just after midnight, forever.
    LaunchedEffect(Unit) {
        while (true) {
            try {
                prayerData = PrayerRepository.fetchPrayerData()
                errorMsg = null
            } catch (e: Exception) {
                if (prayerData == null) errorMsg = "Failed to load prayer times."
            }
            val delayMs = if (prayerData == null) 60_000L else millisUntilAfterMidnight()
            kotlinx.coroutines.delay(delayMs)
        }
    }

    // Weather: refresh every 15 minutes.
    LaunchedEffect(Unit) {
        while (true) {
            WeatherRepository.fetch()?.let { weather = it }
            kotlinx.coroutines.delay(15L * 60L * 1000L)
        }
    }

    // Over-the-air update checks.
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(15_000L) // small delay after launch
            UpdateChecker.check()?.let { UpdateChecker.downloadAndInstall(context, it) }
            kotlinx.coroutines.delay(UPDATE_CHECK_INTERVAL_MS)
        }
    }

    // Prayer-time trigger: when the clock hits a prayer time, mute everything for the window.
    val data = prayerData
    LaunchedEffect(now, data) {
        if (data == null) return@LaunchedEffect
        if (now.time >= muteUntil) {
            val cal = Calendar.getInstance().apply { time = now }
            val cur = String.format(
                Locale.US, "%02d:%02d",
                cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE)
            )
            if (ADHAN_PRAYERS.any { data.timings[it] == cur }) {
                muteUntil = now.time + PRAYER_MUTE_WINDOW_MS
            }
        }
    }

    // Are we inside the prayer/adhan mute window right now?
    val isPrayerMute = now.time < muteUntil
    // Quran audio is muted if the user muted it, or during the prayer window.
    val isQuranMuted = !userSoundEnabled || isPrayerMute

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        // Overscan-safe inset so nothing gets cropped by the TV's edge cropping.
        val hPad = maxWidth * 0.035f
        val vPad = maxHeight * 0.05f
        Box(Modifier.fillMaxSize().padding(horizontal = hPad, vertical = vPad)) {
            when {
                errorMsg != null && data == null -> ErrorScreen(errorMsg!!)
                data == null -> LoadingScreen()
                else -> MainScreen(
                    data = data,
                    weather = weather,
                    now = now,
                    isPrayerMute = isPrayerMute,
                    isQuranMuted = isQuranMuted,
                    onToggleMute = { userSoundEnabled = !userSoundEnabled }
                )
            }
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
    weather: WeatherData?,
    now: Date,
    isPrayerMute: Boolean,
    isQuranMuted: Boolean,
    onToggleMute: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Header(data = data, now = now, modifier = Modifier.fillMaxWidth().weight(0.19f))
        Row(
            modifier = Modifier.fillMaxWidth().weight(0.81f).padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Left: weather/sunrise glass pane + prayer times
            Column(Modifier.weight(0.45f).fillMaxHeight()) {
                GlassPane(weather = weather, sunrise = data.timings["Sunrise"], modifier = Modifier.fillMaxWidth().weight(1f))
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().weight(3.4f)) {
                    PrayerListView(data = data, now = now)
                }
            }
            // Right: live stream
            Box(Modifier.weight(0.55f).fillMaxHeight()) {
                StreamView(
                    isPrayerMute = isPrayerMute,
                    isQuranMuted = isQuranMuted,
                    onToggleMute = onToggleMute
                )
            }
        }
    }
}

// ---- Header (logo / holiday banner + clock) ---------------------------------

@Composable
private fun Header(data: PrayerData, now: Date, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val holiday = PrayerRepository.getHolidayMessage(data.hijriMonthNumber, data.hijriDay)
        // Logo (or holiday banner) centered within the left region of the header.
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            if (holiday != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Image(
                        painter = painterResource(R.drawable.ic_lantern),
                        contentDescription = null,
                        modifier = Modifier.size(60.dp)
                    )
                    Column {
                        Text(holiday.en, color = Amber, fontSize = 30.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(holiday.ar, color = AmberLight, fontSize = 24.sp, maxLines = 1)
                    }
                }
            } else {
                Image(
                    painter = painterResource(R.drawable.marhaba_logo),
                    contentDescription = "Marhaba Grill",
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.Center,
                    modifier = Modifier.fillMaxHeight().widthIn(max = 380.dp)
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    fmt(now, "h:mm a"),
                    color = Amber, fontSize = 46.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Visible
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(fmt(now, "EEEE").uppercase(Locale.US), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text("|", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp)
                    Text(fmtLocale(now, "EEEE", Locale("ar")), color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp, maxLines = 1)
                }
            }
            Box(Modifier.width(1.dp).height(52.dp).background(Color.White.copy(alpha = 0.1f)))
            Column(horizontalAlignment = Alignment.End) {
                Text(fmt(now, "MMMM d"), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                Text("${data.hijriDay} ${data.hijriMonthAr}", color = Amber, fontSize = 17.sp, maxLines = 1, softWrap = false)
            }
        }
    }
}

// ---- Weather + Sunrise glass pane -------------------------------------------

@Composable
private fun GlassPane(weather: WeatherData?, sunrise: String?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.04f))
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(22.dp))
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Weather block
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            WeatherIcon(kind = weather?.kind ?: WeatherKind.CLOUD, isDay = weather?.isDay ?: true, modifier = Modifier.size(54.dp))
            Column {
                Text(
                    weather?.let { "${it.tempF}°F" } ?: "--°",
                    color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false
                )
                Text(
                    weather?.condition ?: "Weather",
                    color = Amber, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false
                )
                Text("Minneapolis", color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp, maxLines = 1)
            }
        }

        Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 6.dp).background(Color.White.copy(alpha = 0.12f)))

        // Sunrise block
        Column(
            modifier = Modifier.padding(start = 16.dp),
            horizontalAlignment = Alignment.End
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SunriseIcon(Modifier.size(26.dp))
                Text("Sunrise", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("الشروق", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp, maxLines = 1)
            }
            Text(to12Hour(sunrise), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
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
        MAIN_PRAYERS.forEach { p ->
            val isNext = next?.name == p.key
            val time = to12Hour(data.timings[p.key])
            if (isNext) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth().weight(2.7f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(CardBg)
                        .border(2.dp, Amber.copy(alpha = 0.8f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            NumberBadge(p.id, big = true)
                            Text(p.label, color = Amber, fontSize = 28.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            Text(p.ar, color = Color.White.copy(alpha = 0.5f), fontSize = 18.sp, maxLines = 1)
                        }
                        Box(
                            Modifier.clip(RoundedCornerShape(4.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) { Text("NEXT", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        if (countdown.isNotEmpty()) {
                            Text("Starts in\n$countdown", color = Amber, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                        } else {
                            Spacer(Modifier.width(1.dp))
                        }
                        Text(time, color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth().weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardBg)
                        .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NumberBadge(p.id, big = false)
                        Text(p.label, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                        Text(p.ar, color = Color.White.copy(alpha = 0.3f), fontSize = 16.sp, maxLines = 1)
                    }
                    Text(time, color = Color.White.copy(alpha = 0.65f), fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
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
            fontWeight = FontWeight.Bold, fontSize = if (big) 16.sp else 12.sp, maxLines = 1
        )
    }
}

// ---- Weather icons (animated) ----------------------------------------------

@Composable
private fun WeatherIcon(kind: WeatherKind, isDay: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "weather")
    val rot by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "rot"
    )
    val fall by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "fall"
    )
    val flash by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), repeatMode = RepeatMode.Reverse), label = "flash"
    )

    val sun = Color(0xFFFBBF24)
    val cloud = Color(0xFFD1D5DB)
    val rainC = Color(0xFF60A5FA)
    val snowC = Color(0xFFE5E7EB)
    val bolt = Color(0xFFFBBF24)

    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        fun drawSun(cx: Float, cy: Float, r: Float) {
            rotate(rot, pivot = Offset(cx, cy)) {
                for (i in 0 until 8) {
                    rotate(i * 45f, pivot = Offset(cx, cy)) {
                        drawLine(sun, Offset(cx, cy - r - r * 0.5f), Offset(cx, cy - r - r * 1.1f), strokeWidth = w * 0.04f, cap = StrokeCap.Round)
                    }
                }
            }
            drawCircle(sun, r, Offset(cx, cy))
        }
        fun drawCloud(cx: Float, cy: Float, s: Float, c: Color) {
            drawCircle(c, s * 0.5f, Offset(cx - s * 0.5f, cy))
            drawCircle(c, s * 0.65f, Offset(cx, cy - s * 0.15f))
            drawCircle(c, s * 0.5f, Offset(cx + s * 0.55f, cy))
            drawRect(c, topLeft = Offset(cx - s, cy), size = Size(s * 2f, s * 0.6f))
        }
        when (kind) {
            WeatherKind.CLEAR -> drawSun(w * 0.5f, h * 0.5f, w * 0.20f)
            WeatherKind.PARTLY -> {
                drawSun(w * 0.36f, h * 0.36f, w * 0.15f)
                drawCloud(w * 0.58f, h * 0.62f, w * 0.22f, cloud)
            }
            WeatherKind.CLOUD, WeatherKind.FOG -> {
                drawCloud(w * 0.5f, h * 0.5f, w * 0.26f, cloud)
                if (kind == WeatherKind.FOG) {
                    for (i in 0 until 3) {
                        val y = h * (0.7f + i * 0.1f)
                        drawLine(cloud.copy(alpha = 0.6f), Offset(w * 0.2f, y), Offset(w * 0.8f, y), strokeWidth = w * 0.03f, cap = StrokeCap.Round)
                    }
                }
            }
            WeatherKind.RAIN -> {
                drawCloud(w * 0.5f, h * 0.42f, w * 0.24f, cloud)
                for (i in 0 until 3) {
                    val phase = (fall + i / 3f) % 1f
                    val x = w * (0.32f + i * 0.18f)
                    val y = h * (0.62f + phase * 0.28f)
                    drawLine(rainC, Offset(x, y), Offset(x, y + h * 0.10f), strokeWidth = w * 0.035f, cap = StrokeCap.Round)
                }
            }
            WeatherKind.SNOW -> {
                drawCloud(w * 0.5f, h * 0.42f, w * 0.24f, cloud)
                for (i in 0 until 3) {
                    val phase = (fall + i / 3f) % 1f
                    val x = w * (0.32f + i * 0.18f)
                    val y = h * (0.64f + phase * 0.26f)
                    drawCircle(snowC, w * 0.035f, Offset(x, y))
                }
            }
            WeatherKind.STORM -> {
                drawCloud(w * 0.5f, h * 0.40f, w * 0.24f, cloud)
                val p = Path().apply {
                    moveTo(w * 0.52f, h * 0.55f)
                    lineTo(w * 0.42f, h * 0.75f)
                    lineTo(w * 0.5f, h * 0.75f)
                    lineTo(w * 0.44f, h * 0.92f)
                    lineTo(w * 0.62f, h * 0.66f)
                    lineTo(w * 0.52f, h * 0.66f)
                    close()
                }
                drawPath(p, bolt.copy(alpha = 0.4f + 0.6f * flash))
            }
        }
    }
}

@Composable
private fun SunriseIcon(modifier: Modifier = Modifier) {
    val c = Amber
    val transition = rememberInfiniteTransition(label = "sunrise")
    val rise by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "rise"
    )
    // Rays continuously sweep around the dome; the sun itself gently rises.
    val sweep by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing)),
        label = "sweep"
    )
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        val horizonY = h * 0.78f
        val cx = w * 0.5f
        val r = w * 0.16f
        // Sun sits at the horizon and rises a little (clipped, so it's a dome).
        val cy = horizonY - r * 0.30f - rise * (r * 0.5f)
        val rayGap = r * 0.32f
        val rayLen = r * 0.55f
        clipRect(left = 0f, top = 0f, right = w, bottom = horizonY) {
            // 8 rays rotating around the sun; those below the horizon are clipped away,
            // so they appear to travel around the visible half-circle (dome).
            val n = 8
            for (i in 0 until n) {
                val rad = Math.toRadians((sweep + i * (360.0 / n)))
                val dx = kotlin.math.cos(rad).toFloat()
                val dy = kotlin.math.sin(rad).toFloat()
                drawLine(
                    c,
                    Offset(cx + dx * (r + rayGap), cy + dy * (r + rayGap)),
                    Offset(cx + dx * (r + rayGap + rayLen), cy + dy * (r + rayGap + rayLen)),
                    strokeWidth = w * 0.05f, cap = StrokeCap.Round
                )
            }
            drawCircle(c, r, Offset(cx, cy))
        }
        // Horizon line drawn on top, full width.
        drawLine(c, Offset(w * 0.05f, horizonY), Offset(w * 0.95f, horizonY), strokeWidth = w * 0.09f, cap = StrokeCap.Round)
    }
}

// ---- Live stream ------------------------------------------------------------

@OptIn(UnstableApi::class)
@Composable
private fun StreamView(
    isPrayerMute: Boolean,
    isQuranMuted: Boolean,
    onToggleMute: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val urlIndex = remember { intArrayOf(0) }
    val urls = remember { listOf(MECCA_HLS_PRIMARY, MECCA_HLS_FALLBACK) }
    val handler = remember { Handler(Looper.getMainLooper()) }

    // Makkah video — ALWAYS silent. Audio comes from the Quran track below.
    val livePlayer = remember {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("MarhabaTV/1.0")
            .setAllowCrossProtocolRedirects(true)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .build().apply {
                repeatMode = Player.REPEAT_MODE_ALL
                volume = 0f // video is permanently muted
                playWhenReady = true
                setMediaItem(MediaItem.fromUri(urls[0]))
                prepare()
            }
    }

    // 24/7 Quran — the uploaded surahs, IN ORDER, looping. Muted during prayer or if user mutes.
    val quranPlayer = remember {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("MarhabaTV/1.0")
            .setAllowCrossProtocolRedirects(true)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .build().apply {
                repeatMode = Player.REPEAT_MODE_ALL
                shuffleModeEnabled = false
                QURAN_TRACKS.forEach { addMediaItem(MediaItem.fromUri(QURAN_BASE + it.file)) }
                playWhenReady = true
                prepare()
            }
    }

    // Current surah name shown in the now-playing bar.
    var currentSurah by remember { mutableStateOf(QURAN_TRACKS.firstOrNull()?.name ?: "") }

    // Self-healing reconnect for both streams.
    DisposableEffect(livePlayer, quranPlayer) {
        val liveListener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                handler.postDelayed({
                    try {
                        urlIndex[0] = (urlIndex[0] + 1) % urls.size
                        livePlayer.stop(); livePlayer.clearMediaItems()
                        livePlayer.setMediaItem(MediaItem.fromUri(urls[urlIndex[0]]))
                        livePlayer.prepare(); livePlayer.playWhenReady = true
                    } catch (_: Exception) {}
                }, 4000)
            }
        }
        val quranListener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                handler.postDelayed({
                    try {
                        // Skip the failed surah and keep the playlist going.
                        if (quranPlayer.hasNextMediaItem()) quranPlayer.seekToNextMediaItem()
                        else quranPlayer.seekTo(0, 0L)
                        quranPlayer.prepare(); quranPlayer.playWhenReady = true
                    } catch (_: Exception) {}
                }, 3000)
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentSurah = QURAN_TRACKS.getOrNull(quranPlayer.currentMediaItemIndex)?.name ?: currentSurah
            }
        }
        livePlayer.addListener(liveListener)
        quranPlayer.addListener(quranListener)
        onDispose {
            livePlayer.removeListener(liveListener)
            quranPlayer.removeListener(quranListener)
            livePlayer.release()
            quranPlayer.release()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME -> {
                    livePlayer.playWhenReady = true; livePlayer.play()
                    quranPlayer.playWhenReady = true; quranPlayer.play()
                }
                Lifecycle.Event.ON_STOP -> { livePlayer.pause(); quranPlayer.pause() }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Quran follows the mute state; the video stays silent regardless.
    LaunchedEffect(isQuranMuted) { quranPlayer.volume = if (isQuranMuted) 0f else 1f }

    Box(
        modifier = Modifier.fillMaxSize()
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
        Box(
            Modifier.align(Alignment.TopStart).padding(16.dp)
                .clip(RoundedCornerShape(3.dp)).background(Color(0xFFB91C1C)).padding(horizontal = 8.dp, vertical = 2.dp)
        ) { Text("LIVE", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1) }

        Row(
            Modifier.align(Alignment.TopStart).padding(start = 62.dp, top = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF22C55E)))
            Text("MAKKAH", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }

        // Bottom bar: now-playing player, doubling as the prayer/adhan mute status.
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.62f))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when {
                    isPrayerMute -> {
                        SpeakerIcon(muted = true)
                        Column {
                            Text("Sound Muted", color = Amber, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text("Prayer / Adhan time", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp, maxLines = 1)
                        }
                    }
                    isQuranMuted -> {
                        SpeakerIcon(muted = true)
                        Text("Quran audio muted", color = Color.White.copy(alpha = 0.8f), fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                    else -> {
                        EqualizerIcon()
                        Column {
                            Text("Now Playing · Holy Qur'an", color = Amber, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text("Surah $currentSurah", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }
            }
            // Mute toggle (controls the Quran audio).
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                    .clickable { onToggleMute() },
                contentAlignment = Alignment.Center
            ) { SpeakerIcon(muted = isQuranMuted) }
        }
    }
}

// Small animated equalizer to indicate audio is playing.
@Composable
private fun EqualizerIcon(modifier: Modifier = Modifier) {
    val tr = rememberInfiniteTransition(label = "eq")
    val a by tr.animateFloat(0.3f, 1f, infiniteRepeatable(tween(500, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    val b by tr.animateFloat(1f, 0.4f, infiniteRepeatable(tween(650, easing = LinearEasing), RepeatMode.Reverse), label = "b")
    val c by tr.animateFloat(0.5f, 1f, infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse), label = "c")
    Canvas(modifier = modifier.size(22.dp)) {
        val w = size.width; val h = size.height
        val bw = w * 0.22f
        val heights = listOf(a, b, c)
        heights.forEachIndexed { i, frac ->
            val x = w * (0.12f + i * 0.32f)
            val barH = h * frac
            drawRect(Amber, topLeft = Offset(x, h - barH), size = Size(bw, barH))
        }
    }
}

@Composable
private fun SpeakerIcon(muted: Boolean) {
    val c = Color.White.copy(alpha = 0.8f)
    Canvas(modifier = Modifier.size(20.dp)) {
        val w = size.width; val h = size.height
        drawRect(c, topLeft = Offset(w * 0.10f, h * 0.35f), size = Size(w * 0.18f, h * 0.30f))
        val path = Path().apply {
            moveTo(w * 0.28f, h * 0.35f); lineTo(w * 0.50f, h * 0.18f)
            lineTo(w * 0.50f, h * 0.82f); lineTo(w * 0.28f, h * 0.65f); close()
        }
        drawPath(path, c)
        if (muted) {
            drawLine(Color(0xFFEF4444), Offset(w * 0.60f, h * 0.30f), Offset(w * 0.90f, h * 0.70f), strokeWidth = w * 0.07f, cap = StrokeCap.Round)
        } else {
            drawLine(c, Offset(w * 0.62f, h * 0.40f), Offset(w * 0.62f, h * 0.60f), strokeWidth = w * 0.05f, cap = StrokeCap.Round)
            drawLine(c, Offset(w * 0.78f, h * 0.32f), Offset(w * 0.78f, h * 0.68f), strokeWidth = w * 0.05f, cap = StrokeCap.Round)
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
        if (diff > 0 && (best == null || diff < best!!.diffMs)) best = NextPrayer(name, diff)
    }
    if (best == null) {
        val fajr = timings["Fajr"]?.split(":")
        val h = fajr?.getOrNull(0)?.toIntOrNull()
        val m = fajr?.getOrNull(1)?.toIntOrNull()
        if (h != null && m != null) {
            val cal = Calendar.getInstance().apply {
                time = now; add(Calendar.DAY_OF_YEAR, 1)
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

private fun fmt(date: Date, pattern: String): String = SimpleDateFormat(pattern, Locale.US).format(date)
private fun fmtLocale(date: Date, pattern: String, locale: Locale): String = SimpleDateFormat(pattern, locale).format(date)

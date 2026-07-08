package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bluetooth.BluetoothConnectionState
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.LampViewModel
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                LampAppScreen()
            }
        }
    }
}

// Premium dark luxury palettes where background is deep charcoal and accent colors glow
object GradientThemes {
    val NeonSunset = listOf(
        Color(0xFF0A090D),
        Color(0xFF131018),
        Color(0xFF1C1322)
    )
    val OceanBreeze = listOf(
        Color(0xFF060D0F),
        Color(0xFF0A151C),
        Color(0xFF0F1E2A)
    )
    val ForestGlow = listOf(
        Color(0xFF070E08),
        Color(0xFF0F1A12),
        Color(0xFF16281C)
    )
    val CosmicNight = listOf(
        Color(0xFF07050E),
        Color(0xFF0F0B1A),
        Color(0xFF181130)
    )
    val BlackAndWhite = listOf(
         Color(0xFF000000),
         Color(0xFF111111),
         Color(0xFF222222)
    )

    fun getThemeBrush(themeName: String): Brush {
        val colors = when (themeName) {
            "Ocean Breeze" -> OceanBreeze
            "Forest Glow" -> ForestGlow
            "Cosmic Night" -> CosmicNight
            "Black & White" -> BlackAndWhite
            else -> NeonSunset
        }
        return Brush.verticalGradient(colors)
    }

    fun getThemeAccent(themeName: String): Color {
        return when (themeName) {
            "Ocean Breeze" -> Color(0xFF00FFCC)
            "Forest Glow" -> Color(0xFF5FF381)
            "Cosmic Night" -> Color(0xFFD431FF)
            "Black & White" -> Color(0xFFFFFFFF)
            else -> Color(0xFFFF483B) // Neon Sunset Red
        }
    }
}

@Composable
fun LampAppScreen(
    modifier: Modifier = Modifier,
    viewModel: LampViewModel = viewModel()
) {
    val context = LocalContext.current
    val config by viewModel.configState.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val connectedDeviceName by viewModel.connectedDeviceName.collectAsState()
    val pairedDevices by viewModel.pairedDevices.collectAsState()
    val dipperTimeRemaining by viewModel.dipperTimeRemaining.collectAsState()
    val alarmTimeRemaining by viewModel.alarmTimeRemaining.collectAsState()
    val alarmList by viewModel.alarms.collectAsState()
    val nextAlarm by viewModel.nextAlarm.collectAsState()
    val isAutoReconnecting by viewModel.isAutoReconnecting.collectAsState()

    var showDeviceDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showTimerSettings by remember { mutableStateOf(false) }
    var showAlarmSettings by remember { mutableStateOf(false) }

    var editingAlarm by remember { mutableStateOf<com.example.data.LampAlarm?>(null) }
    var showAddEditPanel by remember { mutableStateOf(false) }
    var formHour by remember { mutableStateOf(8) }
    var formMinute by remember { mutableStateOf(0) }
    var formTurnOn by remember { mutableStateOf(true) }

    // System Permissions Request Flow
    val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
    } else {
        arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN)
    }

    var permissionGranted by remember {
        mutableStateOf(viewModel.hasBluetoothPermission())
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.all { it }
        permissionGranted = granted
        if (granted) {
            viewModel.refreshDevices()
            Toast.makeText(context, "Bluetooth permission active!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Bluetooth permissions are required to connect HC-05", Toast.LENGTH_LONG).show()
        }
    }

    val currentAccent = GradientThemes.getThemeAccent(config.themeName)

    val isAnyPanelOpen = showTimerSettings || showAlarmSettings || showSettingsDialog || showDeviceDialog
    val backgroundBlur by animateDpAsState(
        targetValue = 0.dp,
        animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing)
    )

    val infiniteTransitionMain = rememberInfiniteTransition(label = "reconnect_pulse_main")
    val pulseAlpha by infiniteTransitionMain.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha_anim"
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        // Beautiful dark OLED atmospheric background with a soft glow
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(
                    colors = when (config.themeName) {
                        "Ocean Breeze" -> GradientThemes.OceanBreeze
                        "Forest Glow" -> GradientThemes.ForestGlow
                        "Cosmic Night" -> GradientThemes.CosmicNight
                        "Black & White" -> GradientThemes.BlackAndWhite
                        else -> GradientThemes.NeonSunset
                    }
                ))
                .drawBehind {
                    // Soft atmospheric central glowing spot of active accent color
                    val centerOffset = Offset(size.width * 0.5f, size.height * 0.35f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                currentAccent.copy(alpha = 0.14f),
                                Color.Transparent
                             ),
                            center = centerOffset,
                            radius = size.width * 0.82f
                        ),
                        radius = size.width * 0.82f,
                        center = centerOffset
                    )
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .blur(backgroundBlur)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Sleek HUD Top Header Block
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Spacer on the left to perfectly center the title (replacing menu icon)
                    Spacer(modifier = Modifier.size(42.dp))

                    // Centered Display Title
                    Text(
                        text = "LUMIX PRO",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif,
                            letterSpacing = 1.2.sp
                        )
                    )

                    // Right Settings/Configuration trigger icon
                    IconButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.05f))
                            .border(1.dp, Color.White.copy(alpha = 0.10f), CircleShape)
                            .testTag("app_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Configuration Profile",
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Sub-header stats row (matches "Energy Status" & "Cells temp: 34°C" in the screenshot!)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ENERGY STATUS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White.copy(alpha = 0.55f),
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif,
                            letterSpacing = 0.5.sp
                        )
                    )

                    Text(
                        text = when (connectionState) {
                            BluetoothConnectionState.Connected -> "Cells temp 34°C"
                            BluetoothConnectionState.Connecting -> "CELLS WARMING"
                            else -> "Cells standby"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White.copy(alpha = 0.45f),
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.2.sp
                        )
                    )
                }

                // Section 1: Circular Telemetry Gauge (Interactive Dial Centerpiece)
                val infiniteTransition = rememberInfiniteTransition(label = "core_pulse")
                        val glowPulse by infiniteTransition.animateFloat(
                            initialValue = 0.96f,
                            targetValue = 1.04f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1500, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "pulse_scale"
                        )

                        // Smooth animated percentage transition from 0% to 100% based on Lamp State!
                        val targetPercentage = if (config.lampState) 100f else 0f
                        val animatedPercentage by animateFloatAsState(
                            targetValue = targetPercentage,
                            animationSpec = tween(durationMillis = 750, easing = FastOutSlowInEasing),
                            label = "state_percent"
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // Circular Telemetry drawing behind
                            Box(
                                modifier = Modifier
                                    .size(240.dp)
                                    .drawBehind {
                                        val center = Offset(size.width / 2f, size.height / 2f)
                                        val radius = (size.width / 2f) - 10.dp.toPx()

                                        // Outer glass ring outline
                                        drawCircle(
                                            color = Color.White.copy(alpha = 0.04f),
                                            radius = radius + 6.dp.toPx(),
                                            style = Stroke(width = 1.dp.toPx())
                                        )

                                        // Draw the fine high-precision telemetry scale lines (tick marks)
                                        val totalTicks = 80
                                        for (i in 0 until totalTicks) {
                                            val angleDegrees = i * (360f / totalTicks)
                                            val angleRad = Math.toRadians(angleDegrees.toDouble()).toFloat()
                                            
                                            val isMajor = i % 5 == 0
                                            val tickLength = if (isMajor) 11.dp.toPx() else 5.dp.toPx()
                                            
                                            // Make ticks glow if the lamp is connected and turned ON
                                            val tickColor = if (config.lampState) {
                                                currentAccent.copy(alpha = if (isMajor) 0.85f else 0.35f)
                                            } else {
                                                Color.White.copy(alpha = if (isMajor) 0.25f else 0.08f)
                                            }

                                            val tickStart = radius - tickLength
                                            val startX = center.x + tickStart * cos(angleRad)
                                            val startY = center.y + tickStart * sin(angleRad)
                                            val endX = center.x + radius * cos(angleRad)
                                            val endY = center.y + radius * sin(angleRad)

                                            drawLine(
                                                color = tickColor,
                                                start = Offset(startX, startY),
                                                end = Offset(endX, endY),
                                                strokeWidth = if (isMajor) 1.5.dp.toPx() else 1.dp.toPx()
                                            )
                                        }

                                        // Concentric ambient neon pulse halos (reactive to Lamp on/off)
                                        if (config.lampState) {
                                            drawCircle(
                                                color = currentAccent.copy(alpha = 0.06f),
                                                radius = radius * 1.15f * glowPulse
                                            )
                                            drawCircle(
                                                brush = Brush.radialGradient(
                                                    colors = listOf(
                                                        currentAccent.copy(alpha = 0.14f),
                                                        Color.Transparent
                                                    ),
                                                    center = center,
                                                    radius = radius * 0.9f
                                                ),
                                                radius = radius * 0.9f
                                            )
                                        }
                                    }
                                    .align(Alignment.Center),
                                contentAlignment = Alignment.Center
                            ) {
                                // Inner clickable Glass Core representing the lamp switch
                                Card(
                                    onClick = {
                                        if (permissionGranted) {
                                            viewModel.toggleLamp()
                                        } else {
                                            launcher.launch(requiredPermissions)
                                        }
                                    },
                                    modifier = Modifier
                                        .size(135.dp)
                                        .testTag("lamp_toggle_button"),
                                    shape = CircleShape,
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (config.lampState) currentAccent.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.03f)
                                    ),
                                    border = BorderStroke(
                                        width = 1.2.dp,
                                        color = if (config.lampState) currentAccent.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.15f)
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        // Top dial labels
                                        Text(
                                            text = if (config.lampState) "ACTIVE" else "STANDBY",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = if (config.lampState) currentAccent else Color.White.copy(alpha = 0.4f),
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 1.sp
                                            )
                                        )

                                        Spacer(modifier = Modifier.height(4.dp))

                                        // Big high-tech percentage digits
                                        Text(
                                            text = String.format("%02d%%", animatedPercentage.toInt()),
                                            style = MaterialTheme.typography.displayMedium.copy(
                                                fontWeight = FontWeight.Light,
                                                fontFamily = FontFamily.Monospace,
                                                color = Color.White,
                                                letterSpacing = (-1.5).sp,
                                                fontSize = 38.sp
                                            )
                                        )

                                        Spacer(modifier = Modifier.height(4.dp))

                                        // High-end link range and countdown timers
                                        Column {
                                            if (dipperTimeRemaining != null && false) {
                                                val hrs = dipperTimeRemaining!! / 3600
                                                val mins = (dipperTimeRemaining!! % 3600) / 60
                                                val secs = dipperTimeRemaining!! % 60
                                                Text(
                                                    text = if (hrs > 0) {
                                                        String.format("%02d:%02d:%02d timer", hrs, mins, secs)
                                                    } else {
                                                        String.format("%02d:%02d timer", mins, secs)
                                                    },
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        color = Color.White.copy(alpha = 0.45f),
                                                        fontSize = 10.sp
                                                    )
                                                )
                                            }
                                            if (config.alarmEnabled && alarmTimeRemaining != null) {
                                                val hrs = alarmTimeRemaining!! / 3600
                                                val mins = (alarmTimeRemaining!! % 3600) / 60
                                                val secs = alarmTimeRemaining!! % 60
                                                Text(
                                                    text = "", fontSize = 0.sp,
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        color = currentAccent.copy(alpha = 0.9f),
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                )
                                            }
                                            if (true) {
                                                Text(
                                                    text = if (connectionState == BluetoothConnectionState.Connected) "~10m link range" else "offline mode",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        color = Color.White.copy(alpha = 0.45f),
                                                        fontSize = 10.sp
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Sub-telemetry status indicator (Left-aligned bottom label: "System stable")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                isAutoReconnecting -> Color(0xFFFFCC00).copy(alpha = pulseAlpha)
                                                connectionState is BluetoothConnectionState.Error -> Color(0xFFFC3D39)
                                                config.lampState -> Color(0xFF00FF66)
                                                else -> Color.White.copy(alpha = 0.3f)
                                            }
                                        )
                                )
                                Text(
                                    text = when {
                                        isAutoReconnecting -> "Reconnecting Link..."
                                        connectionState is BluetoothConnectionState.Error -> "Connection Lost / Error"
                                        else -> "System stable"
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = when {
                                            isAutoReconnecting -> Color(0xFFFFCC00)
                                            connectionState is BluetoothConnectionState.Error -> Color(0xFFFC3D39)
                                            config.lampState -> Color(0xFF00FF66)
                                            else -> Color.White.copy(alpha = 0.45f)
                                        },
                                        fontFamily = FontFamily.SansSerif
                                    )
                                )
                            }

                            Text(
                                text = "HC-05 LINK STATUS",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color.White.copy(alpha = 0.35f),
                                    fontSize = 9.sp,
                                    letterSpacing = 0.5.sp
                                )
                            )
                        }

                        // Section 2: Highly Polished Capsule Grid Layout (Directly mirroring the screenshot cards!)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(190.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Taller Capsule (Timer Card) — left column
                            Card(
                                onClick = { showTimerSettings = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                shape = RoundedCornerShape(28.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFF131217).copy(alpha = 0.7f)
                                ),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Column(modifier = Modifier.weight(1.5f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                text = "Timer",
                                                style = MaterialTheme.typography.titleSmall.copy(
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    letterSpacing = 0.5.sp
                                                )
                                            )
                                            Text(
                                                text = "Shutdown",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = Color.White.copy(alpha = 0.4f),
                                                    fontSize = 11.sp
                                                )
                                            )
                                        }

                                        // Turn on/off Timer Switch right on Home Screen card!
                                        Switch(
                                            checked = config.dipperEnabled,
                                            onCheckedChange = { checked ->
                                                viewModel.updateDipper(checked, config.dipperDurationMinutes)
                                            },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = currentAccent,
                                                checkedTrackColor = currentAccent.copy(alpha = 0.35f),
                                                uncheckedThumbColor = Color.White.copy(alpha = 0.4f),
                                                uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
                                            ),
                                            modifier = Modifier
                                                .scale(0.85f)
                                                .testTag("dipper_toggle_home")
                                        )
                                    }

                                    Column {
                                        val dipperHours = config.dipperDurationMinutes / 60
                                        val dipperMins = config.dipperDurationMinutes % 60
                                        val dipperText = if (dipperHours > 0) {
                                            String.format("%dh %02dm", dipperHours, dipperMins)
                                        } else {
                                            "${dipperMins}m"
                                        }
                                        Text(
                                            text = dipperText,
                                            style = MaterialTheme.typography.displaySmall.copy(
                                                fontWeight = FontWeight.ExtraBold,
                                                fontFamily = FontFamily.Monospace,
                                                color = Color.White,
                                                fontSize = 28.sp
                                            )
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = "Edit",
                                                tint = currentAccent.copy(alpha = 0.6f),
                                                modifier = Modifier.size(11.dp)
                                            )
                                            Text(
                                                text = "Tap to set/edit",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = currentAccent.copy(alpha = 0.6f),
                                                    fontSize = 10.sp
                                                )
                                            )
                                        }
                                    }

                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Status",
                                                style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.35f))
                                            )
                                            Text(
                                                text = if (config.dipperEnabled) "Active" else "Idle",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = if (config.dipperEnabled) currentAccent.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.4f)
                                                )
                                            )
                                        }

                                        if (config.dipperEnabled && dipperTimeRemaining != null) {
                                            val hrs = dipperTimeRemaining!! / 3600
                                            val mins = (dipperTimeRemaining!! % 3600) / 60
                                            val secs = dipperTimeRemaining!! % 60
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Time Left",
                                                    style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.35f))
                                                )
                                                Text(
                                                    text = String.format("%02dh %02dm %02ds", hrs, mins, secs),
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        color = currentAccent,
                                                        fontFamily = FontFamily.Monospace,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                )
                                            }
                                        }

                                        val progressFraction = if (config.dipperEnabled && dipperTimeRemaining != null) {
                                            val totalSecs = (config.dipperDurationMinutes * 60f).coerceAtLeast(1f)
                                            (dipperTimeRemaining!!.toFloat() / totalSecs).coerceIn(0f, 1f)
                                        } else if (config.dipperEnabled) {
                                            0.75f
                                        } else {
                                            0f
                                        }

                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(Color.White.copy(alpha = 0.08f))
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(progressFraction)
                                                    .fillMaxHeight()
                                                    .background(currentAccent)
                                            )
                                        }
                                    }
                                }
                            }

                            // Taller Capsule (Alarm Card) — right column
                            val nextScheduledAlarm = nextAlarm ?: alarmList.firstOrNull()
                            val hasActiveAlarm = nextAlarm != null

                            Card(
                                onClick = { showAlarmSettings = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                shape = RoundedCornerShape(28.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFF131217).copy(alpha = 0.7f)
                                ),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Column(modifier = Modifier.weight(1.5f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                text = "Alarms",
                                                style = MaterialTheme.typography.titleSmall.copy(
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    letterSpacing = 0.5.sp
                                                )
                                            )
                                            val badgeText = if (alarmList.size <= 1) "Light wake up" else "${alarmList.size} Alarms configured"
                                            Text(
                                                text = badgeText,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = Color.White.copy(alpha = 0.4f),
                                                    fontSize = 11.sp
                                                )
                                            )
                                        }

                                        // Turn on/off Alarm Switch on Home Screen card toggles the closest upcoming alarm
                                        Switch(
                                            checked = hasActiveAlarm,
                                            onCheckedChange = { checked ->
                                                nextScheduledAlarm?.let {
                                                    viewModel.toggleAlarm(it, checked)
                                                }
                                            },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = currentAccent,
                                                checkedTrackColor = currentAccent.copy(alpha = 0.35f),
                                                uncheckedThumbColor = Color.White.copy(alpha = 0.4f),
                                                uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
                                            ),
                                            modifier = Modifier
                                                .scale(0.85f)
                                                .testTag("alarm_toggle_home")
                                        )
                                    }

                                    Column {
                                        val displayHour = nextScheduledAlarm?.hour ?: 8
                                        val displayMin = nextScheduledAlarm?.minute ?: 0
                                        val alarmAmPm = if (displayHour >= 12) "PM" else "AM"
                                        val alarmHour12 = when {
                                            displayHour == 0 -> 12
                                            displayHour > 12 -> displayHour - 12
                                            else -> displayHour
                                        }
                                        val timeStrFormat = String.format("%02d:%02d", alarmHour12, displayMin)
                                        Row(
                                            verticalAlignment = Alignment.Bottom,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = timeStrFormat,
                                                style = MaterialTheme.typography.displaySmall.copy(
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = Color.White,
                                                    fontSize = 28.sp
                                                )
                                            )
                                            Text(
                                                text = alarmAmPm,
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    color = currentAccent,
                                                    fontWeight = FontWeight.Black,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 12.sp
                                                ),
                                                modifier = Modifier.padding(bottom = 3.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            val actionName = if (nextScheduledAlarm?.turnOn == true) "Turn ON" else "Turn OFF"
                                            val actionIcon = if (nextScheduledAlarm?.turnOn == true) Icons.Default.Check else Icons.Default.Close
                                            Icon(
                                                actionIcon,
                                                contentDescription = "Action",
                                                tint = currentAccent.copy(alpha = 0.6f),
                                                modifier = Modifier.size(11.dp)
                                            )
                                            Text(
                                                text = "Action: $actionName",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = currentAccent.copy(alpha = 0.6f),
                                                    fontSize = 10.sp
                                                )
                                            )
                                        }
                                    }

                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Status",
                                                style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.35f))
                                            )
                                            Text(
                                                text = if (hasActiveAlarm) "Active" else "Idle",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = if (hasActiveAlarm) currentAccent.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.4f)
                                                )
                                            )
                                        }

                                        if (hasActiveAlarm && alarmTimeRemaining != null) {
                                            val hrs = alarmTimeRemaining!! / 3600
                                            val mins = (alarmTimeRemaining!! % 3600) / 60
                                            val secs = alarmTimeRemaining!! % 60
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Time Left",
                                                    style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.35f))
                                                )
                                                Text(
                                                    text = String.format("%02dh %02dm %02ds", hrs, mins, secs),
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        color = currentAccent,
                                                        fontFamily = FontFamily.Monospace,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                )
                                            }
                                        }

                                        val progressFraction = if (hasActiveAlarm && alarmTimeRemaining != null) {
                                            (1f - (alarmTimeRemaining!!.toFloat() / (24f * 3600f))).coerceIn(0f, 1f)
                                        } else if (hasActiveAlarm) {
                                            0.75f
                                        } else {
                                            0f
                                        }

                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(Color.White.copy(alpha = 0.08f))
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(progressFraction)
                                                    .fillMaxHeight()
                                                    .background(currentAccent)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Floating panels / smoothly animated custom Dialog popups for Timer and Alarm settings
                        if (false && showTimerSettings) {
                            androidx.compose.ui.window.Dialog(
                                onDismissRequest = { showTimerSettings = false },
                                properties = androidx.compose.ui.window.DialogProperties(
                                    usePlatformDefaultWidth = false
                                )
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(0xD4050407))
                                        .clickable { showTimerSettings = false },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(0.95f)
                                            .wrapContentHeight()
                                            .shadow(
                                                elevation = 16.dp,
                                                shape = RoundedCornerShape(28.dp),
                                                clip = false,
                                                ambientColor = currentAccent.copy(alpha = 0.4f),
                                                spotColor = currentAccent.copy(alpha = 0.4f)
                                            )
                                            .background(
                                                brush = Brush.verticalGradient(
                                                    colors = listOf(
                                                        Color(0xBF100E17),
                                                        Color(0xA608070F)
                                                    )
                                                ),
                                                shape = RoundedCornerShape(28.dp)
                                            )
                                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(28.dp))
                                            .border(2.dp, currentAccent.copy(alpha = 0.12f), RoundedCornerShape(28.dp))
                                            .clickable(enabled = false) {}
                                            .padding(20.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(20.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column {
                                                    Text(
                                                        text = "Timer Configuration",
                                                        style = MaterialTheme.typography.titleMedium.copy(
                                                            color = Color.White,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    )
                                                    Text(
                                                        text = "Adjust light auto-shutdown duration",
                                                        style = MaterialTheme.typography.bodySmall.copy(
                                                            color = Color.White.copy(alpha = 0.5f)
                                                        )
                                                    )
                                                }
                                                IconButton(onClick = { showTimerSettings = false }) {
                                                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                                                }
                                            }

                                            // Time Display Header
                                             val timerHoursStr = config.dipperDurationMinutes / 60
                                             val timerMinsStr = config.dipperDurationMinutes % 60
                                             val timeDisplayStr = if (timerHoursStr > 0) {
                                                 String.format("%dh %02dm", timerHoursStr, timerMinsStr)
                                             } else {
                                                 String.format("%dm", timerMinsStr)
                                             }
                                             Row(
                                                 modifier = Modifier.fillMaxWidth(),
                                                 horizontalArrangement = Arrangement.Center,
                                                 verticalAlignment = Alignment.CenterVertically
                                             ) {
                                                 Text(
                                                     text = timeDisplayStr,
                                                     style = MaterialTheme.typography.headlineLarge.copy(
                                                         fontWeight = FontWeight.ExtraBold,
                                                         fontFamily = FontFamily.Monospace,
                                                         color = Color.White,
                                                         fontSize = 44.sp
                                                     )
                                                 )
                                             }

                                            // Side-by-side Hour and Minute selectors
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.Center,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                WheelColumnPicker(
                                                    value = timerHoursStr,
                                                    range = 0..12,
                                                    labelText = "HOURS",
                                                    accentColor = currentAccent,
                                                    onValueChange = { newHours ->
                                                        val newDuration = (newHours * 60 + timerMinsStr).coerceAtLeast(1)
                                                        viewModel.updateDipper(config.dipperEnabled, newDuration)
                                                    }
                                                )

                                                Spacer(modifier = Modifier.width(16.dp))

                                                Text(
                                                    text = ":",
                                                    color = Color.White,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 44.sp,
                                                    modifier = Modifier.padding(top = 22.dp)
                                                )

                                                Spacer(modifier = Modifier.width(16.dp))

                                                WheelColumnPicker(
                                                    value = timerMinsStr,
                                                    range = 0..59,
                                                    labelText = "MINUTES",
                                                    accentColor = currentAccent,
                                                    onValueChange = { newMins ->
                                                        val newDuration = (timerHoursStr * 60 + newMins).coerceAtLeast(1)
                                                        viewModel.updateDipper(config.dipperEnabled, newDuration)
                                                    }
                                                )
                                            }

                                            Button(
                                                onClick = { showTimerSettings = false },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = currentAccent
                                                ),
                                                shape = RoundedCornerShape(14.dp)
                                            ) {
                                                Text("Save & Close", color = Color.Black, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }



                        // Theme and Sync panel horizontally below (as a nice Row!)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(90.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Stack item 1: Glowing Theme Card
                            val nextTheme = when (config.themeName) {
                                "Neon Sunset" -> "Ocean Breeze"
                                "Ocean Breeze" -> "Forest Glow"
                                "Forest Glow" -> "Cosmic Night"
                                "Cosmic Night" -> "Black & White"
                                else -> "Neon Sunset"
                            }

                            Card(
                                onClick = { viewModel.updateTheme(nextTheme) },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .testTag("theme_sunset_button"),
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = currentAccent.copy(alpha = 0.12f)
                                ),
                                border = BorderStroke(
                                    1.3.dp,
                                    Brush.horizontalGradient(
                                        listOf(
                                            currentAccent.copy(alpha = 0.45f),
                                            Color.White.copy(alpha = 0.08f)
                                        )
                                    )
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        verticalArrangement = Arrangement.Center,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = "Theme Ambient",
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                        )
                                        Text(
                                            text = config.themeName.split(" ").first(),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = currentAccent,
                                                fontWeight = FontWeight.Black,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp
                                            )
                                        )
                                    }

                                    Icon(
                                        imageVector = Icons.Default.Palette,
                                        contentDescription = "Theme color",
                                        tint = currentAccent,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            // Stack item 2: Deep blue link channel capsule (Sync logic matching bottom-right capsule!)
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFF131217).copy(alpha = 0.7f)
                                ),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(verticalArrangement = Arrangement.Center) {
                                        Text(
                                            text = "Sync",
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                        )
                                        Text(
                                            text = when {
                                                connectionState is BluetoothConnectionState.Connected -> "Linked"
                                                connectionState is BluetoothConnectionState.Connecting -> "Linking..."
                                                isAutoReconnecting -> "Reconnecting..."
                                                else -> "Sync off"
                                            },
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = when {
                                                    connectionState is BluetoothConnectionState.Connected -> Color(0xFF5FF381)
                                                    isAutoReconnecting -> Color(0xFFFFCC00).copy(alpha = pulseAlpha)
                                                    connectionState is BluetoothConnectionState.Connecting -> currentAccent.copy(alpha = pulseAlpha)
                                                    else -> Color.White.copy(alpha = 0.4f)
                                                },
                                                fontSize = 11.sp
                                            )
                                        )
                                    }

                                    // Styled luxury Switch matching the screenshot
                                    Switch(
                                        checked = connectionState == BluetoothConnectionState.Connected,
                                        onCheckedChange = { checked ->
                                            if (checked) {
                                                if (permissionGranted) {
                                                    viewModel.refreshDevices()
                                                    showDeviceDialog = true
                                                } else {
                                                    launcher.launch(requiredPermissions)
                                                }
                                            } else {
                                                viewModel.disconnectDevice()
                                            }
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = currentAccent,
                                            checkedTrackColor = currentAccent.copy(alpha = 0.35f),
                                            uncheckedThumbColor = Color.White.copy(alpha = 0.4f),
                                            uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
                                        ),
                                        modifier = Modifier.testTag("bluetooth_connect_button")
                                    )
                                }
                            }
                        }

                        // Last connected information panel
                        if (config.lastDeviceAddress.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White.copy(alpha = 0.03f))
                                    .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bluetooth,
                                        contentDescription = "Bluetooth Mac",
                                        tint = currentAccent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "HC-05 MAC: ${config.lastDeviceAddress}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color.White.copy(alpha = 0.6f),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                                Text(
                                    text = config.lastDeviceName,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = currentAccent,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // App Developer Credit Line at Bottom
                        Text(
                            text = "Developed by Salman Irshad",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color.White.copy(alpha = 0.35f),
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            ),
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }
                }
            }

    // Bluetooth Paired Device Picker Dialog
    if (false && showDeviceDialog) {
        AlertDialog(
            onDismissRequest = { showDeviceDialog = false },
            title = {
                Text(
                    text = "Select HC-05 Device",
                    style = MaterialTheme.typography.titleMedium.copy(color = Color.White),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (pairedDevices.isEmpty()) {
                        Text(
                            text = "No paired devices found! Please pair HC-05 in your phone's system Bluetooth settings first.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    } else {
                        pairedDevices.forEach { (name, address) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (config.lastDeviceAddress == address) {
                                            currentAccent.copy(alpha = 0.15f)
                                        } else {
                                            Color.White.copy(alpha = 0.04f)
                                        }
                                    )
                                    .clickable {
                                        viewModel.connectDevice(address, name)
                                        showDeviceDialog = false
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bluetooth,
                                    contentDescription = "Device icon",
                                    tint = currentAccent,
                                    modifier = Modifier.padding(end = 12.dp)
                                )
                                Column {
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = address,
                                        style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.5f))
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDeviceDialog = false }) {
                    Text("Close", color = currentAccent)
                }
            },
            containerColor = Color(0xC7100E17),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .shadow(
                    elevation = 20.dp,
                    shape = RoundedCornerShape(24.dp),
                    clip = false,
                    ambientColor = currentAccent.copy(alpha = 0.4f),
                    spotColor = currentAccent.copy(alpha = 0.4f)
                )
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                .border(2.dp, currentAccent.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
        )
    }

    // Smart Configuration Dialog Overlay
    if (false && showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings Icon",
                            tint = currentAccent,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SMART CONFIGURATION",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    IconButton(onClick = { showSettingsDialog = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.5f))
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Feature B: Themes Selection Panel
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "GRAPHICAL BACKGROUNDS",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = Color.White.copy(alpha = 0.55f),
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val themes = listOf("Neon Sunset", "Ocean Breeze", "Forest Glow", "Cosmic Night", "Black & White")
                            themes.forEach { themeName ->
                                val isSel = config.themeName == themeName
                                Box(
                                    modifier = Modifier
                                        .width(96.dp)
                                        .aspectRatio(1.2f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(GradientThemes.getThemeBrush(themeName))
                                        .clickable { viewModel.updateTheme(themeName) }
                                        .border(
                                            width = if (isSel) 2.dp else 1.dp,
                                            color = if (isSel) Color.White else Color.White.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(14.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = themeName.split(" ").first(),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            shadow = Shadow(color = Color.Black, offset = Offset(0f, 1f), blurRadius = 2.5f)
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Feature C: About Salman Card (Our Passionate Creator!)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White.copy(alpha = 0.04f)
                        ),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = "Avatar profile",
                                    tint = currentAccent,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "SALMAN",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    Text(
                                        text = "@syntaxbysalman",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color.White.copy(alpha = 0.45f)
                                        )
                                    )
                                }
                            }
                            Text(
                                text = "A physical hardware developer and software craftsman passionate about creating intelligent IoT controllers. Creating solutions that bridge local hardware (Arduino/Relay/HC-05) with modern Jetpack Compose UIs to simplify room living.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color.White.copy(alpha = 0.7f),
                                    lineHeight = 16.sp
                                )
                            )
                        }
                    }

                    // Feature D: Contact & Social section (SyntaxBySalman!)
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "CONTACT CHANNELS",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = Color.White.copy(alpha = 0.55f),
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        )

                        val contacts = listOf(
                            Triple("YouTube", "https://youtube.com/@syntaxbysalman", Icons.Default.Share),
                            Triple("Instagram", "https://instagram.com/syntaxbysalman", Icons.Default.Person),
                            Triple("Twitter / X", "https://x.com/syntaxbysalman", Icons.Default.Share),
                            Triple("LinkedIn", "https://linkedin.com/in/syntaxbysalman", Icons.Default.Person),
                            Triple("Email ME", "mailto:syntaxbysalman@gmail.com", Icons.Default.Mail)
                        )

                        contacts.forEach { (name, urlStr, icon) ->
                            Card(
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(urlStr))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Could not open $name link", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color.White.copy(alpha = 0.03f)
                                ),
                                border = BorderStroke(0.6.dp, Color.White.copy(alpha = 0.06f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(icon, contentDescription = name, tint = currentAccent.copy(alpha = 0.85f), modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(name, style = MaterialTheme.typography.bodySmall.copy(color = Color.White, fontWeight = FontWeight.Medium))
                                    }
                                    Icon(Icons.Default.ChevronRight, contentDescription = "Go", tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }

                    // Feature E: Support section (Google Form error feedback)
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "SYSTEM SUPPORT",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = Color.White.copy(alpha = 0.55f),
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        )

                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://forms.gle/Wc3WPCJ2o475qdSy6"))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Could not load report feedback form", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = currentAccent)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                Icon(Icons.Default.BugReport, contentDescription = "Error Report", tint = if (currentAccent == Color.White) Color.Black else Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Report an Bug / Error",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = if (currentAccent == Color.White) Color.Black else Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("Apply & Close", color = currentAccent, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xC7100E17),
            shape = RoundedCornerShape(26.dp),
            modifier = Modifier
                .shadow(
                    elevation = 20.dp,
                    shape = RoundedCornerShape(26.dp),
                    clip = false,
                    ambientColor = currentAccent.copy(alpha = 0.4f),
                    spotColor = currentAccent.copy(alpha = 0.4f)
                )
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(26.dp))
                .border(2.dp, currentAccent.copy(alpha = 0.12f), RoundedCornerShape(26.dp))
        )
    }

    // ------------------- Premium Glassmorphic Selection Overlays (Z-Index Overlaid Inline on root Box) -------------------

    // 1. Timer Setup Overlay
    GlassmorphicOverlay(
        visible = showTimerSettings,
        onDismiss = { showTimerSettings = false },
        accentColor = currentAccent
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Timer Configuration",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "Adjust light auto-shutdown duration",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    )
                }
                IconButton(onClick = { showTimerSettings = false }) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            // Time Display Header
            val timerHoursStr = config.dipperDurationMinutes / 60
            val timerMinsStr = config.dipperDurationMinutes % 60
            val timeDisplayStr = if (timerHoursStr > 0) {
                String.format("%dh %02dm", timerHoursStr, timerMinsStr)
            } else {
                String.format("%dm", timerMinsStr)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = timeDisplayStr,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White,
                        fontSize = 44.sp
                    )
                )
            }

            // Side-by-side Hour and Minute selectors
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                WheelColumnPicker(
                    value = timerHoursStr,
                    range = 0..12,
                    labelText = "HOURS",
                    accentColor = currentAccent,
                    onValueChange = { newHours ->
                        val newDuration = (newHours * 60 + timerMinsStr).coerceAtLeast(1)
                        viewModel.updateDipper(config.dipperEnabled, newDuration)
                    }
                )

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = ":",
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 44.sp,
                    modifier = Modifier.padding(top = 22.dp)
                )

                Spacer(modifier = Modifier.width(16.dp))

                WheelColumnPicker(
                    value = timerMinsStr,
                    range = 0..59,
                    labelText = "MINUTES",
                    accentColor = currentAccent,
                    onValueChange = { newMins ->
                        val newDuration = (timerHoursStr * 60 + newMins).coerceAtLeast(1)
                        viewModel.updateDipper(config.dipperEnabled, newDuration)
                    }
                )
            }

            Button(
                onClick = { showTimerSettings = false },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = currentAccent
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Save & Close", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }

    // 2. Alarm Setup Overlay (Multiple Alarms Support)
    GlassmorphicOverlay(
        visible = showAlarmSettings,
        onDismiss = { 
            showAlarmSettings = false 
            showAddEditPanel = false
            editingAlarm = null
        },
        accentColor = currentAccent
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            if (!showAddEditPanel) {
                // LIST VIEW OF ALL ALARMS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "My Alarms",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = "Schedules to turn Lamp ON or OFF",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color.White.copy(alpha = 0.5f)
                            )
                        )
                    }
                    IconButton(
                        onClick = { 
                            showAlarmSettings = false 
                            showAddEditPanel = false
                            editingAlarm = null
                        }
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                // Scrollable container for multiple alarm configurations
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    alarmList.forEach { alarm ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = 0.04f))
                                .clickable {
                                    // Enter Edit Mode for this alarm
                                    editingAlarm = alarm
                                    formHour = alarm.hour
                                    formMinute = alarm.minute
                                    formTurnOn = alarm.turnOn
                                    showAddEditPanel = true
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val alarmHour12 = when {
                                alarm.hour == 0 -> 12
                                alarm.hour > 12 -> alarm.hour - 12
                                else -> alarm.hour
                            }
                            val alarmAmPm = if (alarm.hour >= 12) "PM" else "AM"

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Action indicator Lamp ON vs Lamp OFF
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (alarm.turnOn) currentAccent.copy(alpha = 0.15f)
                                            else Color.White.copy(alpha = 0.08f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (alarm.turnOn) Icons.Default.Check else Icons.Default.Close,
                                        contentDescription = if (alarm.turnOn) "ON" else "OFF",
                                        tint = if (alarm.turnOn) currentAccent else Color.White.copy(alpha = 0.4f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Column {
                                    Text(
                                        text = String.format("%02d:%02d %s", alarmHour12, alarm.minute, alarmAmPm),
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            color = if (alarm.enabled) Color.White else Color.White.copy(alpha = 0.4f),
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    )
                                    Text(
                                        text = if (alarm.turnOn) "Action: Turn ON Lamp" else "Action: Turn OFF Lamp",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = if (alarm.turnOn) currentAccent.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.35f)
                                        )
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Switch(
                                    checked = alarm.enabled,
                                    onCheckedChange = { checked ->
                                        viewModel.toggleAlarm(alarm, checked)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = currentAccent,
                                        checkedTrackColor = currentAccent.copy(alpha = 0.35f),
                                        uncheckedThumbColor = Color.White.copy(alpha = 0.4f),
                                        uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
                                    ),
                                    modifier = Modifier.scale(0.85f)
                                )

                                IconButton(
                                    onClick = { viewModel.deleteAlarm(alarm) }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Alarm",
                                        tint = Color.Red.copy(alpha = 0.7f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Action buttons: Add New & Save & Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            editingAlarm = null
                            formHour = 8
                            formMinute = 0
                            formTurnOn = true
                            showAddEditPanel = true
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, currentAccent.copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = currentAccent
                        )
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Alarm", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { showAlarmSettings = false },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = currentAccent
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Close", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                // ADD / EDIT FORM PANEL
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = { 
                                showAddEditPanel = false 
                                editingAlarm = null
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack, 
                                contentDescription = "Back to List", 
                                tint = Color.White
                            )
                        }
                        Text(
                            text = if (editingAlarm != null) "Edit Alarm" else "Add Alarm",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    IconButton(
                        onClick = { 
                            showAlarmSettings = false 
                            showAddEditPanel = false
                            editingAlarm = null
                        }
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                // 12-Hour formatted display
                val alarmAmPm = if (formHour >= 12) "PM" else "AM"
                val alarmHour12 = when {
                    formHour == 0 -> 12
                    formHour > 12 -> formHour - 12
                    else -> formHour
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.Bottom
                ) {
                    val timeStrFormat = String.format("%02d:%02d", alarmHour12, formMinute)
                    Text(
                        text = timeStrFormat,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White,
                            fontSize = 44.sp
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = alarmAmPm,
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = currentAccent,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 18.sp
                        ),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                // Samsung cylindrical Wheel Column Pickers side-by-side
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    WheelColumnPicker(
                        value = alarmHour12,
                        range = 1..12,
                        labelText = "HOUR",
                        accentColor = currentAccent,
                        onValueChange = { newHour12 ->
                            val isPm = alarmAmPm == "PM"
                            formHour = if (isPm) {
                                (newHour12 % 12) + 12
                            } else {
                                newHour12 % 12
                            }
                        }
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Text(
                        text = ":",
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 44.sp,
                        modifier = Modifier.padding(top = 22.dp)
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    WheelColumnPicker(
                        value = formMinute,
                        range = 0..59,
                        labelText = "MINUTE",
                        accentColor = currentAccent,
                        onValueChange = { newMin ->
                            formMinute = newMin
                        }
                    )
                }

                // AM / PM Segmented Selection Bar
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("AM", "PM").forEach { ampm ->
                        val isSelected = alarmAmPm == ampm
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) currentAccent else Color.Transparent)
                                .clickable {
                                    if (!isSelected) {
                                        val isPm = ampm == "PM"
                                        formHour = if (isPm) {
                                            (alarmHour12 % 12) + 12
                                        } else {
                                            alarmHour12 % 12
                                        }
                                    }
                                }
                                .padding(horizontal = 24.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = ampm,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = if (isSelected) Color.Black else Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }

                // Lamp Action Configuration selector (Turn ON or Turn OFF)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Lamp Action when triggered",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Card for Lamp ON Action
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (formTurnOn) currentAccent.copy(alpha = 0.12f)
                                    else Color.White.copy(alpha = 0.03f)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (formTurnOn) currentAccent else Color.White.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable { formTurnOn = true }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Turn ON",
                                    tint = if (formTurnOn) currentAccent else Color.White.copy(alpha = 0.3f),
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = "Turn Lamp ON",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = if (formTurnOn) Color.White else Color.White.copy(alpha = 0.5f),
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }

                        // Card for Lamp OFF Action
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (!formTurnOn) currentAccent.copy(alpha = 0.12f)
                                    else Color.White.copy(alpha = 0.03f)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (!formTurnOn) currentAccent else Color.White.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable { formTurnOn = false }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Turn OFF",
                                    tint = if (!formTurnOn) currentAccent else Color.White.copy(alpha = 0.3f),
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = "Turn Lamp OFF",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = if (!formTurnOn) Color.White else Color.White.copy(alpha = 0.5f),
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Form Action Buttons: Cancel and Confirm Save
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { 
                            showAddEditPanel = false 
                            editingAlarm = null
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        )
                    ) {
                        Text("Cancel", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val activeAlarm = editingAlarm
                            if (activeAlarm != null) {
                                // Save changes to existing alarm
                                viewModel.updateAlarmTime(activeAlarm, formHour, formMinute, formTurnOn)
                            } else {
                                // Create new alarm
                                viewModel.addAlarm(formHour, formMinute, formTurnOn)
                            }
                            showAddEditPanel = false
                            editingAlarm = null
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = currentAccent
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Confirm", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // 3. Bluetooth Selector Overlay (Glassmorphic)
    GlassmorphicOverlay(
        visible = showDeviceDialog,
        onDismiss = { showDeviceDialog = false },
        accentColor = currentAccent
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Select HC-05 Device",
                style = MaterialTheme.typography.titleMedium.copy(color = Color.White),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            if (pairedDevices.isEmpty()) {
                Text(
                    text = "No paired devices found! Please pair HC-05 in your phone's system Bluetooth settings first.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
            } else {
                pairedDevices.forEach { (name, address) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (config.lastDeviceAddress == address) {
                                    currentAccent.copy(alpha = 0.15f)
                                } else {
                                    Color.White.copy(alpha = 0.04f)
                                }
                            )
                            .clickable {
                                viewModel.connectDevice(address, name)
                                showDeviceDialog = false
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bluetooth,
                            contentDescription = "Device icon",
                            tint = currentAccent,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                        Column {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = address,
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.5f))
                            )
                        }
                    }
                }
            }
        }
    }

    // 4. Smart Configuration Overlay (Glassmorphic)
    GlassmorphicOverlay(
        visible = showSettingsDialog,
        onDismiss = { showSettingsDialog = false },
        accentColor = currentAccent
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings Icon",
                        tint = currentAccent,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SMART CONFIGURATION",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                IconButton(onClick = { showSettingsDialog = false }) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.5f))
                }
            }

            // Themes Selection Panel
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "GRAPHICAL BACKGROUNDS",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = Color.White.copy(alpha = 0.55f),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val themes = listOf("Neon Sunset", "Ocean Breeze", "Forest Glow", "Cosmic Night", "Black & White")
                    themes.forEach { themeName ->
                        val isSel = config.themeName == themeName
                        Box(
                            modifier = Modifier
                                .width(96.dp)
                                .aspectRatio(1.2f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(GradientThemes.getThemeBrush(themeName))
                                .clickable { viewModel.updateTheme(themeName) }
                                .border(
                                    width = if (isSel) 2.dp else 1.dp,
                                    color = if (isSel) Color.White else Color.White.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(14.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = themeName.split(" ").first(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    shadow = Shadow(color = Color.Black, offset = Offset(0f, 1f), blurRadius = 2.5f)
                                )
                            )
                        }
                    }
                }
            }

            // Auto Connect Toggle Setting Panel
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "AUTO CONNECTION MODULE",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = Color.White.copy(alpha = 0.55f),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.04f))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-connect on App Start",
                            style = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Instantly connects to HC-05 when you open or launch the application.",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.5f))
                        )
                    }
                    Switch(
                        checked = config.autoConnectEnabled,
                        onCheckedChange = { viewModel.updateAutoConnect(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = currentAccent,
                            checkedTrackColor = currentAccent.copy(alpha = 0.35f),
                            uncheckedThumbColor = Color.White.copy(alpha = 0.4f),
                            uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
                        ),
                        modifier = Modifier.testTag("auto_connect_switch")
                    )
                }
            }

            // About Salman Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.04f)
                ),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "Avatar profile",
                            tint = currentAccent,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "SALMAN",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = "@syntaxbysalman",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color.White.copy(alpha = 0.45f)
                                )
                            )
                        }
                    }
                    Text(
                        text = "A physical hardware developer and software craftsman passionate about creating intelligent IoT controllers. Creating solutions that bridge local hardware (Arduino/Relay/HC-05) with modern Jetpack Compose UIs to simplify room living.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.7f),
                            lineHeight = 16.sp
                        )
                    )
                }
            }

            // Contact Channels
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "CONTACT CHANNELS",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = Color.White.copy(alpha = 0.55f),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                )

                val contacts = listOf(
                    Triple("YouTube", "https://youtube.com/@syntaxbysalman", Icons.Default.Share),
                    Triple("Instagram", "https://instagram.com/syntaxbysalman", Icons.Default.Person),
                    Triple("Twitter / X", "https://x.com/syntaxbysalman", Icons.Default.Share),
                    Triple("LinkedIn", "https://linkedin.com/in/syntaxbysalman", Icons.Default.Person),
                    Triple("Email ME", "mailto:syntaxbysalman@gmail.com", Icons.Default.Mail)
                )

                contacts.forEach { (name, urlStr, icon) ->
                    Card(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(urlStr))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Could not open $name link", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White.copy(alpha = 0.03f)
                        ),
                        border = BorderStroke(0.6.dp, Color.White.copy(alpha = 0.06f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(icon, contentDescription = name, tint = currentAccent.copy(alpha = 0.85f), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(name, style = MaterialTheme.typography.bodySmall.copy(color = Color.White, fontWeight = FontWeight.Medium))
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = "Go", tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            // Support System
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "SYSTEM SUPPORT",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = Color.White.copy(alpha = 0.55f),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                )

                Button(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://forms.gle/Wc3WPCJ2o475qdSy6"))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not load report feedback form", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = currentAccent)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Icon(Icons.Default.BugReport, contentDescription = "Error Report", tint = if (currentAccent == Color.White) Color.Black else Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Report an Bug / Error",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = if (currentAccent == Color.White) Color.Black else Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }
    }
}

// Gorgeous Custom Navigation Bar featuring the Yellow/Gold Glowing Pill matching the screenshot!
@Composable
fun LuxuryBottomNavigation(
    currentTab: String,
    onTabSelected: (String) -> Unit,
    accentColor: Color
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        color = Color(0xFF0C0A0F),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val tabs = listOf(
                TabItem("CONTROLLER", Icons.Default.Bolt),
                TabItem("SUPPORT", Icons.Default.BugReport),
                TabItem("CONTACT", Icons.Default.Share),
                TabItem("ABOUT", Icons.Default.Person)
            )
            tabs.forEach { tab ->
                val isSelected = currentTab == tab.name

                if (isSelected) {
                    // Custom Glowing Pill layout matching the highlighted yellow asset at the bottom of the screenshot!
                    Box(
                        modifier = Modifier
                            .height(44.dp)
                            .widthIn(min = 90.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(accentColor) // Cozy yellow gold/accent glow
                            .clickable { onTabSelected(tab.name) }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.name,
                                tint = Color.Black, // High-contrast black icon inside active glowing pill!
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = if (tab.name == "CONTROLLER") "Control" else tab.name.lowercase().capitalize(),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = Color.Black,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                } else {
                    // Dimmed inactive standard tab representation
                    Column(
                        modifier = Modifier
                            .height(48.dp)
                            .width(64.dp)
                            .clip(CircleShape)
                            .clickable { onTabSelected(tab.name) }
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.name,
                            tint = Color.White.copy(alpha = 0.45f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

data class TabItem(val name: String, val icon: ImageVector)

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    accentColor: Color,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(20.dp),
                clip = false,
                ambientColor = accentColor.copy(alpha = 0.5f)
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.35f)
        ),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
    ) {
        content()
    }
}

@Composable
fun rememberCustomSnappingFlingBehavior(lazyListState: androidx.compose.foundation.lazy.LazyListState): androidx.compose.foundation.gestures.FlingBehavior {
    return remember(lazyListState) {
        object : androidx.compose.foundation.gestures.FlingBehavior {
            override suspend fun androidx.compose.foundation.gestures.ScrollScope.performFling(initialVelocity: Float): Float {
                val layoutInfo = lazyListState.layoutInfo
                val visibleItems = layoutInfo.visibleItemsInfo
                if (visibleItems.isEmpty()) return 0f

                // Find the item that is closest to the viewport start / center
                val containerCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
                var closestItem = visibleItems.first()
                var minDiff = Float.MAX_VALUE
                for (item in visibleItems) {
                    val itemCenter = item.offset + item.size / 2f
                    val diff = kotlin.math.abs(itemCenter - containerCenter)
                    if (diff < minDiff) {
                        minDiff = diff
                        closestItem = item
                    }
                }

                // Smoothly animate snap to the target index!
                lazyListState.animateScrollToItem(closestItem.index)
                return 0f
            }
        }
    }
}

@Composable
fun WheelColumnPicker(
    value: Int,
    range: IntRange,
    labelText: String,
    accentColor: Color,
    onValueChange: (Int) -> Unit
) {
    val list = range.toList()
    val listSize = list.size
    
    // Virtual infinite size range to achieve looping / infinite cyclic scrolling
    val virtualSize = 1000 * listSize
    val initialIndex = 500 * listSize + list.indexOf(value).coerceAtLeast(0)
    
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    
    // Smooth physics-based fling snapping with natural momentum
    val flingBehavior = rememberCustomSnappingFlingBehavior(listState)
    
    // Synchronize selected center item to parent state
    val centerIndex = listState.firstVisibleItemIndex
    val activeValue = list[centerIndex % listSize]
    
    LaunchedEffect(activeValue) {
        if (activeValue != value) {
            onValueChange(activeValue)
        }
    }
    
    // Keep list scrolling in sync if state updates externally
    LaunchedEffect(value) {
        val currentCenterIndex = listState.firstVisibleItemIndex
        val currentMod = currentCenterIndex % listSize
        val targetMod = list.indexOf(value).coerceAtLeast(0)
        val diff = targetMod - currentMod
        if (diff != 0) {
            listState.scrollToItem(currentCenterIndex + diff)
        }
    }

    val itemHeight = 44.dp
    val itemHeightPx = with(LocalDensity.current) { itemHeight.toPx() }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(85.dp)
    ) {
        Text(
            text = labelText,
            color = Color.White.copy(alpha = 0.45f),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            ),
            modifier = Modifier.padding(bottom = 6.dp)
        )
        
        Box(
            modifier = Modifier
                .height(132.dp) // exactly 3 items visible at any time
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            // Elegant selections background pill layout (Gold/Ambient border highlighting center item)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                    .border(1.dp, accentColor.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            )
            
            LazyColumn(
                state = listState,
                flingBehavior = flingBehavior,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 44.dp), // centers first and last item in active row
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                items(virtualSize) { index ->
                    val itemValue = list[index % listSize]
                    
                    // Live perspective coordinate translation and scaling
                    val indexOffset = index - listState.firstVisibleItemIndex
                    val offsetPercent = indexOffset - (listState.firstVisibleItemScrollOffset.toFloat() / itemHeightPx)
                    
                    // 3D curving formulas!
                    val alpha = (1f - kotlin.math.abs(offsetPercent) * 0.65f).coerceIn(0.15f, 1f)
                    val scale = (1.15f - kotlin.math.abs(offsetPercent) * 0.3f).coerceIn(0.75f, 1.2f)
                    val rotationX = -offsetPercent * 38f
                    val translationYFactor = -offsetPercent * 5f
                    
                    Box(
                        modifier = Modifier
                            .height(44.dp)
                            .fillMaxWidth()
                            .graphicsLayer {
                                this.alpha = alpha
                                this.scaleX = scale
                                this.scaleY = scale
                                this.rotationX = rotationX
                                this.translationY = translationYFactor * 1.dp.toPx()
                                this.cameraDistance = 8f * density
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = String.format("%02d", itemValue),
                            style = MaterialTheme.typography.titleLarge.copy(
                                color = if (kotlin.math.abs(offsetPercent) < 0.5f) Color.White else Color.White.copy(alpha = 0.45f),
                                fontWeight = if (kotlin.math.abs(offsetPercent) < 0.5f) FontWeight.Bold else FontWeight.Medium,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 28.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

data class SocialItem(
    val name: String,
    val icon: ImageVector,
    val url: String
)

@Composable
fun GlassmorphicOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    accentColor: Color,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(300)) + slideInVertically(animationSpec = tween(350, easing = EaseOutCubic)) { it / 2 },
        exit = fadeOut(animationSpec = tween(250)) + slideOutVertically(animationSpec = tween(350, easing = EaseInCubic)) { it / 2 }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f)) // Translucent focus scrim overlay
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() }
                .padding(horizontal = 16.dp, vertical = 24.dp),
            contentAlignment = Alignment.BottomCenter // Align from bottom for easy one-hand operation
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.96f)
                    .wrapContentHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* prevent backdrop clicks dismissal */ }
                    .shadow(
                        elevation = 20.dp,
                        shape = RoundedCornerShape(24.dp),
                        clip = false,
                        ambientColor = accentColor.copy(alpha = 0.25f),
                        spotColor = accentColor.copy(alpha = 0.25f)
                    )
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFA14121F), // 98% opaque luxury dark container
                                Color(0xFA0B0A12)
                            )
                        ),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                    .border(2.dp, accentColor.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                content()
            }
        }
    }
}

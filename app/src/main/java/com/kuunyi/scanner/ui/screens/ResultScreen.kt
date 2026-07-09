package com.kuunyi.scanner.ui.screens

import android.app.Activity
import androidx.compose.animation.core.animateFloatAsState
import com.kuunyi.scanner.util.FeedbackManager
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kuunyi.scanner.data.ScanMode
import com.kuunyi.scanner.data.ScanResult
import com.kuunyi.scanner.ui.theme.Green
import com.kuunyi.scanner.ui.theme.InterTightFamily
import com.kuunyi.scanner.ui.theme.Red
import com.kuunyi.scanner.viewmodel.ScannerViewModel
import kotlinx.coroutines.delay

@Composable
fun ResultScreen(vm: ScannerViewModel) {
    val result by vm.currentResult.collectAsStateWithLifecycle()
    val scanMode by vm.scanMode.collectAsStateWithLifecycle()
    val soundEnabled by vm.soundEnabled.collectAsStateWithLifecycle()
    val vibrateEnabled by vm.vibrateEnabled.collectAsStateWithLifecycle()

    // Dark status bar (light icons on coloured background)
    val view = LocalView.current
    val context = LocalContext.current
    val window = (context as Activity).window
    SideEffect {
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
    }

    val r = result ?: return
    val bgColor = if (r is ScanResult.Valid) Green else Red

    // Sound + vibration feedback on first appearance
    val feedback = remember { FeedbackManager(context) }
    DisposableEffect(Unit) { onDispose { feedback.release() } }
    LaunchedEffect(r) {
        val isValid = r is ScanResult.Valid
        if (soundEnabled) { if (isValid) feedback.playValid() else feedback.playInvalid() }
        if (vibrateEnabled) { if (isValid) feedback.vibrateValid() else feedback.vibrateInvalid() }
    }

    // Entrance animation
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.85f,
        animationSpec = tween(durationMillis = 220),
        label = "result-scale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "result-alpha"
    )

    // Auto-advance in continuous mode after a valid scan
    LaunchedEffect(r) {
        if (r is ScanResult.Valid && scanMode == ScanMode.CONTINUOUS) {
            delay(2_000)
            vm.onNext()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Spacer(Modifier.weight(1f))

        // Icon + text block
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .scale(scale)
                .alpha(alpha)
        ) {
            // Circle icon
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(118.dp)
                    .background(Color.White, CircleShape)
            ) {
                if (r is ScanResult.Valid) CheckIcon(tint = Green)
                else CrossIcon(tint = Red)
            }

            Spacer(Modifier.height(26.dp))

            // Headline
            val headline = when (r) {
                is ScanResult.Valid         -> "VALID"
                is ScanResult.AlreadyUsed   -> "ALREADY USED"
                is ScanResult.FakeTicket    -> "FAKE TICKET"
                is ScanResult.Expired       -> "EXPIRED"
                is ScanResult.WrongEntrance -> "WRONG ENTRANCE"
            }
            val headlineSize = when (r) {
                is ScanResult.AlreadyUsed, is ScanResult.WrongEntrance -> 30.sp
                else -> if (r is ScanResult.Valid) 40.sp else 34.sp
            }
            Text(
                text = headline,
                fontFamily = InterTightFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = headlineSize,
                letterSpacing = 0.5.sp,
                lineHeight = headlineSize,
                color = Color.White,
            )

            Spacer(Modifier.height(6.dp))

            val sub = when (r) {
                is ScanResult.Valid         -> "Allow entry"
                is ScanResult.AlreadyUsed   -> "Do not admit"
                is ScanResult.FakeTicket    -> "Signature invalid \u00b7 do not admit"
                is ScanResult.Expired       -> "Outside valid time window"
                is ScanResult.WrongEntrance -> "Send to correct gate"
            }
            Text(text = sub, fontSize = 14.sp, color = Color.White.copy(alpha = 0.85f))

            Spacer(Modifier.height(26.dp))

            // Detail card
            when (r) {
                is ScanResult.Valid -> {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .background(
                                Color.White.copy(alpha = 0.18f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 26.dp, vertical = 12.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                r.tier,
                                fontFamily = InterTightFamily,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 30.sp,
                                lineHeight = 30.sp,
                                color = Color.White,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "TICKET TIER",
                                fontSize = 11.sp,
                                letterSpacing = 0.5.sp,
                                color = Color.White.copy(alpha = 0.85f),
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "${r.ticketId} \u00b7 ${r.admits} admit",
                        fontSize = 11.5.sp,
                        color = Color.White.copy(alpha = 0.75f),
                    )
                }

                is ScanResult.AlreadyUsed -> {
                    Column(
                        modifier = Modifier
                            .width(196.dp)
                            .background(Color.Black.copy(alpha = 0.16f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 18.dp, vertical = 14.dp)
                    ) {
                        Text(
                            "FIRST SCANNED",
                            fontSize = 11.sp,
                            letterSpacing = 0.5.sp,
                            color = Color.White.copy(alpha = 0.75f),
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            r.firstScanTime,
                            fontFamily = InterTightFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = Color.White,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(r.firstScanGate, fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f))
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "${r.tier} \u00b7 ${r.ticketId}",
                        fontSize = 11.5.sp,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                }

                is ScanResult.FakeTicket -> {
                    Box(
                        modifier = Modifier
                            .width(196.dp)
                            .background(Color.Black.copy(alpha = 0.16f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 18.dp, vertical = 13.dp)
                    ) {
                        Text(
                            "This QR was not issued by the platform, or has been tampered with.",
                            fontSize = 12.5.sp,
                            lineHeight = 17.sp,
                            color = Color.White.copy(alpha = 0.92f),
                        )
                    }
                }

                is ScanResult.Expired -> {
                    Column(
                        modifier = Modifier
                            .width(196.dp)
                            .background(Color.Black.copy(alpha = 0.16f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 18.dp, vertical = 14.dp)
                    ) {
                        Text(
                            "VALID FOR",
                            fontSize = 11.sp,
                            letterSpacing = 0.5.sp,
                            color = Color.White.copy(alpha = 0.75f),
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            r.validFor,
                            fontFamily = InterTightFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = Color.White,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Scanned ${r.scannedAt}",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "${r.tier} \u00b7 ${r.ticketId}",
                        fontSize = 11.5.sp,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                }

                is ScanResult.WrongEntrance -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // This ticket tier
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.18f), RoundedCornerShape(11.dp))
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Text(
                                r.ticketTier,
                                fontFamily = InterTightFamily,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 22.sp,
                                lineHeight = 22.sp,
                                color = Color.White,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "THIS TICKET",
                                fontSize = 9.5.sp,
                                color = Color.White.copy(alpha = 0.8f),
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("\u2192", fontSize = 20.sp, color = Color.White)
                        Spacer(Modifier.width(10.dp))
                        // This gate tier
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(11.dp))
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Text(
                                r.gateTier,
                                fontFamily = InterTightFamily,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 22.sp,
                                lineHeight = 22.sp,
                                color = Red,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "THIS GATE",
                                fontSize = 9.5.sp,
                                color = Red,
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(r.ticketId, fontSize = 11.5.sp, color = Color.White.copy(alpha = 0.8f))
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // Action button
        val btnText = when (r) {
            is ScanResult.Valid, is ScanResult.AlreadyUsed -> "Next ticket"
            else -> "Scan again"
        }
        Box(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 18.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(13.dp))
                    .clickable { vm.onNext() }
                    .padding(vertical = 15.dp)
            ) {
                Text(
                    text = btnText,
                    fontFamily = InterTightFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = bgColor,
                )
            }
        }

        NavPill(light = true)
    }
}

@Composable
private fun CheckIcon(tint: Color) {
    androidx.compose.foundation.Canvas(modifier = Modifier.size(62.dp)) {
        val strokePx = 8.dp.toPx()
        val path = Path().apply {
            moveTo(14.dp.toPx(), 33.dp.toPx())
            lineTo(26.dp.toPx(), 45.dp.toPx())
            lineTo(48.dp.toPx(), 19.dp.toPx())
        }
        drawPath(path, tint, style = Stroke(strokePx, cap = StrokeCap.Round,
            join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

@Composable
private fun CrossIcon(tint: Color) {
    androidx.compose.foundation.Canvas(modifier = Modifier.size(58.dp)) {
        val strokePx = 8.dp.toPx()
        drawLine(tint, androidx.compose.ui.geometry.Offset(17.dp.toPx(), 17.dp.toPx()),
            androidx.compose.ui.geometry.Offset(41.dp.toPx(), 41.dp.toPx()), strokePx, StrokeCap.Round)
        drawLine(tint, androidx.compose.ui.geometry.Offset(41.dp.toPx(), 17.dp.toPx()),
            androidx.compose.ui.geometry.Offset(17.dp.toPx(), 41.dp.toPx()), strokePx, StrokeCap.Round)
    }
}

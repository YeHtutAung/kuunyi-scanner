package com.kuunyi.scanner.ui.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.common.InputImage
import com.kuunyi.scanner.data.ScanMode
import com.kuunyi.scanner.data.ScanResult
import com.kuunyi.scanner.ui.theme.InterTightFamily
import com.kuunyi.scanner.ui.theme.ScannerBar
import com.kuunyi.scanner.ui.theme.ScannerBg
import com.kuunyi.scanner.ui.theme.ScannerToggleBg
import com.kuunyi.scanner.viewmodel.ScannerViewModel
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun ScannerScreen(vm: ScannerViewModel) {
    val selectedEvent by vm.selectedEvent.collectAsStateWithLifecycle()
    val scanMode by vm.scanMode.collectAsStateWithLifecycle()
    val scanCount by vm.scanCount.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val toastMessage by vm.toastMessage.collectAsStateWithLifecycle()
    val scanResetKey by vm.scanResetKey.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Dark status bar (light icons on dark background)
    val view = LocalView.current
    val window = (context as Activity).window
    SideEffect {
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
    }

    // In TAP mode the user must press a button before each scan
    var tapReady by remember { mutableStateOf(false) }
    val scanEnabled = scanMode == ScanMode.CONTINUOUS || tapReady

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    SideEffect {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            vm.clearToast()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ScannerBg)
    ) {
        // Camera preview fills the background
        if (hasCameraPermission) {
            CameraPreview(
                scanEnabled = scanEnabled,
                scanResetKey = scanResetKey,
                onBarcodeDetected = {
                    tapReady = false
                    vm.onBarcodeDetected(it)
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // Hatched placeholder when no permission
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize().background(Color(0xFF333333))
            ) {
                Text("Camera permission required", color = Color(0xFF777777), fontSize = 13.sp)
            }
        }

        // UI overlay
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(Modifier.statusBarsPadding())

            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = selectedEvent.name,
                        fontFamily = InterTightFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = Color.White,
                    )
                    Text(
                        text = selectedEvent.location,
                        fontSize = 10.5.sp,
                        color = Color(0xFFBBBBBB),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Scan counter badge
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .background(
                                Color.White.copy(alpha = 0.14f),
                                RoundedCornerShape(999.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "%,d".format(scanCount),
                            fontFamily = InterTightFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            lineHeight = 16.sp,
                            color = Color.White,
                        )
                        Text(
                            text = "SCANNED",
                            fontSize = 8.5.sp,
                            letterSpacing = 0.5.sp,
                            color = Color(0xFFCCCCCC),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = vm::openSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color(0xFFBBBBBB),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }

            // Camera area with viewfinder overlay
            Box(modifier = Modifier.weight(1f)) {
                // Viewfinder corners drawn on Canvas
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokePx = 4.dp.toPx()
                    val cornerLen = 34.dp.toPx()
                    val boxSize = 186.dp.toPx()
                    val radius = 6.dp.toPx()
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val l = cx - boxSize / 2f
                    val t = cy - boxSize / 2f
                    val r = cx + boxSize / 2f
                    val b = cy + boxSize / 2f

                    val corners = listOf(
                        // top-left
                        Path().apply {
                            moveTo(l, t + cornerLen)
                            lineTo(l, t + radius)
                            quadraticTo(l, t, l + radius, t)
                            lineTo(l + cornerLen, t)
                        },
                        // top-right
                        Path().apply {
                            moveTo(r - cornerLen, t)
                            lineTo(r - radius, t)
                            quadraticTo(r, t, r, t + radius)
                            lineTo(r, t + cornerLen)
                        },
                        // bottom-left
                        Path().apply {
                            moveTo(l, b - cornerLen)
                            lineTo(l, b - radius)
                            quadraticTo(l, b, l + radius, b)
                            lineTo(l + cornerLen, b)
                        },
                        // bottom-right
                        Path().apply {
                            moveTo(r - cornerLen, b)
                            lineTo(r - radius, b)
                            quadraticTo(r, b, r, b - radius)
                            lineTo(r, b - cornerLen)
                        },
                    )
                    corners.forEach { path ->
                        drawPath(path, Color.White, style = Stroke(strokePx, cap = StrokeCap.Round))
                    }
                }

                if (scanMode == ScanMode.CONTINUOUS) {
                    // Hint label
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 54.dp)
                            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(999.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Point at the ticket QR", fontSize = 12.5.sp, color = Color.White)
                    }
                } else {
                    // Tap-to-scan shutter button
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 40.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(72.dp)
                                .background(Color.White.copy(alpha = 0.15f), CircleShape)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(58.dp)
                                    .background(
                                        if (tapReady) Color.White.copy(alpha = 0.6f) else Color.White,
                                        CircleShape
                                    )
                                    .clickable { tapReady = true }
                            )
                        }
                    }
                }

                // Demo panel — top-right corner, for testing without real QR codes
                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val demos = listOf(
                        "VALID" to ScanResult.Valid("#TKT-9F2A31", "VIP", 1),
                        "USED" to ScanResult.AlreadyUsed("#TKT-4B77C0", "GA", "Today · 8:52 AM", "Gate — Main Arena"),
                        "FAKE" to ScanResult.FakeTicket("#TKT-FAKE01"),
                        "EXPIRED" to ScanResult.Expired("#TKT-2210AA", "GA", "Jul 11 · 6:00–10:00 PM", "Jul 12 · 9:41 AM"),
                        "ZONE" to ScanResult.WrongEntrance("#TKT-77C931", "GA", "VIP"),
                    )
                    demos.forEach { (label, result) ->
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .background(
                                    Color.Black.copy(alpha = 0.5f),
                                    RoundedCornerShape(4.dp)
                                )
                                .clickable { vm.onDemoResult(result) }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(label, fontSize = 9.sp, color = Color.White)
                        }
                    }
                }
            }

            // Mode toggle bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ScannerBar)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .padding(bottom = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ScannerToggleBg, RoundedCornerShape(999.dp))
                        .padding(4.dp)
                ) {
                    listOf(ScanMode.CONTINUOUS to "Continuous", ScanMode.TAP to "Tap to scan")
                        .forEach { (mode, label) ->
                            val isActive = scanMode == mode
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        if (isActive) Color.White else Color.Transparent,
                                        RoundedCornerShape(999.dp)
                                    )
                                    .clickable { vm.setScanMode(mode) }
                                    .padding(vertical = 9.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontFamily = InterTightFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.5.sp,
                                    color = if (isActive) Color(0xFF1C1C1C) else Color(0xFFBBBBBB),
                                )
                            }
                        }
                }
            }

            Spacer(Modifier.navigationBarsPadding())
            NavPill(light = true)
        }

        if (loading) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
            ) {
                androidx.compose.material3.CircularProgressIndicator(color = Color.White)
            }
        }
    }
}

@Composable
private fun CameraPreview(
    scanEnabled: Boolean,
    scanResetKey: Int,
    onBarcodeDetected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    DisposableEffect(lifecycleOwner, scanResetKey) {
        val detected = AtomicBoolean(false)   // fresh on each key change
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        val barcodeScanner = BarcodeScanning.getClient(options)
        val executor = ContextCompat.getMainExecutor(context)
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        var cameraProvider: ProcessCameraProvider? = null

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analysis.setAnalyzer(executor) { proxy: ImageProxy ->
                if (!scanEnabled || detected.get()) { proxy.close(); return@setAnalyzer }
                val mediaImage = proxy.image
                if (mediaImage == null) { proxy.close(); return@setAnalyzer }
                val image = InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees)
                barcodeScanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        barcodes.firstOrNull()?.rawValue?.let { raw ->
                            if (detected.compareAndSet(false, true)) {
                                onBarcodeDetected(raw)
                            }
                        }
                    }
                    .addOnCompleteListener { proxy.close() }
            }

            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )
            } catch (_: Exception) { }
        }, executor)

        onDispose {
            cameraProvider?.unbindAll()
            barcodeScanner.close()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

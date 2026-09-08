package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.example.desktop.DesktopConnectionManager
import com.example.desktop.PairingManager
import com.example.security.SecurityHardening
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(
    navController: NavController,
    pairingManager: PairingManager = PairingManager.getInstance(LocalContext.current),
    connectionManager: DesktopConnectionManager = DesktopConnectionManager.getInstance(LocalContext.current)
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: QR Scan, 1: Manual Entry
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            Toast.makeText(context, "Camera permission needed to scan QR code. You can also enter IP manually.", Toast.LENGTH_LONG).show()
        }
    }

    var manualHost by remember { mutableStateOf("") }
    var manualPort by remember { mutableStateOf("8443") }
    var manualHttpPort by remember { mutableStateOf("8444") }
    var manualCode by remember { mutableStateOf("") }
    var manualFingerprint by remember { mutableStateOf("") }
    var isPairing by remember { mutableStateOf(false) }
    var pairingErrorMessage by remember { mutableStateOf<String?>(null) }

    fun executePairing(host: String, port: Int, httpPort: Int, code: String, fingerprint: String?) {
        if (host.isBlank() || code.isBlank()) {
            pairingErrorMessage = "Please provide valid host and pairing code."
            return
        }

        isPairing = true
        pairingErrorMessage = null

        coroutineScope.launch {
            val result = pairingManager.pair(
                host = host.trim(),
                port = port,
                httpPort = httpPort,
                pairingCode = code.trim(),
                expectedFingerprint = fingerprint?.trim()?.takeIf { it.isNotBlank() }
            )

            isPairing = false
            if (result.success) {
                Toast.makeText(context, "Successfully paired with ${result.desktopInfo?.desktopName ?: "Desktop"}!", Toast.LENGTH_SHORT).show()
                // Connect WebSocket immediately
                connectionManager.connect()
                navController.popBackStack()
            } else {
                pairingErrorMessage = result.errorMessage ?: "Pairing failed. Check IP, port, and Wi-Fi connection."
            }
        }
    }

    fun parseQrContent(content: String) {
        if (isPairing) return
        try {
            // Check if JSON format
            if (content.trim().startsWith("{")) {
                val json = JSONObject(content)
                val host = json.optString("host", "")
                val port = json.optInt("port", 8443)
                val httpPort = json.optInt("httpPort", 8444)
                val code = json.optString("code", "")
                val fingerprint = json.optString("fingerprint", "")
                executePairing(host, port, httpPort, code, fingerprint)
                return
            }

            // Check if URI format: kalynt://pair?host=...&port=...&code=...&fingerprint=...
            val uri = Uri.parse(content)
            val host = uri.getQueryParameter("host") ?: ""
            val port = uri.getQueryParameter("port")?.toIntOrNull() ?: 8443
            val httpPort = uri.getQueryParameter("httpPort")?.toIntOrNull() ?: 8444
            val code = uri.getQueryParameter("code") ?: uri.getQueryParameter("pin") ?: ""
            val fingerprint = uri.getQueryParameter("fingerprint")

            if (host.isNotBlank() && code.isNotBlank()) {
                executePairing(host, port, httpPort, code, fingerprint)
            } else {
                pairingErrorMessage = "QR code does not contain valid Kalynt pairing parameters."
            }
        } catch (e: Exception) {
            SecurityHardening.safeLog("PairingScreen", "Failed to parse QR code: ${e.message}", isError = true)
            pairingErrorMessage = "Invalid QR code format."
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pair Kalynt Desktop", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.testTag("pairing_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Tabs: Scan QR vs Manual Entry
            TabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Scan QR Code") },
                    icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) },
                    modifier = Modifier.testTag("tab_qr_scan")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Manual IP") },
                    icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    modifier = Modifier.testTag("tab_manual_ip")
                )
            }

            if (pairingErrorMessage != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Text(
                            text = pairingErrorMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            if (isPairing) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator()
                        Text("Negotiating TLS pairing handshake...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else if (selectedTab == 0) {
                // QR Code Scanner Tab
                if (!hasCameraPermission) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Camera Permission Required",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Scan the QR code displayed in Kalynt Desktop (Settings -> Mobile Companion).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                            modifier = Modifier.testTag("grant_camera_permission_button")
                        ) {
                            Text("Grant Camera Permission")
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black)
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                val previewView = PreviewView(ctx)
                                val cameraExecutor = Executors.newSingleThreadExecutor()
                                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                                cameraProviderFuture.addListener({
                                    val cameraProvider = cameraProviderFuture.get()
                                    val preview = Preview.Builder().build().also {
                                        it.setSurfaceProvider(previewView.surfaceProvider)
                                    }

                                    val imageAnalysis = ImageAnalysis.Builder()
                                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                        .build()

                                    val reader = MultiFormatReader()

                                    imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                        scanBarcode(imageProxy, reader) { scannedText ->
                                            coroutineScope.launch {
                                                parseQrContent(scannedText)
                                            }
                                        }
                                    }

                                    try {
                                        cameraProvider.unbindAll()
                                        cameraProvider.bindToLifecycle(
                                            context as androidx.lifecycle.LifecycleOwner,
                                            CameraSelector.DEFAULT_BACK_CAMERA,
                                            preview,
                                            imageAnalysis
                                        )
                                    } catch (e: Exception) {
                                        SecurityHardening.safeLog("PairingScreen", "Camera binding failed: ${e.message}", isError = true)
                                    }
                                }, ContextCompat.getMainExecutor(ctx))

                                previewView
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Target Overlay Box
                        Surface(
                            modifier = Modifier
                                .size(220.dp)
                                .align(Alignment.Center),
                            color = Color.Transparent,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                        ) {}
                    }
                }
            } else {
                // Manual IP Entry Tab
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Enter Desktop Companion Details",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )

                    OutlinedTextField(
                        value = manualHost,
                        onValueChange = { manualHost = it },
                        label = { Text("Desktop IP Address (e.g. 192.168.1.50)") },
                        modifier = Modifier.fillMaxWidth().testTag("input_desktop_host"),
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = manualPort,
                            onValueChange = { manualPort = it },
                            label = { Text("TLS Port (8443)") },
                            modifier = Modifier.weight(1f).testTag("input_desktop_port"),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = manualHttpPort,
                            onValueChange = { manualHttpPort = it },
                            label = { Text("HTTP Port (8444)") },
                            modifier = Modifier.weight(1f).testTag("input_desktop_http_port"),
                            singleLine = true
                        )
                    }

                    OutlinedTextField(
                        value = manualCode,
                        onValueChange = { manualCode = it },
                        label = { Text("Pairing Secret / Code") },
                        modifier = Modifier.fillMaxWidth().testTag("input_pairing_code"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = manualFingerprint,
                        onValueChange = { manualFingerprint = it },
                        label = { Text("TLS Cert SHA-256 Fingerprint (Optional)") },
                        modifier = Modifier.fillMaxWidth().testTag("input_cert_fingerprint"),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            val port = manualPort.toIntOrNull() ?: 8443
                            val httpPort = manualHttpPort.toIntOrNull() ?: 8444
                            executePairing(manualHost, port, httpPort, manualCode, manualFingerprint)
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("submit_manual_pair_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Link, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Connect & Pair Desktop")
                    }
                }
            }
        }
    }
}

private fun scanBarcode(imageProxy: ImageProxy, reader: MultiFormatReader, onScanned: (String) -> Unit) {
    try {
        val buffer = imageProxy.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        val source = PlanarYUVLuminanceSource(
            bytes,
            imageProxy.width,
            imageProxy.height,
            0,
            0,
            imageProxy.width,
            imageProxy.height,
            false
        )
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        val result = reader.decodeWithState(bitmap)
        if (result != null && result.text.isNotBlank()) {
            onScanned(result.text)
        }
    } catch (_: Exception) {
        // No barcode detected in frame, ignore
    } finally {
        imageProxy.close()
    }
}

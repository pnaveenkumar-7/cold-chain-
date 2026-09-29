package com.coldchain.app.ui.screens.inspect

import android.Manifest
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.coldchain.app.BuildConfig
import com.coldchain.app.data.models.BatchVerificationReport
import com.coldchain.app.data.models.OnChainRecord
import com.coldchain.app.data.models.SensorRecord
import com.coldchain.app.data.models.VerificationReport
import com.coldchain.app.data.models.Violation
import com.coldchain.app.ui.components.*
import com.coldchain.app.ui.theme.*
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PublicInspectScreen(
    viewModel: PublicInspectViewModel = hiltViewModel()
) {
    val shipmentId by viewModel.shipmentId.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val selectedBatchIndex by viewModel.selectedBatchIndex.collectAsState()
    var showScanner by remember { mutableStateOf(false) }
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ColdWhite),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Search & QR Card ──────────────────────────────────────────────────
        item {
            AppCard {
                Text(
                    "🔍 Inspect Cold-Chain Crate",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Scan the physical crate QR code or enter a Shipment ID to verify decentralized Ethereum Sepolia proofs, hardware identity, and sensor integrity.",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                    lineHeight = 18.sp
                )
                Spacer(Modifier.height(16.dp))

                // Input row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = shipmentId,
                        onValueChange = viewModel::onShipmentIdChanged,
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                "e.g. SHIP-MTOIMJJ0-91EA60",
                                style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted)
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentBlue,
                            unfocusedBorderColor = BorderLight
                        )
                    )
                    IconButton(
                        onClick = {
                            if (cameraPermission.status.isGranted) {
                                showScanner = !showScanner
                            } else {
                                cameraPermission.launchPermissionRequest()
                            }
                        },
                        modifier = Modifier
                            .background(SurfaceLight, RoundedCornerShape(8.dp))
                            .border(1.dp, BorderLight, RoundedCornerShape(8.dp))
                    ) {
                        Icon(
                            Icons.Default.QrCodeScanner,
                            contentDescription = "Scan QR",
                            tint = TextPrimary
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                PrimaryButton(
                    text = if (uiState is InspectUiState.Loading) "Querying Sepolia Ledger…" else "Verify Shipment Integrity",
                    onClick = { viewModel.inspect() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = shipmentId.trim().isNotEmpty() && uiState !is InspectUiState.Loading
                )
            }
        }

        // ── QR Scanner Overlay ────────────────────────────────────────────────
        if (showScanner) {
            item {
                AnimatedVisibility(visible = true) {
                    AppCard {
                        Text("📷 Scan Crate QR Code", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        QrScannerView(
                            onQrScanned = { scannedId ->
                                showScanner = false
                                viewModel.inspect(scannedId)
                            }
                        )
                    }
                }
            }
        }

        // ── Loading ───────────────────────────────────────────────────────────
        if (uiState is InspectUiState.Loading) {
            item { LoadingCard("Querying Ethereum Sepolia ledger & recomputing Merkle tree…") }
        }

        // ── Not Found ─────────────────────────────────────────────────────────
        if (uiState is InspectUiState.NotFound) {
            item {
                AppCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("📦", fontSize = 40.sp, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Shipment Not Found On-Chain",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Shipment ID \"${(uiState as InspectUiState.NotFound).shipmentId}\" has not been committed to Ethereum Sepolia yet.",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // ── Error ─────────────────────────────────────────────────────────────
        if (uiState is InspectUiState.Error) {
            item { ErrorCard(message = (uiState as InspectUiState.Error).message) }
        }

        // ── Inspection Results ────────────────────────────────────────────────
        if (uiState is InspectUiState.Success) {
            val state = uiState as InspectUiState.Success
            val report = state.report
            val activeBatchReport = if (selectedBatchIndex >= 0 && selectedBatchIndex < report.batchReports.size) {
                report.batchReports[selectedBatchIndex]
            } else null

            // 1. Trust Badge
            item {
                TrustBadge(
                    isFresh = report.isCertifiedFresh,
                    label = report.badgeLabel,
                    detail = if (report.isCertifiedFresh) {
                        "All ${report.recordCount} telemetry readings across ${report.batchCount} batch(es) are verified by Ethereum Sepolia smart contract ${BuildConfig.CONTRACT_ADDRESS.take(10)}… with authentic hardware binding."
                    } else {
                        "Cold-chain integrity check failed! Fraud, hardware tamper, or temperature spoilage detected."
                    }
                )
            }

            // 2. Multi-Batch Selector Tabs
            if (report.batchCount > 1) {
                item {
                    AppCard {
                        Text(
                            "📦 Multi-Batch Shipment Navigation",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        ScrollableTabRow(
                            selectedTabIndex = if (selectedBatchIndex == -1) 0 else selectedBatchIndex + 1,
                            edgePadding = 0.dp,
                            containerColor = SurfaceLight,
                            contentColor = AccentBlueDark
                        ) {
                            Tab(
                                selected = selectedBatchIndex == -1,
                                onClick = { viewModel.selectBatch(-1) },
                                text = { Text("All Batches (${report.recordCount} recs)") }
                            )
                            report.batchReports.forEachIndexed { idx, b ->
                                Tab(
                                    selected = selectedBatchIndex == idx,
                                    onClick = { viewModel.selectBatch(idx) },
                                    text = { Text("Batch #${idx + 1} (${b.onChainKey})") }
                                )
                            }
                        }
                    }
                }
            }

            // 3. Hardware Silicon ID & Anti-Rollback Security Card
            item {
                HardwareSecurityCard(report = report, activeBatch = activeBatchReport)
            }

            // 4. Multi-Vector Tamper Breakdown Card
            item {
                MultiVectorTamperCard(report = report, activeBatch = activeBatchReport)
            }

            // 5. On-Chain Cryptographic Proof Card
            item {
                SepoliaLedgerProofCard(
                    onChain = activeBatchReport?.onChainRecord ?: state.onChain,
                    report = report,
                    activeBatch = activeBatchReport
                )
            }

            // 6. Physical Cold-Chain Checklist
            item {
                PhysicalChecklistCard(
                    onChain = activeBatchReport?.onChainRecord ?: state.onChain,
                    report = report
                )
            }

            // 7. Violations Breakdown (if any)
            if (report.violations.isNotEmpty()) {
                item {
                    ViolationsCard(report.violations)
                }
            }

            // 8. Telemetry Readings Table
            val displayRecords = activeBatchReport?.records ?: state.records
            item {
                Phase10TelemetryCard(
                    records = displayRecords,
                    batchTitle = if (activeBatchReport != null) "Batch #${activeBatchReport.batchIndex + 1} (${activeBatchReport.onChainKey})" else "All Batches (${displayRecords.size} records)"
                )
            }
        }
    }
}

// ── Hardware Silicon ID & Anti-Rollback Card ─────────────────────────────────

@Composable
fun HardwareSecurityCard(report: VerificationReport, activeBatch: BatchVerificationReport?) {
    val boundId = report.boundSiliconId ?: "N/A"
    val isSubstituted = report.deviceSubstitutionDetected
    val isRollback = report.rollbackDetected

    AppCard {
        SectionHeader("🔐 Hardware Binding & Anti-Rollback Security")

        InfoRow("Bound Silicon MAC") {
            CodeText(boundId, color = if (isSubstituted) DangerRedText else TextPrimary)
        }
        InfoRow("Hardware Identity") {
            StatusTag(
                text = if (isSubstituted) "⚠ DEVICE SUBSTITUTION DETECTED" else "✔ AUTHENTIC ESP32 HARDWARE",
                bg = if (isSubstituted) TagRedBg else TagGreenBg,
                textColor = if (isSubstituted) TagRedText else TagGreenText
            )
        }
        InfoRow("Anti-Rollback Monotonic Counter") {
            StatusTag(
                text = if (isRollback) "🚨 ROLLBACK / REPLAY ATTEMPT" else "✔ SEQUENCE MONOTONIC (INTACT)",
                bg = if (isRollback) TagRedBg else TagGreenBg,
                textColor = if (isRollback) TagRedText else TagGreenText
            )
        }
    }
}

// ── Multi-Vector Tamper Card ─────────────────────────────────────────────────

@Composable
fun MultiVectorTamperCard(report: VerificationReport, activeBatch: BatchVerificationReport?) {
    val vectors = if (activeBatch != null) activeBatch.tamperVectors else report.tamperVectorsSummary
    val isDoorBreached = vectors.contains("DOOR_BREACH") || vectors.contains("doorOpened")
    val isSdRemoved = vectors.contains("SD_REMOVAL") || vectors.contains("sdRemoved")
    val isShellCut = vectors.contains("SHELL_CUT") || vectors.contains("shellCut")
    val isLightBreached = vectors.contains("LIGHT_BREACH") || vectors.contains("lightBreach")
    val isManualTripped = vectors.contains("MANUAL_SWITCH") || vectors.contains("manualSwitch")

    AppCard {
        SectionHeader("🛡️ Multi-Vector Physical Tamper Status")

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TamperVectorRow("Magnetic Door Reed Switch", isDoorBreached, "Lid / Door Opened")
            TamperVectorRow("SD Card Removal Interlock", isSdRemoved, "Storage Card Removed")
            TamperVectorRow("Conductive Enclosure Shell", isShellCut, "Chassis Cut / Drilled")
            TamperVectorRow("Optical Light Photodiode", isLightBreached, "Internal Light Breach")
            TamperVectorRow("Manual Pushbutton Switch", isManualTripped, "Physical Tamper Button")
        }
    }
}

@Composable
fun TamperVectorRow(name: String, tripped: Boolean, trippedDesc: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (tripped) DangerRedBg else SurfaceLight, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            name,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Medium,
                color = if (tripped) DangerRedText else TextPrimary
            )
        )
        StatusTag(
            text = if (tripped) "TRIPPED: $trippedDesc" else "NORMAL / INTACT",
            bg = if (tripped) TagRedBg else TagGreenBg,
            textColor = if (tripped) TagRedText else TagGreenText
        )
    }
}

// ── Sepolia Ledger Proof Card ────────────────────────────────────────────────

@Composable
fun SepoliaLedgerProofCard(
    onChain: OnChainRecord,
    report: VerificationReport,
    activeBatch: BatchVerificationReport?
) {
    val clientRoot = activeBatch?.clientMerkleRoot ?: report.clientMerkleRoot
    val onChainRoot = (activeBatch?.onChainMerkleRoot?.ifEmpty { null } ?: onChain.merkleRoot).lowercase()
    val merkleMatch = if (activeBatch != null) activeBatch.merkleMatch else report.merkleMatch

    AppCard {
        SectionHeader("⛓️ Ethereum Sepolia Smart Contract Proof")

        InfoRow("Sepolia Contract") {
            CodeText(BuildConfig.CONTRACT_ADDRESS.take(18) + "…")
        }
        InfoRow("On-Chain Key") {
            CodeText(activeBatch?.onChainKey ?: onChain.shipmentId)
        }
        InfoRow("On-Chain Merkle Root") {
            CodeText(
                if (onChainRoot.isNotEmpty()) onChainRoot.take(24) + "…" else "Not committed",
                color = TextSecondary
            )
        }
        InfoRow("Client Recomputed Root") {
            CodeText(
                if (clientRoot.isNotEmpty()) clientRoot.take(24) + "…" else "–",
                color = if (merkleMatch) FreshGreenText else DangerRedText
            )
        }
        InfoRow("Merkle Equivalence") {
            Text(
                text = if (merkleMatch) "✔ Exact Match (32 bytes SHA-256)" else "✘ CRYPTOGRAPHIC MISMATCH",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = if (merkleMatch) FreshGreenText else DangerRedText,
                    fontWeight = FontWeight.Bold
                )
            )
        }
        InfoRow("Tamper Violation Flag") {
            StatusTag(
                text = if (onChain.tamperViolation) "TRUE (TAMPERED)" else "FALSE (CLEAN)",
                bg = if (onChain.tamperViolation) TagRedBg else TagGreenBg,
                textColor = if (onChain.tamperViolation) TagRedText else TagGreenText
            )
        }
        if (onChain.timestamp > 0) {
            InfoRow("Block Timestamp") {
                val date = Date(onChain.timestamp * 1000)
                Text(
                    text = SimpleDateFormat("MMM dd yyyy, HH:mm:ss z", Locale.getDefault()).format(date),
                    style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary)
                )
            }
        }
    }
}

// ── Physical Checklist Card ───────────────────────────────────────────────────

@Composable
fun PhysicalChecklistCard(onChain: OnChainRecord, report: VerificationReport) {
    val hasTempBreach = report.violations.any { it.type == "TEMPERATURE_BREACH" }
    val hasHardwareTamper = report.violations.any { it.type == "MULTI_VECTOR_TAMPER" || it.type == "HARDWARE_TAMPER_BREACH" }
    val hasSubstitution = report.deviceSubstitutionDetected
    val hasRollback = report.rollbackDetected

    AppCard {
        SectionHeader("🌡️ Comprehensive Trust Verification Checklist")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChecklistItem(
                pass = report.merkleMatch,
                label = "Cryptographic Merkle Root Proof",
                detail = if (report.merkleMatch) "Client binary tree matches Sepolia on-chain commitment" else "FAILED — Merkle root mismatch"
            )
            ChecklistItem(
                pass = !hasHardwareTamper && !onChain.tamperViolation,
                label = "Physical Multi-Vector Tamper Sensors",
                detail = if (!hasHardwareTamper && !onChain.tamperViolation) "All 5 chassis breach sensors intact" else "FAILED — Physical enclosure breach flagged"
            )
            ChecklistItem(
                pass = !hasSubstitution,
                label = "Silicon ID Hardware Device Binding",
                detail = if (!hasSubstitution) "Authenticated eFuse MAC address matches bound device" else "FAILED — Unauthorized physical device substitution"
            )
            ChecklistItem(
                pass = !hasRollback,
                label = "Anti-Rollback Sequence Monotonicity",
                detail = if (!hasRollback) "Non-volatile sequence counters strictly ascending" else "FAILED — Non-monotonic sequence counter anomaly"
            )
            ChecklistItem(
                pass = !hasTempBreach,
                label = "Cold-Chain Temperature Compliance (${BuildConfig.TEMP_MIN}°C – ${BuildConfig.TEMP_MAX}°C)",
                detail = if (!hasTempBreach) "All readings within safe biological window" else "FAILED — Temperature excursion detected"
            )
        }
    }
}

// ── Violations Card ───────────────────────────────────────────────────────────

@Composable
fun ViolationsCard(violations: List<Violation>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DangerRedBg),
        border = BorderStroke(1.dp, Color(0xFFF87171))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "🚨 Critical Security & Integrity Violations",
                style = MaterialTheme.typography.titleMedium.copy(
                    color = DangerRedText,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(Modifier.height(8.dp))
            violations.forEach { v ->
                Column(modifier = Modifier.padding(bottom = 10.dp)) {
                    Text(
                        v.title,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = DangerRedText
                        )
                    )
                    Text(
                        v.detail,
                        style = MaterialTheme.typography.bodySmall.copy(color = DangerRedText),
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

// ── Telemetry Table ───────────────────────────────────────────────────────────

@Composable
fun Phase10TelemetryCard(records: List<SensorRecord>, batchTitle: String) {
    AppCard {
        SectionHeader("📊 Sensor Telemetry Readings — $batchTitle")

        // Header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceLight, RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 6.dp)
        ) {
            TelemetryHeader("#", 0.08f)
            TelemetryHeader("Time", 0.18f)
            TelemetryHeader("Temp", 0.16f)
            TelemetryHeader("Hum%", 0.14f)
            TelemetryHeader("Gas", 0.12f)
            TelemetryHeader("Tamper", 0.18f)
            TelemetryHeader("Hash", 0.14f)
        }

        records.forEachIndexed { idx, record ->
            val tempOut = record.temp < BuildConfig.TEMP_MIN || record.temp > BuildConfig.TEMP_MAX
            val chainBad = record.chainValid == false
            val tampered = record.isTampered
            val rowBg = if (tempOut || chainBad || tampered) Color(0xFFFFF1F2) else Color.Transparent

            val timeStr = try {
                val date = java.time.Instant.parse(record.timestamp)
                SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(date.toEpochMilli()))
            } catch (e: Exception) {
                record.timestamp.take(8)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(rowBg)
                    .padding(horizontal = 6.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TelemetryCell("#${record.effectiveIndex}", 0.08f)
                TelemetryCell(timeStr, 0.18f)
                TelemetryCell(
                    "${record.temp}°C ${if (tempOut) "⚠" else ""}",
                    0.16f,
                    color = if (tempOut) DangerRed else FreshGreenText
                )
                TelemetryCell("${record.humidity}%", 0.14f)
                TelemetryCell("${record.gas?.toInt() ?: "–"}", 0.12f)
                Box(modifier = Modifier.weight(0.18f)) {
                    val vectors = record.getTrippedVectors()
                    if (vectors.isEmpty()) {
                        StatusTag("NORMAL", TagGreenBg, TagGreenText)
                    } else {
                        StatusTag(vectors.first(), TagRedBg, TagRedText)
                    }
                }
                Text(
                    text = record.hash?.take(6) ?: "–",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        color = TextMuted
                    ),
                    modifier = Modifier.weight(0.14f)
                )
            }
            if (idx < records.size - 1) {
                HorizontalDivider(color = SurfaceLight, thickness = 0.5.dp)
            }
        }
    }
}

@Composable
private fun RowScope.TelemetryHeader(text: String, weight: Float) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary),
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.weight(weight)
    )
}

@Composable
private fun RowScope.TelemetryCell(text: String, weight: Float, color: Color = TextPrimary) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(color = color),
        modifier = Modifier.weight(weight),
        maxLines = 1
    )
}

// ── QR Scanner View ──────────────────────────────────────────────────────────

@Composable
fun QrScannerView(onQrScanned: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var scanned by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .background(Color.Black, RoundedCornerShape(8.dp))
    ) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }
            },
            modifier = Modifier.fillMaxSize()
        ) { previewView ->
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val barcodeScanner = BarcodeScanning.getClient()
                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                            processImageProxy(imageProxy, barcodeScanner, scanned) { value ->
                                scanned = true
                                onQrScanned(value)
                            }
                        }
                    }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalyzer
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(context))
        }

        // Viewfinder overlay
        Box(
            modifier = Modifier
                .size(220.dp)
                .align(Alignment.Center)
                .border(2.dp, IcyBlue, RoundedCornerShape(12.dp))
        )
        Text(
            text = "Point camera at crate QR code",
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }
}

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
private fun processImageProxy(
    imageProxy: ImageProxy,
    barcodeScanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    scanned: Boolean,
    onQrFound: (String) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage != null && !scanned) {
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        barcodeScanner.process(image)
            .addOnSuccessListener { barcodes ->
                for (barcode in barcodes) {
                    val rawValue = barcode.rawValue
                    if (!rawValue.isNullOrBlank()) {
                        onQrFound(rawValue)
                        break
                    }
                }
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    } else {
        imageProxy.close()
    }
}

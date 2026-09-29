package com.coldchain.app.ui.screens.sender

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.coldchain.app.data.models.Shipment
import com.coldchain.app.ui.components.*
import com.coldchain.app.ui.theme.*

@Composable
fun SenderScreen(
    viewModel: SenderViewModel = hiltViewModel(),
    onInspectShipment: (String) -> Unit = {}
) {
    val authState by viewModel.authState.collectAsState()

    when (val state = authState) {
        is AuthUiState.Idle, is AuthUiState.Loading, is AuthUiState.Error -> {
            SenderAuthScreen(viewModel = viewModel, state = state)
        }
        is AuthUiState.Authenticated -> {
            SenderDashboard(viewModel = viewModel, user = state, onInspectShipment = onInspectShipment)
        }
    }
}

// ── Auth Screen ───────────────────────────────────────────────────────────────

@Composable
fun SenderAuthScreen(viewModel: SenderViewModel, state: AuthUiState) {
    var authMode by remember { mutableStateOf("login") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ColdWhite)
            .imePadding()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(modifier = Modifier.padding(28.dp)) {
                // Header
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("📦", fontSize = 40.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Sender Portal",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (authMode == "login") "Sign in to manage and dispatch cold-chain shipments"
                        else "Register a new sender account",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(Modifier.height(20.dp))

                // Tab Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceLight, RoundedCornerShape(8.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("login" to "Sign In", "signup" to "Create Account").forEach { (mode, label) ->
                        val active = authMode == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    if (active) CardBg else Color.Transparent,
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable { authMode = mode }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                    color = if (active) TextPrimary else TextSecondary
                                )
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Error
                if (state is AuthUiState.Error) {
                    ErrorCard(state.message)
                    Spacer(Modifier.height(12.dp))
                }

                // Form
                ColdChainTextField(label = "Sender Email Address", value = email, onValueChange = { email = it }, placeholder = "sender@coldchain.com", keyboardType = KeyboardType.Email)
                Spacer(Modifier.height(12.dp))
                ColdChainTextField(label = "Password (min 8 chars)", value = password, onValueChange = { password = it }, placeholder = "••••••••", isPassword = true)
                Spacer(Modifier.height(20.dp))

                PrimaryButton(
                    text = when {
                        state is AuthUiState.Loading -> "Processing…"
                        authMode == "login" -> "🔑 Sign In as Sender"
                        else -> "📝 Register Sender Account"
                    },
                    onClick = {
                        if (authMode == "login") viewModel.login(email, password)
                        else viewModel.signup(email, password)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state !is AuthUiState.Loading && email.isNotEmpty() && password.length >= 8
                )

                if (authMode == "signup") {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "🔒 Security Policy: All public signups are assigned the sender role.",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

// ── Authenticated Sender Dashboard ────────────────────────────────────────────

@Composable
fun SenderDashboard(
    viewModel: SenderViewModel,
    user: AuthUiState.Authenticated,
    onInspectShipment: (String) -> Unit
) {
    val myShipments by viewModel.myShipments.collectAsState()
    val shipmentsLoading by viewModel.shipmentsLoading.collectAsState()
    val shipmentsError by viewModel.shipmentsError.collectAsState()
    val createdShipment by viewModel.createdShipment.collectAsState()
    val createLoading by viewModel.createLoading.collectAsState()
    val createError by viewModel.createError.collectAsState()

    var origin by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf("") }
    var receiverName by remember { mutableStateOf("") }
    var receiverContact by remember { mutableStateOf("") }
    var vehicleId by remember { mutableStateOf("") }
    var cargoDescription by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ColdWhite)
            .imePadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Profile bar
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                border = BorderStroke(1.dp, BorderLight),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("📦 Sender Dashboard", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Logged in as: ${user.email}", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
                            StatusTag("SENDER", TagBlueBg, TagBlueText)
                        }
                    }
                    IconButton(onClick = { viewModel.logout() }) {
                        Icon(Icons.Default.Logout, contentDescription = "Logout", tint = TextSecondary)
                    }
                }
            }
        }

        // Create Shipment form
        item {
            AppCard {
                Text("➕ Register New Cold-Chain Shipment", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("Backend automatically assigns an immutable Shipment ID and generates a crate QR code.",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary), lineHeight = 16.sp)
                Spacer(Modifier.height(14.dp))

                createError?.let { ErrorCard(it); Spacer(Modifier.height(10.dp)) }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        ColdChainTextField("Origin *", origin, { origin = it }, "e.g. Hyderabad Pharma Hub")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        ColdChainTextField("Destination *", destination, { destination = it }, "e.g. Mumbai Vaccine Center")
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        ColdChainTextField("Receiver Name *", receiverName, { receiverName = it }, "e.g. Dr. Ramesh Patel")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        ColdChainTextField("Receiver Contact", receiverContact, { receiverContact = it }, "+91 98765 43210")
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        ColdChainTextField("Vehicle / Container ID", vehicleId, { vehicleId = it }, "REEFER-TRUCK-88A")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        ColdChainTextField("Cargo Description *", cargoDescription, { cargoDescription = it }, "5,000 Vials mRNA Vaccines")
                    }
                }
                Spacer(Modifier.height(16.dp))

                PrimaryButton(
                    text = if (createLoading) "Generating Shipment…" else "🚀 Create Shipment & Generate QR Tag",
                    onClick = {
                        viewModel.createShipment(origin, destination, receiverName, receiverContact, vehicleId, cargoDescription)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !createLoading && origin.isNotEmpty() && destination.isNotEmpty() && receiverName.isNotEmpty() && cargoDescription.isNotEmpty(),
                    color = AccentBlueDark
                )

                // Created shipment success + QR
                AnimatedVisibility(visible = createdShipment != null) {
                    createdShipment?.let { shipment ->
                        Spacer(Modifier.height(16.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = FreshGreenBg),
                            border = BorderStroke(1.dp, Color(0xFFBBF7D0))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "✔ Shipment Created & Assigned!",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = FreshGreenText
                                    )
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Assigned Shipment ID:", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                                    CodeText(shipment.shipmentId, color = AccentBlueDark)
                                }
                                Text("Route: ${shipment.origin} ➔ ${shipment.destination}", style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.height(16.dp))
                                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        QrCodeImage(content = shipment.shipmentId, size = 180.dp)
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            "${shipment.origin} ➔ ${shipment.destination}",
                                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // My Shipments list header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "📋 My Dispatched Shipments (${myShipments.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { viewModel.loadMyShipments() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = TextSecondary)
                }
            }
        }

        if (shipmentsLoading) {
            item { LoadingCard("Loading shipments…") }
        }
        shipmentsError?.let { err ->
            item { ErrorCard(err) }
        }
        if (!shipmentsLoading && myShipments.isEmpty()) {
            item {
                AppCard {
                    Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📦", fontSize = 32.sp, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(8.dp))
                        Text("No shipments created yet. Register your first shipment above.", style = MaterialTheme.typography.bodySmall.copy(color = TextMuted), textAlign = TextAlign.Center)
                    }
                }
            }
        }

        items(myShipments) { shipment ->
            SenderShipmentCard(shipment = shipment, onStatusChange = { viewModel.updateStatus(shipment.shipmentId, it) }, onInspect = { onInspectShipment(shipment.shipmentId) })
        }
    }
}

// ── Shipment Card ─────────────────────────────────────────────────────────────

@Composable
fun SenderShipmentCard(shipment: Shipment, onStatusChange: (String) -> Unit, onInspect: () -> Unit) {
    AppCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    shipment.shipmentId,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = AccentBlueDark
                    ),
                    fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${shipment.origin} ➔ ${shipment.destination}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    "📦 ${shipment.cargoDescription ?: ""} | 👤 ${shipment.receiverName ?: ""}",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                )
            }
            ShipmentStatusBadge(shipment.status)
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = SurfaceLight)
        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status dropdown
            var showMenu by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Status:", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
                Box {
                    OutlinedButton(
                        onClick = { showMenu = true },
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text(shipment.status.replace("_", " ").uppercase(), style = MaterialTheme.typography.labelSmall)
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        listOf("created", "in_transit", "delivered").forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s.replace("_", " ").uppercase(), style = MaterialTheme.typography.bodySmall) },
                                onClick = { onStatusChange(s); showMenu = false }
                            )
                        }
                    }
                }
            }

            Button(
                onClick = onInspect,
                colors = ButtonDefaults.buttonColors(containerColor = IcyBlue.copy(alpha = 0.9f)),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Text("🔍 Inspect as Receiver", style = MaterialTheme.typography.labelSmall, color = DeepNavy)
            }
        }
    }
}

// ── Reusable TextField ────────────────────────────────────────────────────────

@Composable
fun ColdChainTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontWeight = FontWeight.SemiBold))
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)) },
            singleLine = true,
            visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentBlue,
                unfocusedBorderColor = BorderLight
            ),
            textStyle = MaterialTheme.typography.bodyMedium
        )
    }
}

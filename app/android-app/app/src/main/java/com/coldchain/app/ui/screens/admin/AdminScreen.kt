package com.coldchain.app.ui.screens.admin

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.coldchain.app.data.models.Shipment
import com.coldchain.app.data.models.SystemStats
import com.coldchain.app.ui.components.*
import com.coldchain.app.ui.screens.sender.ColdChainTextField
import com.coldchain.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AdminScreen(
    viewModel: AdminViewModel = hiltViewModel(),
    onInspectShipment: (String) -> Unit = {}
) {
    val authState by viewModel.authState.collectAsState()

    when (val state = authState) {
        is AdminAuthState.Idle, is AdminAuthState.Loading, is AdminAuthState.Error ->
            AdminAuthScreen(viewModel = viewModel, state = state)
        is AdminAuthState.Authenticated ->
            AdminDashboardContent(viewModel = viewModel, user = state, onInspectShipment = onInspectShipment)
    }
}

// ── Admin Login Screen ────────────────────────────────────────────────────────

@Composable
fun AdminAuthScreen(viewModel: AdminViewModel, state: AdminAuthState) {
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
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🛡️", fontSize = 40.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Admin Portal",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Restricted management portal. Requires verified administrative credentials.",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(Modifier.height(20.dp))

                if (state is AdminAuthState.Error) {
                    ErrorCard(state.message)
                    Spacer(Modifier.height(12.dp))
                }

                ColdChainTextField("Admin Email Address", email, { email = it }, "admin@coldchain.com")
                Spacer(Modifier.height(12.dp))
                ColdChainTextField("Admin Password", password, { password = it }, "••••••••", isPassword = true)
                Spacer(Modifier.height(20.dp))

                PrimaryButton(
                    text = if (state is AdminAuthState.Loading) "Authenticating…" else "🛡️ Sign In to Admin Console",
                    onClick = { viewModel.login(email, password) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state !is AdminAuthState.Loading && email.isNotEmpty() && password.isNotEmpty(),
                    color = DeepNavy
                )

                Spacer(Modifier.height(16.dp))
                Text(
                    "ℹ️ Root admin accounts are provisioned out-of-band via scripts/seed-admin.js.",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ── Admin Dashboard ───────────────────────────────────────────────────────────

@Composable
fun AdminDashboardContent(
    viewModel: AdminViewModel,
    user: AdminAuthState.Authenticated,
    onInspectShipment: (String) -> Unit
) {
    val stats by viewModel.stats.collectAsState()
    val shipments by viewModel.shipments.collectAsState()
    val dataLoading by viewModel.dataLoading.collectAsState()
    val dataError by viewModel.dataError.collectAsState()

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
                        Text("🛡️ Master Administration Console", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Admin: ${user.email}", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
                            StatusTag("ROOT ADMIN", TagRedBg, TagRedText)
                        }
                    }
                    Row {
                        IconButton(onClick = { viewModel.loadAdminData() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = TextSecondary)
                        }
                        IconButton(onClick = { viewModel.logout() }) {
                            Icon(Icons.Default.Logout, contentDescription = "Logout", tint = TextSecondary)
                        }
                    }
                }
            }
        }

        // Immutability Notice
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F9FF)),
                border = BorderStroke(1.dp, Color(0xFFBAE6FD))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("🔒", fontSize = 24.sp)
                    Column {
                        Text(
                            "Cryptographic Immutability Enforced by Protocol",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color(0xFF0369A1),
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Sensor telemetry, hash chain records, Merkle roots, and Ethereum Sepolia commitments are strictly append-only and immutable. " +
                                    "No user or administrative role can modify, overwrite, or delete sensor records.",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF0C4A6E)),
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }

        dataError?.let { item { ErrorCard(it) } }
        if (dataLoading) { item { LoadingCard("Loading administration data…") } }

        // System Stats Cards
        stats?.let { s ->
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("Total Users", s.totalUsers.toString(), "${s.sendersCount} Senders / ${s.adminsCount} Admin", Modifier.weight(1f))
                    StatCard("Total Shipments", s.totalShipments.toString(), "Across all senders", Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("On-Chain Committed", s.committedShipments.toString(), "Secured with Merkle Roots", Modifier.weight(1f), valueColor = FreshGreenText)
                    StatCard("KMS Key Rotations", (s.keyRotationEvents).toString(), "Cryptographic handover events", Modifier.weight(1f), valueColor = AccentBlueDark)
                }
            }
        }

        // Master Shipments Table header
        item {
            Text(
                "🌐 Master Shipments Ledger (${shipments.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (!dataLoading && shipments.isEmpty()) {
            item {
                AppCard {
                    Text(
                        "No shipments registered in the system yet.",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextMuted),
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        items(shipments) { shipment ->
            AdminShipmentCard(shipment = shipment, onStatusChange = { viewModel.updateStatus(shipment.shipmentId, it) }, onInspect = { onInspectShipment(shipment.shipmentId) })
        }
    }
}

// ── Stat Card ─────────────────────────────────────────────────────────────────

@Composable
fun StatCard(label: String, value: String, sub: String, modifier: Modifier = Modifier, valueColor: Color = TextPrimary) {
    AppCard(modifier = modifier) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.headlineMedium.copy(color = valueColor, fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(2.dp))
        Text(sub, style = MaterialTheme.typography.bodySmall.copy(color = TextMuted), lineHeight = 14.sp)
    }
}

// ── Admin Shipment Card ───────────────────────────────────────────────────────

@Composable
fun AdminShipmentCard(shipment: Shipment, onStatusChange: (String) -> Unit, onInspect: () -> Unit) {
    AppCard {
        // Shipment ID + date
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    shipment.shipmentId,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = AccentBlueDark),
                    fontFamily = FontFamily.Monospace
                )
                shipment.createdAt?.let {
                    Text(
                        try {
                            SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                                .format(SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).parse(it)!!)
                        } catch (e: Exception) { it },
                        style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                    )
                }
            }
            ShipmentStatusBadge(shipment.status)
        }

        Spacer(Modifier.height(8.dp))

        // Sender + Route
        Text(shipment.senderEmail ?: "Unknown sender", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontWeight = FontWeight.Medium))
        Text("${shipment.origin} ➔ ${shipment.destination}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
        shipment.cargoDescription?.let { Text(it, style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)) }

        Spacer(Modifier.height(10.dp))

        // On-chain status
        if (shipment.onChainCommitted) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusTag("✔ COMMITTED ON-CHAIN", FreshGreenPill, FreshGreenText)
                Text(
                    "Root: ${shipment.merkleRoot?.take(14) ?: "N/A"}… (${shipment.recordCount} recs)",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextMuted),
                    fontFamily = FontFamily.Monospace
                )
            }
        } else {
            StatusTag("⏳ AWAITING SENSOR TELEMETRY", WarnAmberBg, WarnAmberText)
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = SurfaceLight)
        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            var showMenu by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(
                    onClick = { showMenu = true },
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text("Status: ${shipment.status.replace("_", " ").uppercase()}", style = MaterialTheme.typography.labelSmall)
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

            PrimaryButton(
                text = "🔍 Inspect",
                onClick = onInspect,
                color = AccentBlueDark
            )
        }
    }
}

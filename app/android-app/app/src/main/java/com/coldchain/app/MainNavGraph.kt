package com.coldchain.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.coldchain.app.ui.components.NetworkBadge
import com.coldchain.app.ui.screens.admin.AdminScreen
import com.coldchain.app.ui.screens.inspect.PublicInspectScreen
import com.coldchain.app.ui.screens.sender.SenderScreen
import com.coldchain.app.ui.theme.*

// ── Navigation Routes ─────────────────────────────────────────────────────────

sealed class NavRoute(val route: String, val label: String, val icon: String) {
    object Public : NavRoute("public", "Verify", "🔍")
    object Sender : NavRoute("sender", "Sender", "📦")
    object Admin : NavRoute("admin", "Admin", "🛡️")
}

val bottomNavItems = listOf(NavRoute.Public, NavRoute.Sender, NavRoute.Admin)

// ── Main Navigation Graph ─────────────────────────────────────────────────────

@Composable
fun MainNavGraph() {
    val navController: NavHostController = rememberNavController()
    val navBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStack?.destination?.route

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        containerColor = ColdWhite,
        topBar = { AppHeader() },
        bottomBar = {
            NavigationBar(
                containerColor = DeepNavy,
                contentColor = Color.White,
                windowInsets = NavigationBarDefaults.windowInsets
            ) {
                bottomNavItems.forEach { item ->
                    val selected = currentRoute == item.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        label = {
                            Text(
                                item.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        icon = { Text(item.icon, fontSize = 18.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedTextColor = IcyBlue,
                            unselectedTextColor = Color(0xFF94A3B8),
                            selectedIconColor = IcyBlue,
                            unselectedIconColor = Color(0xFF94A3B8),
                            indicatorColor = Color(0xFF1E293B)
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = NavRoute.Public.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues)
                .imePadding()
        ) {
            composable(NavRoute.Public.route) {
                PublicInspectScreen()
            }
            composable(NavRoute.Sender.route) {
                SenderScreen(
                    onInspectShipment = {
                        navController.navigate(NavRoute.Public.route) {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(NavRoute.Admin.route) {
                AdminScreen(
                    onInspectShipment = {
                        navController.navigate(NavRoute.Public.route) {
                            launchSingleTop = true
                        }
                    }
                )
            }
        }
    }
}

// ── App Header ────────────────────────────────────────────────────────────────

@Composable
fun AppHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.linearGradient(colors = listOf(DeepNavy, SlateBlue))
            )
            .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("❄️ ", fontSize = 22.sp)
                Text(
                    "Cold-Chain Trust Inspector",
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
            Text(
                "Decentralized Cryptographic Provenance & Telemetry Verification",
                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8))
            )
        }
        Spacer(Modifier.width(8.dp))
        NetworkBadge()
    }
}

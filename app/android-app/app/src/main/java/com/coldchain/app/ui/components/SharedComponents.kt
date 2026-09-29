package com.coldchain.app.ui.components

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coldchain.app.ui.theme.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix

// ── Section Card ─────────────────────────────────────────────────────────────

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, BorderLight)
    ) {
        Column(modifier = Modifier.padding(20.dp), content = content)
    }
}

// ── Section Header ────────────────────────────────────────────────────────────

@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium.copy(
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        ),
        modifier = modifier.padding(bottom = 12.dp)
    )
    HorizontalDivider(
        color = SurfaceLight,
        thickness = 1.dp,
        modifier = Modifier.padding(bottom = 12.dp)
    )
}

// ── Status Tag ─────────────────────────────────────────────────────────────

@Composable
fun StatusTag(
    text: String,
    bg: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(bg, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

// ── Monospace Code Label ──────────────────────────────────────────────────────

@Composable
fun CodeText(text: String, modifier: Modifier = Modifier, color: Color = TextSecondary) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = FontFamily.Monospace,
            color = color
        ),
        modifier = modifier
            .background(SurfaceLight, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

// ── Trust Badge ───────────────────────────────────────────────────────────────

@Composable
fun TrustBadge(isFresh: Boolean, label: String, detail: String) {
    val bg = if (isFresh) FreshGreenBg else DangerRedBg
    val border = if (isFresh) FreshGreenBorder else DangerRedBorder
    val pillBg = if (isFresh) FreshGreenPill else DangerRedPill
    val pillText = if (isFresh) FreshGreenText else DangerRedText
    val emoji = if (isFresh) "✅" else "🚨"
    val tag = if (isFresh) "CRYPTOGRAPHICALLY VERIFIED" else "CRITICAL INTEGRITY VIOLATION"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, border, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = bg),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(text = emoji, fontSize = 42.sp)
            Column {
                Box(
                    modifier = Modifier
                        .background(pillBg, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = tag,
                        style = MaterialTheme.typography.labelSmall.copy(color = pillText),
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.headlineMedium.copy(color = pillText)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall.copy(color = pillText),
                    lineHeight = 18.sp
                )
            }
        }
    }
}

// ── Checklist Item ────────────────────────────────────────────────────────────

@Composable
fun ChecklistItem(pass: Boolean, label: String, detail: String) {
    val bg = if (pass) FreshGreenBg else DangerRedBg
    val textColor = if (pass) FreshGreenText else DangerRedText
    val border = if (pass) Color(0xFFBBF7D0) else Color(0xFFFECACA)
    val icon = if (pass) "✔" else "✘"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(8.dp))
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(text = icon, color = textColor, fontWeight = FontWeight.Bold)
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = textColor,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall.copy(color = textColor),
                lineHeight = 16.sp
            )
        }
    }
}

// ── Network Badge ─────────────────────────────────────────────────────────────

@Composable
fun NetworkBadge() {
    Row(
        modifier = Modifier
            .background(Color(0x14FFFFFF), RoundedCornerShape(20.dp))
            .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(NetworkGreen, CircleShape)
        )
        Text(
            text = "Ethereum Sepolia (11155111)",
            color = Color.White,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

// ── Loading Overlay ───────────────────────────────────────────────────────────

@Composable
fun LoadingCard(message: String = "Querying Ethereum Sepolia ledger & recomputing Merkle tree…") {
    AppCard {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator(color = AccentBlue, strokeWidth = 3.dp)
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ── Error Card ────────────────────────────────────────────────────────────────

@Composable
fun ErrorCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = DangerRedBg),
        border = BorderStroke(1.dp, Color(0xFFFECACA))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("⚠️")
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium.copy(color = DangerRedText)
            )
        }
    }
}

// ── Primary Button ────────────────────────────────────────────────────────────

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = AccentBlue
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color)
    ) {
        Text(text = text, fontWeight = FontWeight.SemiBold)
    }
}

// ── Shipment Status Badge ─────────────────────────────────────────────────────

@Composable
fun ShipmentStatusBadge(status: String) {
    val (bg, textColor, label) = when (status) {
        "in_transit" -> Triple(WarnAmberBg, WarnAmberText, "IN TRANSIT")
        "delivered" -> Triple(FreshGreenPill, FreshGreenText, "DELIVERED")
        else -> Triple(TagBlueBg, TagBlueText, "CREATED")
    }
    StatusTag(text = label, bg = bg, textColor = textColor)
}

// ── QR Code Generator ─────────────────────────────────────────────────────────

@Composable
fun QrCodeImage(content: String, size: Dp = 200.dp) {
    val bitmap = remember(content) {
        try {
            val writer = MultiFormatWriter()
            val bitMatrix: BitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 512, 512)
            val bmp = Bitmap.createBitmap(512, 512, Bitmap.Config.RGB_565)
            for (x in 0 until 512) {
                for (y in 0 until 512) {
                    bmp.setPixel(x, y, if (bitMatrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE)
                }
            }
            bmp
        } catch (e: Exception) {
            null
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "QR Code for $content",
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp))
        )
    }
}

// ── Info Table Row ────────────────────────────────────────────────────────────

@Composable
fun InfoRow(label: String, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
            modifier = Modifier.weight(0.4f)
        )
        Box(modifier = Modifier.weight(0.6f)) {
            content()
        }
    }
    HorizontalDivider(color = SurfaceLight, thickness = 0.5.dp)
}

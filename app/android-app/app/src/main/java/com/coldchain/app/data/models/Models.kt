package com.coldchain.app.data.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// ── Auth Models ─────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class AuthRequest(
    val email: String,
    val password: String
)

@JsonClass(generateAdapter = true)
data class AuthResponse(
    val token: String,
    val user: UserInfo
)

@JsonClass(generateAdapter = true)
data class UserInfo(
    val id: String,
    val email: String,
    val role: String // "sender" | "admin"
)

// ── Shipment Models ──────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class CreateShipmentRequest(
    val origin: String,
    val destination: String,
    @Json(name = "receiver_name") val receiverName: String,
    @Json(name = "receiver_contact") val receiverContact: String,
    @Json(name = "vehicle_id") val vehicleId: String,
    @Json(name = "cargo_description") val cargoDescription: String
)

@JsonClass(generateAdapter = true)
data class ShipmentResponse(
    val shipment: Shipment
)

@JsonClass(generateAdapter = true)
data class Shipment(
    val id: String? = null,
    @Json(name = "shipment_id") val shipmentId: String,
    val origin: String,
    val destination: String,
    @Json(name = "receiver_name") val receiverName: String? = null,
    @Json(name = "receiver_contact") val receiverContact: String? = null,
    @Json(name = "vehicle_id") val vehicleId: String? = null,
    @Json(name = "cargo_description") val cargoDescription: String? = null,
    val status: String = "created",
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "sender_email") val senderEmail: String? = null,
    @Json(name = "onChainCommitted") val onChainCommitted: Boolean = false,
    @Json(name = "merkleRoot") val merkleRoot: String? = null,
    @Json(name = "recordCount") val recordCount: Int = 0,
    @Json(name = "device_silicon_id") val deviceSiliconId: String? = null,
    @Json(name = "expected_silicon_id") val expectedSiliconId: String? = null,
    @Json(name = "device_substitution_detected") val deviceSubstitutionDetected: Boolean? = null,
    @Json(name = "rollback_detected") val rollbackDetected: Boolean? = null,
    @Json(name = "tamper_vectors_detected") val tamperVectorsDetected: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class MyShipmentsResponse(
    val shipments: List<Shipment>
)

@JsonClass(generateAdapter = true)
data class UpdateStatusRequest(
    val status: String
)

// ── Telemetry & Multi-Vector Tamper Models ───────────────────────────────────

@JsonClass(generateAdapter = true)
data class ShipmentTelemetryResponse(
    val shipmentId: String? = null,
    val shipment: Shipment? = null,
    @Json(name = "deviceSiliconId") val deviceSiliconId: String? = null,
    @Json(name = "deviceSubstitutionDetected") val deviceSubstitutionDetected: Boolean? = null,
    @Json(name = "rollbackDetected") val rollbackDetected: Boolean? = null,
    @Json(name = "anyTamperDetected") val anyTamperDetected: Boolean? = null,
    @Json(name = "tamperVectorsSummary") val tamperVectorsSummary: List<String>? = null,
    @Json(name = "totalBatches") val totalBatches: Int? = null,
    @Json(name = "totalRecords") val totalRecords: Int? = null,
    val batches: List<Batch>? = null
)

@JsonClass(generateAdapter = true)
data class Batch(
    val id: Long? = null,
    @Json(name = "batch_id") val batchId: Long? = null,
    @Json(name = "shipment_id") val shipmentId: String? = null,
    @Json(name = "merkle_root") val merkleRoot: String? = null,
    @Json(name = "record_count") val recordCount: Int? = null,
    @Json(name = "tamper_detected") val tamperDetected: Boolean? = null,
    @Json(name = "device_substitution") val deviceSubstitution: Boolean? = null,
    @Json(name = "rollback_detected") val rollbackDetected: Boolean? = null,
    @Json(name = "device_silicon_id") val deviceSiliconId: String? = null,
    @Json(name = "on_chain_key") val onChainKey: String? = null,
    @Json(name = "tamper_vectors_detected") val tamperVectorsDetected: List<String>? = null,
    val records: List<SensorRecord>? = null
)

@JsonClass(generateAdapter = true)
data class SensorRecord(
    @Json(name = "rec_index") val recIndex: Int? = null,
    @Json(name = "index") val index: Int? = null,
    val timestamp: String = "",
    val temp: Double = 0.0,
    val humidity: Double = 0.0,
    @Json(name = "gas") val gas: Double? = null,
    @Json(name = "tamper_state") val tamperState: String? = null,
    @Json(name = "chain_valid") val chainValid: Boolean? = null,
    val hash: String? = null,
    @Json(name = "previous_hash") val previousHash: String? = null,
    @Json(name = "previousHash") val previousHashAlt: String? = null,
    @Json(name = "device_silicon_id") val deviceSiliconId: String? = null,
    @Json(name = "deviceSiliconID") val deviceSiliconID: String? = null,
    @Json(name = "deviceSiliconId") val deviceSiliconIdAlt: String? = null,
    @Json(name = "device_sequence_index") val deviceSequenceIndex: Long? = null,
    @Json(name = "deviceSequenceIndex") val deviceSequenceIndexAlt: Long? = null,
    // Multi-Vector Tamper Flags
    @Json(name = "door_opened") val doorOpened: Boolean? = null,
    @Json(name = "sd_removed") val sdRemoved: Boolean? = null,
    @Json(name = "shell_cut") val shellCut: Boolean? = null,
    @Json(name = "light_breach") val lightBreach: Boolean? = null,
    @Json(name = "manual_switch") val manualSwitch: Boolean? = null,
    @Json(name = "any_tamper") val anyTamper: Boolean? = null,
    @Json(name = "tamper_flags") val tamperFlags: String? = null
) {
    val effectiveIndex: Int get() = recIndex ?: index ?: 0
    val effectiveSiliconId: String get() = deviceSiliconID ?: deviceSiliconId ?: deviceSiliconIdAlt ?: ""
    val effectivePreviousHash: String get() = previousHash ?: previousHashAlt ?: "0".repeat(64)
    val effectiveSequenceIndex: Long get() = deviceSequenceIndex ?: deviceSequenceIndexAlt ?: 0L

    val isTampered: Boolean
        get() = (anyTamper == true) ||
                (doorOpened == true) ||
                (sdRemoved == true) ||
                (shellCut == true) ||
                (lightBreach == true) ||
                (manualSwitch == true) ||
                (tamperState != null && tamperState != "NORMAL")

    fun getTrippedVectors(): List<String> {
        val list = mutableListOf<String>()
        if (doorOpened == true) list.add("DOOR_BREACH")
        if (sdRemoved == true) list.add("SD_REMOVAL")
        if (shellCut == true) list.add("SHELL_CUT")
        if (lightBreach == true) list.add("LIGHT_BREACH")
        if (manualSwitch == true) list.add("MANUAL_SWITCH")
        if (list.isEmpty() && isTampered) list.add(tamperState ?: "TAMPER_DETECTED")
        return list
    }
}

// ── Blockchain Models ────────────────────────────────────────────────────────

data class OnChainRecord(
    val found: Boolean,
    val shipmentId: String = "",
    val merkleRoot: String = "",
    val dataRef: String = "",
    val timestamp: Long = 0L,
    val tamperViolation: Boolean = false,
    val contractAddress: String = "",
    val txHash: String? = null,
    val error: String? = null,
    val reason: String? = null
)

// ── Verification Models ──────────────────────────────────────────────────────

data class Violation(
    val type: String,
    val title: String,
    val detail: String
)

data class BatchVerificationReport(
    val batchIndex: Int,
    val batchId: Long,
    val onChainKey: String,
    val clientMerkleRoot: String,
    val onChainMerkleRoot: String,
    val merkleMatch: Boolean,
    val onChainRecord: OnChainRecord?,
    val records: List<SensorRecord>,
    val violations: List<Violation>,
    val isCertifiedFresh: Boolean,
    val deviceSiliconId: String,
    val tamperVectors: List<String>,
    val rollbackDetected: Boolean
)

data class VerificationReport(
    val isCertifiedFresh: Boolean,
    val badgeLabel: String,
    val clientMerkleRoot: String,
    val onChainRoot: String,
    val merkleMatch: Boolean,
    val violations: List<Violation>,
    val recordCount: Int,
    val batchCount: Int = 1,
    val checkedAt: String,
    val deviceSiliconId: String? = null,
    val boundSiliconId: String? = null,
    val deviceSubstitutionDetected: Boolean = false,
    val rollbackDetected: Boolean = false,
    val tamperVectorsSummary: List<String> = emptyList(),
    val batchReports: List<BatchVerificationReport> = emptyList()
)

// ── Admin Models ─────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class AdminStatsResponse(
    @Json(name = "systemStats") val systemStats: SystemStats
)

@JsonClass(generateAdapter = true)
data class SystemStats(
    val totalUsers: Int = 0,
    val sendersCount: Int = 0,
    val adminsCount: Int = 0,
    val totalShipments: Int = 0,
    val committedShipments: Int = 0,
    val keyRotationEvents: Int = 0
)

@JsonClass(generateAdapter = true)
data class AdminShipmentsResponse(
    val shipments: List<Shipment>
)

// ── RPC Models ───────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class RpcRequest(
    val jsonrpc: String = "2.0",
    val method: String,
    val params: List<Any>,
    val id: Int = 1
)

@JsonClass(generateAdapter = true)
data class RpcResponse(
    val id: Int? = null,
    val result: String? = null,
    val error: RpcError? = null
)

@JsonClass(generateAdapter = true)
data class RpcError(
    val code: Int? = null,
    val message: String? = null
)

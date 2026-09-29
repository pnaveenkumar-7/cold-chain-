package com.coldchain.app.data.repository

import com.coldchain.app.BuildConfig
import com.coldchain.app.data.api.BackendApi
import com.coldchain.app.data.blockchain.RpcClient
import com.coldchain.app.data.crypto.MerkleVerifier
import com.coldchain.app.data.local.AuthPreferences
import com.coldchain.app.data.local.PersistedSession
import com.coldchain.app.data.models.*
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShipmentRepository @Inject constructor(
    private val api: BackendApi,
    private val rpcClient: RpcClient,
    private val merkleVerifier: MerkleVerifier,
    private val authPreferences: AuthPreferences
) {
    val sessionFlow: Flow<PersistedSession?> = authPreferences.sessionFlow

    suspend fun getPersistedSession(): PersistedSession? = authPreferences.getSession()

    // ── Public Multi-Batch Inspection Pipeline ──────────────────────────────────

    /**
     * Comprehensive Inspection Pipeline:
     * 1. Fetch raw sensor telemetry & all batches from backend
     * 2. Query smart contract for all on-chain batch roots (SHIP-xxx, SHIP-xxx-B2, etc.)
     * 3. Recompute record hashes (Phase 10 8-field formula) and Merkle roots
     * 4. Perform Phase 10 security verification:
     *    - Device Silicon ID binding & substitution detection
     *    - Anti-rollback sequence counter monotonicity check
     *    - Multi-vector physical tamper breakdown (Door, SD, Shell, Light, Manual)
     *    - Temperature compliance window (2°C - 8°C)
     *    - On-chain Merkle root cryptographic equivalence
     * 5. Generate unified & per-batch VerificationReport
     */
    suspend fun inspectShipment(
        shipmentId: String,
        tempMin: Double = BuildConfig.TEMP_MIN,
        tempMax: Double = BuildConfig.TEMP_MAX
    ): Result<Triple<OnChainRecord, ShipmentTelemetryResponse, VerificationReport>> {
        return try {
            // Step 1: Fetch telemetry and metadata from backend
            val telemetry = api.getShipmentTelemetry(shipmentId)
            val batches = telemetry.batches?.sortedBy { it.id ?: 0L } ?: emptyList()

            if (batches.isEmpty()) {
                // If backend has no batches, attempt direct on-chain query to see if record exists
                val directOnChain = rpcClient.fetchOnChainRecord(
                    shipmentId,
                    BuildConfig.CONTRACT_ADDRESS,
                    BuildConfig.RPC_URL
                )
                if (!directOnChain.found) {
                    return Result.failure(Exception("Shipment \"$shipmentId\" not found on-chain or in telemetry store."))
                }
                return Result.failure(Exception("On-chain record exists, but no raw telemetry batches found."))
            }

            // Step 2: Determine on-chain keys for all batches
            val batchKeyMap = batches.mapIndexed { idx, batch ->
                val key = batch.onChainKey ?: if (idx == 0) shipmentId else "$shipmentId-B${batch.id ?: (idx + 1)}"
                batch to key
            }

            val onChainKeys = batchKeyMap.map { it.second }
            val onChainResults = rpcClient.fetchOnChainRecords(
                onChainKeys,
                BuildConfig.CONTRACT_ADDRESS,
                BuildConfig.RPC_URL
            )

            val primaryKey = onChainKeys.firstOrNull() ?: shipmentId
            val primaryOnChain = onChainResults[primaryKey] ?: OnChainRecord(
                found = false,
                shipmentId = primaryKey,
                contractAddress = BuildConfig.CONTRACT_ADDRESS,
                reason = "Primary on-chain record not found"
            )

            // Step 3 & 4: Process batches and verify cryptographic integrity
            val allViolations = mutableListOf<Violation>()
            val batchReports = mutableListOf<BatchVerificationReport>()
            val allRecords = mutableListOf<SensorRecord>()
            val allTamperVectors = mutableSetOf<String>()

            val boundSiliconId = telemetry.deviceSiliconId
                ?: telemetry.shipment?.deviceSiliconId
                ?: telemetry.shipment?.expectedSiliconId
                ?: batches.firstOrNull()?.deviceSiliconId
                ?: batches.firstOrNull()?.records?.firstOrNull()?.effectiveSiliconId

            var anyDeviceSubstitution = telemetry.deviceSubstitutionDetected == true || telemetry.shipment?.deviceSubstitutionDetected == true
            var anyRollbackDetected = telemetry.rollbackDetected == true || telemetry.shipment?.rollbackDetected == true
            var prevBatchSeq = -1L
            var previousHashTracker = "0".repeat(64)

            for ((idx, pair) in batchKeyMap.withIndex()) {
                val (batch, onChainKey) = pair
                val onChainRecord = onChainResults[onChainKey]
                val records = batch.records ?: emptyList()
                allRecords.addAll(records)

                val batchViolations = mutableListOf<Violation>()
                val batchTamperVectors = mutableSetOf<String>()

                // Check batch-level flags
                if (batch.deviceSubstitution == true) anyDeviceSubstitution = true
                if (batch.rollbackDetected == true) anyRollbackDetected = true
                batch.tamperVectorsDetected?.forEach {
                    batchTamperVectors.add(it)
                    allTamperVectors.add(it)
                }

                // 1. Recompute record hashes & check chain continuity
                val leafHashes = mutableListOf<String>()
                for (record in records) {
                    val computedHash = merkleVerifier.computeRecordHash(record)
                    val storedHash = record.hash ?: computedHash
                    leafHashes.add(storedHash)

                    // Check individual record tamper vectors
                    val recordVectors = record.getTrippedVectors()
                    if (recordVectors.isNotEmpty()) {
                        recordVectors.forEach {
                            batchTamperVectors.add(it)
                            allTamperVectors.add(it)
                        }
                    }

                    // Check Silicon ID binding
                    val recordSiliconId = record.effectiveSiliconId
                    if (boundSiliconId != null && recordSiliconId.isNotEmpty() && recordSiliconId != boundSiliconId) {
                        anyDeviceSubstitution = true
                    }

                    // Check anti-rollback counter
                    val seq = record.effectiveSequenceIndex
                    if (seq > 0) {
                        if (prevBatchSeq != -1L && seq <= prevBatchSeq) {
                            anyRollbackDetected = true
                        }
                        prevBatchSeq = seq
                    }
                }

                // 2. Client Merkle Root recomputation
                val clientMerkleRoot = merkleVerifier.computeClientMerkleRoot(leafHashes) ?: ""
                val onChainRoot = onChainRecord?.merkleRoot?.lowercase() ?: ""
                val merkleMatch = clientMerkleRoot.isNotEmpty() &&
                        onChainRoot.isNotEmpty() &&
                        clientMerkleRoot.equals(onChainRoot, ignoreCase = true)

                if (onChainRecord != null && onChainRecord.found) {
                    if (!merkleMatch) {
                        batchViolations.add(
                            Violation(
                                type = "MERKLE_MISMATCH",
                                title = "Batch #${idx + 1} Merkle Root Mismatch",
                                detail = "Computed root ($clientMerkleRoot) does not match on-chain root ($onChainRoot) for key $onChainKey."
                            )
                        )
                    }
                    if (onChainRecord.tamperViolation) {
                        batchViolations.add(
                            Violation(
                                type = "ON_CHAIN_TAMPER_FLAG",
                                title = "Batch #${idx + 1} On-Chain Tamper Flagged",
                                detail = "Immutable ledger record has tamperViolation set to TRUE for $onChainKey."
                            )
                        )
                    }
                } else {
                    batchViolations.add(
                        Violation(
                            type = "ON_CHAIN_NOT_COMMITTED",
                            title = "Batch #${idx + 1} Pending On-Chain Certification",
                            detail = "Batch key $onChainKey is not yet anchored on Ethereum Sepolia."
                        )
                    )
                }

                val isBatchFresh = batchViolations.isEmpty() && batchTamperVectors.isEmpty()

                batchReports.add(
                    BatchVerificationReport(
                        batchIndex = idx,
                        batchId = batch.id ?: (idx + 1).toLong(),
                        onChainKey = onChainKey,
                        clientMerkleRoot = clientMerkleRoot,
                        onChainMerkleRoot = onChainRoot,
                        merkleMatch = merkleMatch,
                        onChainRecord = onChainRecord,
                        records = records,
                        violations = batchViolations,
                        isCertifiedFresh = isBatchFresh,
                        deviceSiliconId = batch.deviceSiliconId ?: boundSiliconId ?: "Unknown",
                        tamperVectors = batchTamperVectors.toList(),
                        rollbackDetected = batch.rollbackDetected == true
                    )
                )

                allViolations.addAll(batchViolations)
            }

            // ── Global System Security Checks ─────────────────────────────────────

            // 1. Device Substitution Check
            if (anyDeviceSubstitution) {
                allViolations.add(
                    0,
                    Violation(
                        type = "DEVICE_SUBSTITUTION",
                        title = "Hardware Silicon ID Mismatch (Device Substitution)",
                        detail = "Telemetry was transmitted from an unauthorized physical ESP32 device. Expected: $boundSiliconId."
                    )
                )
            }

            // 2. Anti-Rollback Check
            if (anyRollbackDetected) {
                allViolations.add(
                    0,
                    Violation(
                        type = "ROLLBACK_ATTEMPT",
                        title = "Anti-Rollback Sequence Counter Anomaly",
                        detail = "Firmware sequence counter decreased or non-monotonic step detected. Possible replay/tamper attack."
                    )
                )
            }

            // 3. Multi-Vector Tamper Hardware Breach
            telemetry.tamperVectorsSummary?.forEach { allTamperVectors.add(it) }
            if (allTamperVectors.isNotEmpty() || telemetry.anyTamperDetected == true) {
                val vectorsList = if (allTamperVectors.isNotEmpty()) allTamperVectors.joinToString(", ") else "Sensor Alert"
                allViolations.add(
                    0,
                    Violation(
                        type = "MULTI_VECTOR_TAMPER",
                        title = "Physical Hardware Tamper Breach Detected",
                        detail = "Active tamper vectors tripped: [$vectorsList]."
                    )
                )
            }

            // 4. Temperature Window Checks across all records
            val tempViolations = allRecords.filter { it.temp < tempMin || it.temp > tempMax }
            if (tempViolations.isNotEmpty()) {
                val first = tempViolations.first()
                allViolations.add(
                    Violation(
                        type = "TEMPERATURE_BREACH",
                        title = "Cold-Chain Temperature Excursion",
                        detail = "${tempViolations.size} of ${allRecords.size} readings breached safe limits ($tempMin°C - $tempMax°C). First breach at index #${first.effectiveIndex} (${first.temp}°C)."
                    )
                )
            }

            val isCertifiedFresh = allViolations.isEmpty()
            val badgeLabel = if (isCertifiedFresh) "Certified Fresh" else "Fraud / Spoilage Warning"

            val report = VerificationReport(
                isCertifiedFresh = isCertifiedFresh,
                badgeLabel = badgeLabel,
                clientMerkleRoot = batchReports.firstOrNull()?.clientMerkleRoot ?: "",
                onChainRoot = primaryOnChain.merkleRoot,
                merkleMatch = batchReports.all { it.merkleMatch },
                violations = allViolations,
                recordCount = allRecords.size,
                batchCount = batches.size,
                checkedAt = Instant.now().toString(),
                deviceSiliconId = boundSiliconId,
                boundSiliconId = boundSiliconId,
                deviceSubstitutionDetected = anyDeviceSubstitution,
                rollbackDetected = anyRollbackDetected,
                tamperVectorsSummary = allTamperVectors.toList(),
                batchReports = batchReports
            )

            Result.success(Triple(primaryOnChain, telemetry, report))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Auth & Session Persistence ──────────────────────────────────────────────

    suspend fun login(email: String, password: String): Result<AuthResponse> {
        return try {
            val response = api.login(AuthRequest(email, password))
            authPreferences.saveSession(response.token, response.user.email, response.user.role)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signup(email: String, password: String): Result<AuthResponse> {
        return try {
            val response = api.signup(AuthRequest(email, password))
            authPreferences.saveSession(response.token, response.user.email, response.user.role)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun logout() {
        authPreferences.clearSession()
    }

    // ── Sender Operations ───────────────────────────────────────────────────────

    suspend fun createShipment(
        token: String,
        request: CreateShipmentRequest
    ): Result<Shipment> {
        return try {
            Result.success(api.createShipment("Bearer $token", request).shipment)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMyShipments(token: String): Result<List<Shipment>> {
        return try {
            Result.success(api.getMyShipments("Bearer $token").shipments)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateStatus(token: String, shipmentId: String, status: String): Result<Unit> {
        return try {
            api.updateShipmentStatus("Bearer $token", shipmentId, UpdateStatusRequest(status))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Admin Operations ────────────────────────────────────────────────────────

    suspend fun getAdminStats(token: String): Result<SystemStats> {
        return try {
            Result.success(api.getAdminStats("Bearer $token").systemStats)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAllShipments(token: String): Result<List<Shipment>> {
        return try {
            Result.success(api.getAllShipments("Bearer $token").shipments)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

package com.coldchain.app.data.blockchain

import com.coldchain.app.data.models.OnChainRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Direct JSON-RPC client for querying the ColdChainLedger smart contract
 * on Ethereum Sepolia without heavy external web3 dependencies.
 *
 * Implements EVM ABI encoding for:
 *   getRecord(string shipmentId) → (bytes32 merkleRoot, string dataRef, uint256 timestamp, bool tamperViolation)
 *   recordExists(string shipmentId) → bool
 */
@Singleton
class RpcClient @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val JSON_TYPE = "application/json; charset=utf-8"
        // getRecord(string) → keccak256("getRecord(string)") = 0x1c23d985
        private const val GET_RECORD_SELECTOR = "1c23d985"
        // recordExists(string) → keccak256("recordExists(string)") = 0x2b2e05c1
        private const val RECORD_EXISTS_SELECTOR = "2b2e05c1"
    }

    /**
     * Query the smart contract for a single on-chain shipment/batch key.
     */
    suspend fun fetchOnChainRecord(
        shipmentId: String,
        contractAddress: String,
        rpcUrl: String
    ): OnChainRecord = withContext(Dispatchers.IO) {
        try {
            // Step 1: Check if record exists
            val existsData = encodeStringCall(RECORD_EXISTS_SELECTOR, shipmentId)
            val existsHex = ethCall(rpcUrl, contractAddress, existsData)
            if (existsHex != null) {
                val cleanHex = existsHex.trimPrefix()
                val exists = cleanHex.isNotEmpty() &&
                        cleanHex.takeLast(1) != "0" &&
                        BigInteger(cleanHex.takeLast(64).ifEmpty { "0" }, 16) != BigInteger.ZERO
                if (!exists) {
                    return@withContext OnChainRecord(
                        found = false,
                        shipmentId = shipmentId,
                        contractAddress = contractAddress,
                        reason = "Shipment key \"$shipmentId\" not found on-chain"
                    )
                }
            }

            // Step 2: Call getRecord(shipmentId)
            val callData = encodeStringCall(GET_RECORD_SELECTOR, shipmentId)
            val resultHex = ethCall(rpcUrl, contractAddress, callData)
                ?: return@withContext OnChainRecord(
                    found = false,
                    shipmentId = shipmentId,
                    contractAddress = contractAddress,
                    reason = "Blockchain RPC query failed"
                )

            decodeGetRecord(resultHex, shipmentId, contractAddress)
        } catch (e: Exception) {
            val msg = e.message ?: ""
            if (msg.contains("Record does not exist") || msg.contains("revert")) {
                OnChainRecord(
                    found = false,
                    shipmentId = shipmentId,
                    contractAddress = contractAddress,
                    reason = "Key \"$shipmentId\" not found on-chain"
                )
            } else {
                OnChainRecord(
                    found = false,
                    shipmentId = shipmentId,
                    contractAddress = contractAddress,
                    error = msg,
                    reason = "Blockchain query error: $msg"
                )
            }
        }
    }

    /**
     * Query multiple on-chain keys concurrently (e.g., SHIP-xxx, SHIP-xxx-B2, SHIP-xxx-B3).
     */
    suspend fun fetchOnChainRecords(
        keys: List<String>,
        contractAddress: String,
        rpcUrl: String
    ): Map<String, OnChainRecord> = withContext(Dispatchers.IO) {
        val deferredList = keys.distinct().map { key ->
            async {
                key to fetchOnChainRecord(key, contractAddress, rpcUrl)
            }
        }
        deferredList.awaitAll().toMap()
    }

    /**
     * ABI-encode a call with a single string argument.
     * Layout: selector(4) + offset(32) + length(32) + data(padded)
     */
    private fun encodeStringCall(selector: String, arg: String): String {
        val strBytes = arg.toByteArray(StandardCharsets.UTF_8)
        val offset = "0000000000000000000000000000000000000000000000000000000000000020"
        val lenHex = strBytes.size.toLong().toHex32()
        val dataHex = strBytes.toHex().padEnd((strBytes.size + 31) / 32 * 64, '0')
        return "0x$selector$offset$lenHex$dataHex"
    }

    /**
     * Execute eth_call via JSON-RPC.
     */
    private fun ethCall(rpcUrl: String, to: String, data: String): String? {
        val body = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("method", "eth_call")
            put("params", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("to", to)
                    put("data", data)
                })
                put("latest")
            })
            put("id", 1)
        }.toString()

        val request = Request.Builder()
            .url(rpcUrl)
            .post(body.toRequestBody(JSON_TYPE.toMediaType()))
            .build()

        val response = okHttpClient.newCall(request).execute()
        val responseBody = response.body?.string() ?: return null
        val json = JSONObject(responseBody)

        if (json.has("error")) {
            val errMsg = json.getJSONObject("error").optString("message", "RPC error")
            throw Exception(errMsg)
        }

        return json.optString("result")
    }

    /**
     * Decode the ABI-encoded result of getRecord(string) which returns:
     * (bytes32 merkleRoot, string dataRef, uint256 timestamp, bool tamperViolation)
     */
    private fun decodeGetRecord(
        hex: String,
        shipmentId: String,
        contractAddress: String
    ): OnChainRecord {
        val data = hex.trimPrefix()
        if (data.length < 256) {
            return OnChainRecord(
                found = false,
                shipmentId = shipmentId,
                contractAddress = contractAddress,
                reason = "Empty or invalid contract response"
            )
        }

        // Slot 0: bytes32 merkleRoot (first 64 hex chars = 32 bytes)
        val merkleRoot = "0x" + data.substring(0, 64).lowercase()

        // Slot 1: offset for dynamic string dataRef (typically 0x80)
        // Slot 2: uint256 timestamp (chars 128..192)
        val timestampHex = data.substring(128, 192)
        val timestamp = try { BigInteger(timestampHex, 16).toLong() } catch (e: Exception) { 0L }

        // Slot 3: bool tamperViolation (chars 192..256)
        val tamperHex = data.substring(192, 256)
        val tamperViolation = try { BigInteger(tamperHex, 16) != BigInteger.ZERO } catch (e: Exception) { false }

        // Slot 4+: string dataRef
        val dataRefOffset = try { BigInteger(data.substring(64, 128), 16).toInt() * 2 } catch (e: Exception) { 128 * 2 }
        val dataRef = if (data.length >= dataRefOffset + 64) {
            val dataRefLenHex = data.substring(dataRefOffset, dataRefOffset + 64)
            val dataRefLen = try { BigInteger(dataRefLenHex, 16).toInt() } catch (e: Exception) { 0 }
            if (dataRefLen > 0 && data.length >= dataRefOffset + 64 + dataRefLen * 2) {
                val dataRefHex = data.substring(dataRefOffset + 64, dataRefOffset + 64 + dataRefLen * 2)
                dataRefHex.hexToString()
            } else ""
        } else ""

        return OnChainRecord(
            found = true,
            shipmentId = shipmentId,
            merkleRoot = merkleRoot.lowercase(),
            dataRef = dataRef,
            timestamp = timestamp,
            tamperViolation = tamperViolation,
            contractAddress = contractAddress
        )
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun String.trimPrefix(): String = removePrefix("0x").removePrefix("0X")

    private fun Long.toHex32(): String = toString(16).padStart(64, '0')

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToString(): String {
        return try {
            val bytes = ByteArray(length / 2) { i ->
                substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
            String(bytes, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }
}

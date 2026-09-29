package com.coldchain.app.data.crypto

import com.coldchain.app.data.models.SensorRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Client-side binary Merkle tree root computation & record hashing.
 * Exactly matches the JavaScript implementation in:
 *   - backend/src/hashChain.js
 *   - backend/src/merkle.js
 *   - dashboard/src/merkle.js
 */
@Singleton
class MerkleVerifier @Inject constructor() {

    /**
     * Format tamper flags canonically for client-side hash recomputation.
     * Matches formatTamperFlags in hashChain.js & merkle.js.
     */
    fun formatTamperFlags(record: SensorRecord): String {
        if (!record.tamperFlags.isNullOrBlank()) {
            return record.tamperFlags
        }
        if (record.doorOpened != null || record.sdRemoved != null ||
            record.shellCut != null || record.lightBreach != null ||
            record.manualSwitch != null || record.anyTamper != null
        ) {
            val door = record.doorOpened == true
            val sd = record.sdRemoved == true
            val shell = record.shellCut == true
            val light = record.lightBreach == true
            val manual = record.manualSwitch == true
            val any = record.anyTamper == true || door || sd || shell || light || manual
            return """{"doorOpened":$door,"sdRemoved":$sd,"shellCut":$shell,"lightBreach":$light,"manualSwitch":$manual,"anyTamper":$any}"""
        }
        return record.tamperState ?: "NORMAL"
    }

    /**
     * Compute a single record's expected SHA-256 hash in Phase 10 canonical format:
     *   `${index}|${timestamp}|${temp}|${humidity}|${gas}|${tamperFlags}|${deviceSiliconID}|${previousHash}`
     */
    fun computeRecordHash(record: SensorRecord): String {
        val index = record.effectiveIndex
        val timestamp = record.timestamp
        val temp = formatNumber(record.temp)
        val humidity = formatNumber(record.humidity)
        val gas = record.gas?.let { formatNumber(it) } ?: ""
        val tamperFlags = formatTamperFlags(record)
        val siliconId = record.effectiveSiliconId
        val prevHash = record.effectivePreviousHash

        val payload = if (siliconId.isNotEmpty()) {
            listOf(index.toString(), timestamp, temp, humidity, gas, tamperFlags, siliconId, prevHash).joinToString("|")
        } else {
            listOf(index.toString(), timestamp, temp, humidity, gas, tamperFlags, prevHash).joinToString("|")
        }

        return sha256Hex(payload)
    }

    private fun formatNumber(num: Double): String {
        return if (num == num.toLong().toDouble()) {
            num.toLong().toString()
        } else {
            num.toString()
        }
    }

    /**
     * Compute the Merkle root from a list of leaf hash strings.
     * These are the 'hash' field strings from each SensorRecord.
     *   - SHA-256 hash each leaf string
     *   - Build binary tree level-by-level, duplicating last node at odd levels
     *   - Return 0x-prefixed lowercase hex root
     */
    suspend fun computeClientMerkleRoot(leafHashes: List<String>): String? =
        withContext(Dispatchers.Default) {
            if (leafHashes.isEmpty()) return@withContext null

            // Hash each leaf
            var level = leafHashes.map { sha256Hex(it) }

            // Build tree bottom-up
            while (level.size > 1) {
                val next = mutableListOf<String>()
                var i = 0
                while (i < level.size) {
                    val left = level[i]
                    val right = if (i + 1 < level.size) level[i + 1] else level[i] // duplicate last
                    next.add(sha256Hex(left + right))
                    i += 2
                }
                level = next
            }

            val rawRoot = level[0]
            if (rawRoot.startsWith("0x")) rawRoot.lowercase()
            else "0x${rawRoot.lowercase()}"
        }

    /**
     * SHA-256 of a string, returns lowercase hex.
     */
    fun sha256Hex(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest(input.toByteArray(StandardCharsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
}

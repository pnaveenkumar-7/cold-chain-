package com.coldchain.app.data.crypto

import com.coldchain.app.data.models.SensorRecord
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class MerkleVerifierTest {

    private val verifier = MerkleVerifier()

    @Test
    fun testPhase10CanonicalRecordHashMatch() {
        // Test record identical to backend test_hash_crosscheck.js
        val record = SensorRecord(
            recIndex = 0,
            timestamp = "2026-09-07T12:00:00Z",
            temp = 4.5,
            humidity = 65.0,
            gas = 320.0,
            doorOpened = false,
            sdRemoved = false,
            shellCut = false,
            lightBreach = false,
            manualSwitch = false,
            anyTamper = false,
            tamperFlags = """{"doorOpened":false,"sdRemoved":false,"shellCut":false,"lightBreach":false,"manualSwitch":false,"anyTamper":false}""",
            deviceSiliconID = "ESP32-B48A0A1F524C",
            previousHash = "0".repeat(64)
        )

        val computedHash = verifier.computeRecordHash(record)
        val expectedHash = "fd649c593fa7392c4af5df72291813f40bd1574a3cbe882106aaf85c3cb99f62"

        assertEquals("Kotlin computed record hash must match backend hashChain.js exactly", expectedHash, computedHash)
    }

    @Test
    fun testClientMerkleRootComputation() = runBlocking {
        val leafHash = "fd649c593fa7392c4af5df72291813f40bd1574a3cbe882106aaf85c3cb99f62"
        val clientRoot = verifier.computeClientMerkleRoot(listOf(leafHash))
        val expectedRoot = "0xa58164cde3474537f5355b0ba80481a922d4cab1a841cb0de77f701b78204465"

        assertEquals("Kotlin computed Merkle root must match backend merkle.js exactly", expectedRoot, clientRoot)
    }
}

package com.liu.andy.demo.nfctagwriter.nfc

import net.bplearning.ntag424.constants.Ntag424
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

/**
 * Unit tests for NTAG424Verifier
 */
class NTAG424VerifierTest {

    @Test
    fun testWithValidData() {
        val success = NTAG424Verifier(masterKey = ByteArray(16) { 0x00 }).runVerification()
        assertTrue(success)
    }

    @Test
    fun testVerifySDMMAC_withValidData() {
        // Test data from user request
        val uidHex = "0464171A282290"
        val counterHex = "00009E"
        val cmacHex = "A0BDA87E01B7516E"

        // Create verifier with KEY3 parameters matching the tag configuration
        // KEY3 base key: FACTORY_KEY (all zeros)
        // KEY3 system identifier: "testing"
        // KEY3 version: 1
        val key3BaseKey = Ntag424.FACTORY_KEY // All zeros
        val key3SystemId = "testing".toByteArray(StandardCharsets.UTF_8)
        val key3Version = 1
        val key0Str = "915565AB915565AB"
        
        val verifier = NTAG424Verifier(
            masterKey = key0Str.toByteArray(), // Not used for SDM MAC, but kept for compatibility
            key3BaseKey = key3BaseKey,
            key3SystemIdentifier = key3SystemId,
            key3Version = key3Version
        )
        
        // Test direct method
        val result = verifier.verifySDMMAC(uidHex, counterHex, cmacHex)

        assertTrue("SDM MAC verification should pass with valid data", result)
    }

    @Test
    fun testVerifySDMMAC_withURL() {
        // Test data from user request
        val gid = "915565a3-65c7-4a2b-8629-194d80ed824b"
        val rule = "249"
        val uidHex = "0464171A282290"
        val counterHex = "00009E"
        val cmacHex = "A0BDA87E01B7516E"
        
        // Construct URL with SDM parameters
        val url = "https://freeway1979.github.io/nfc?gid=$gid&rule=$rule&u=$uidHex&c=$counterHex&m=$cmacHex"
        
        // Create verifier with KEY3 parameters matching the tag configuration
        val key3BaseKey = Ntag424.FACTORY_KEY
        val key3SystemId = "testing".toByteArray(StandardCharsets.UTF_8)
        val key3Version = 1
        val key0Str = "915565AB915565AB"
        
        val verifier = NTAG424Verifier(
            masterKey = key0Str.toByteArray(),
            key3BaseKey = key3BaseKey,
            key3SystemIdentifier = key3SystemId,
            key3Version = key3Version
        )
        
        // Test URL parsing method
        val result = verifier.verifySDMMAC(url)
        
        assertTrue("SDM MAC verification should pass with valid URL", result)
    }

    @Test
    fun testVerifySDMMAC_withInvalidCMAC() {
        val uidHex = "0464171A282290"
        val counterHex = "00009E"
        val invalidCmacHex = "0000000000000000" // Invalid MAC
        
        val key3BaseKey = Ntag424.FACTORY_KEY
        val key3SystemId = "testing".toByteArray(StandardCharsets.UTF_8)
        val key3Version = 1
        val key0Str = "915565AB915565AB"
        
        val verifier = NTAG424Verifier(
            masterKey = key0Str.toByteArray(),
            key3BaseKey = key3BaseKey,
            key3SystemIdentifier = key3SystemId,
            key3Version = key3Version
        )
        
        val result = verifier.verifySDMMAC(uidHex, counterHex, invalidCmacHex)
        
        assertFalse("SDM MAC verification should fail with invalid CMAC", result)
    }

    @Test
    fun testVerifySDMMAC_withInvalidCounter() {
        val uidHex = "0464171A282290"
        val invalidCounterHex = "000000" // Different counter
        val cmacHex = "A0BDA87E01B7516E"
        
        val key3BaseKey = Ntag424.FACTORY_KEY
        val key3SystemId = "testing".toByteArray(StandardCharsets.UTF_8)
        val key3Version = 1
        val key0Str = "915565AB915565AB"
        
        val verifier = NTAG424Verifier(
            masterKey = key0Str.toByteArray(),
            key3BaseKey = key3BaseKey,
            key3SystemIdentifier = key3SystemId,
            key3Version = key3Version
        )
        
        val result = verifier.verifySDMMAC(uidHex, invalidCounterHex, cmacHex)
        
        assertFalse("SDM MAC verification should fail with invalid counter", result)
    }

    @Test
    fun testVerifySDMMAC_withInvalidUID() {
        val invalidUidHex = "00000000000000" // Different UID
        val counterHex = "00009E"
        val cmacHex = "A0BDA87E01B7516E"
        
        val key3BaseKey = Ntag424.FACTORY_KEY
        val key3SystemId = "testing".toByteArray(StandardCharsets.UTF_8)
        val key3Version = 1
        val key0Str = "915565AB915565AB"
        
        val verifier = NTAG424Verifier(
            masterKey = key0Str.toByteArray(),
            key3BaseKey = key3BaseKey,
            key3SystemIdentifier = key3SystemId,
            key3Version = key3Version
        )
        
        val result = verifier.verifySDMMAC(invalidUidHex, counterHex, cmacHex)
        
        assertFalse("SDM MAC verification should fail with invalid UID", result)
    }

    @Test
    fun testVerifySDMMAC_withMissingURLParameters() {
        val url = "https://freeway1979.github.io/nfc?gid=915565a3-65c7-4a2b-8629-194d80ed824b&rule=249"
        // Missing u, c, m parameters
        
        val key3BaseKey = Ntag424.FACTORY_KEY
        val key3SystemId = "testing".toByteArray(StandardCharsets.UTF_8)
        val key3Version = 1
        val key0Str = "915565AB915565AB"
        
        val verifier = NTAG424Verifier(
            masterKey = key0Str.toByteArray(),
            key3BaseKey = key3BaseKey,
            key3SystemIdentifier = key3SystemId,
            key3Version = key3Version
        )
        
        val result = verifier.verifySDMMAC(url)
        
        assertFalse("SDM MAC verification should fail with missing URL parameters", result)
    }
}


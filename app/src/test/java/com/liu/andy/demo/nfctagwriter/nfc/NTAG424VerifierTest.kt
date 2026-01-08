package com.liu.andy.demo.nfctagwriter.nfc

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for NTAG424Verifier
 */
class NTAG424VerifierTest {

    @Test
    fun testVerifySDMMAC_withValidData() {
        // Test data - must be generated with FACTORY_KEY (all zeros) as key3BaseKey
        // Note: These test values may need to be updated with actual data from a tag
        // configured with FACTORY_KEY for Key 3
        val uidHex = "04A3151A282290"
        val counterHex = "000005"
        val cmacHex = "8C76B2AEA92FF00B"

        // NTAG424Verifier is an object, call static method directly
        val result = NTAG424Verifier().verifySDMMAC(uidHex, counterHex, cmacHex)

        // Note: This test may fail if the test data was generated with a different key
        // The assertion is commented to allow the test to pass, but you should update
        // the test data to match actual tag data using FACTORY_KEY
         assertTrue("SDM MAC verification should pass with valid data", result)
    }

    @Test
    fun testVerifySDMMAC_withInvalidCMAC() {
        val uidHex = "0464171A282290"
        val counterHex = "00009E"
        val invalidCmacHex = "0000000000000000" // Invalid MAC
        
        // NTAG424Verifier is an object, call static method directly
        val result = NTAG424Verifier().verifySDMMAC(uidHex, counterHex, invalidCmacHex)
        
        assertFalse("SDM MAC verification should fail with invalid CMAC", result)
    }

    @Test
    fun testVerifySDMMAC_withInvalidCounter() {
        val uidHex = "0464171A282290"
        val invalidCounterHex = "000000" // Different counter
        // Using a CMAC that would be valid for the original counter but invalid for this one
        val cmacHex = "A0BDA87E01B7516E"
        
        // NTAG424Verifier is an object, call static method directly
        val result = NTAG424Verifier().verifySDMMAC(uidHex, invalidCounterHex, cmacHex)
        
        assertFalse("SDM MAC verification should fail with invalid counter", result)
    }

    @Test
    fun testVerifySDMMAC_withInvalidUID() {
        val invalidUidHex = "00000000000000" // Different UID
        val counterHex = "00009E"
        // Using a CMAC that would be valid for the original UID but invalid for this one
        val cmacHex = "A0BDA87E01B7516E"
        
        // NTAG424Verifier is an object, call static method directly
        val result = NTAG424Verifier().verifySDMMAC(invalidUidHex, counterHex, cmacHex)
        
        assertFalse("SDM MAC verification should fail with invalid UID", result)
    }

    @Test
    fun testVerifySDMMAC_withMissingURLParameters() {
        val url = "https://freeway1979.github.io/nfc?gid=915565a3-65c7-4a2b-8629-194d80ed824b&rule=249"
        // Missing u, c, m parameters
        
        // NTAG424Verifier is an object, call static method directly
        val result = NTAG424Verifier().verifySDMMAC(url)
        
        assertFalse("SDM MAC verification should fail with missing URL parameters", result)
    }

    @Test
    fun testIsFreshScan() {
        // Test the isFreshScan functionality
        val uid = "0464171A282290"
        val counter1 = 100
        val counter2 = 200
        val counter3 = 150 // Lower than counter2, should be rejected
        
        // First scan should be fresh
        val result1 = NTAG424Verifier.isFreshScan(uid, counter1)
        assertTrue("First scan should be fresh", result1)
        
        // Second scan with higher counter should be fresh
        val result2 = NTAG424Verifier.isFreshScan(uid, counter2)
        assertTrue("Second scan with higher counter should be fresh", result2)
        
        // Third scan with lower counter should be rejected (replay attack)
        val result3 = NTAG424Verifier.isFreshScan(uid, counter3)
        assertFalse("Scan with lower counter should be rejected (replay attack)", result3)
        
        // Scan with same counter should be rejected
        val result4 = NTAG424Verifier.isFreshScan(uid, counter2)
        assertFalse("Scan with same counter should be rejected", result4)
    }
}


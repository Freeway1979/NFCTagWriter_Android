package com.liu.andy.demo.nfctagwriter.nfc

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import android.net.Uri
import androidx.core.net.toUri
import net.bplearning.ntag424.constants.Ntag424
import java.net.URI
import java.net.URLDecoder

class NTAG424Verifier(
    private val key3BaseKey: ByteArray = Ntag424.FACTORY_KEY
) {
    companion object {
        val nfcUrlPattern = "https?://(?:www\\.)?freeway1979\\.github\\.io/nfc" // "https?://(?:www\\.)?firewalla\\.com/nfc",
        // Static map to cache lastCounter for each uid
        // Using ConcurrentHashMap for thread-safe access
        private val lastCounterCache: MutableMap<String, Int> = ConcurrentHashMap()

        fun isFreshScan(uid: String, currentCounter: Int): Boolean {
            val lastCounter = lastCounterCache.getOrDefault(uid, 0)
            if (currentCounter > lastCounter) {
                lastCounterCache[uid] = currentCounter
                return true
            }
            return false // Replay Attack detected!
        }
    }

    /**
     * Verifies the SDM MAC from a URL string.
     * Parses the URL to extract UID, counter, and MAC parameters, then calls verifySDMMAC.
     * @param url The URL string containing SDM parameters (e.g., "https://example.com/nfc?u=044B6A4A4E6880&c=000021&m=3E12626CBBFB3FB9")
     * @return true if the MAC is valid, false otherwise
     */
    fun verifySDMMAC(url: String): Boolean {
        try {
            Log.d("NTAG424Verifier", "verifySDMMAC: url:$url")
            
            // Validate URL format first
            if (url.isBlank()) {
                Log.e("NTAG424Verifier", "URL is blank")
                return false
            }
            
            // Use Uri.parse() - it always returns a Uri object in Android runtime
            // In unit tests without Android framework, this may not work correctly
            val uri = Uri.parse(url)
            if (uri == null) {
                Log.e("NTAG424Verifier", "URL is invalid")
                return false
            }
            val uidHex = uri.getQueryParameter("u")
            val counterHex = uri.getQueryParameter("c")
            val receivedMacHex = uri.getQueryParameter("m")
            
            if (uidHex == null) {
                Log.e("NTAG424Verifier", "Missing 'u' parameter in URL: $url")
                return false
            }
            if (counterHex == null) {
                Log.e("NTAG424Verifier", "Missing 'c' parameter in URL: $url")
                return false
            }
            if (receivedMacHex == null) {
                Log.e("NTAG424Verifier", "Missing 'm' parameter in URL: $url")
                return false
            }
            
            Log.d("NTAG424Verifier", "verifySDMMAC: uidHex:$uidHex counterHex:$counterHex macHex:$receivedMacHex")

            return verifySDMMAC(uidHex, counterHex, receivedMacHex)
        } catch (e: Exception) {
            Log.e("NTAG424Verifier", "verifySDMMAC error: ${e.message}", e)
            e.printStackTrace()
            return false
        }
    }

    /**
     * Verifies the SDM MAC from the scanned URL.
     * Uses PiccData class to correctly calculate CMAC following NTAG424 specification.
     * @param uidHex The 'u' parameter from URL (7-byte hex string)
     * @param counterHex The 'c' parameter from URL (3-byte hex string)
     * @param receivedMacHex The 'm' parameter from URL (8-byte hex string)
     */
    fun verifySDMMAC(uidHex: String, counterHex: String, receivedMacHex: String): Boolean {
        try {
            val piccData = PiccData.decodeAndVerifyMac(
                uidString = uidHex,
                readCounterString = counterHex,
                macString = receivedMacHex,
                macFileKey = key3BaseKey,
                usesLrp = false // Using AES, not LRP
            )
            val isValid = piccData != null
            Log.d(
                "NTAG424Verifier",
                "verifySDMMAC: ${if (isValid) "✅ PASSED" else "❌FAILED"} - uid:$uidHex counter:$counterHex mac:$receivedMacHex"
            )
            return isValid
        } catch (e: Exception) {
            Log.e("NTAG424Verifier", "verifySDMMAC error:❌ ${e.message}", e)
            return false
        }
    }
}
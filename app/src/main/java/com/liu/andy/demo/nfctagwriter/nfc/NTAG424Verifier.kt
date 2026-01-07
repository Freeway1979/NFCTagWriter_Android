package com.liu.andy.demo.nfctagwriter.nfc

import android.net.Uri
import android.util.Log
import net.bplearning.ntag424.util.ByteUtil
import net.bplearning.ntag424.util.Crypto
import java.util.concurrent.ConcurrentHashMap
import androidx.core.net.toUri
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.macs.CMac
import org.bouncycastle.crypto.params.KeyParameter

class NTAG424Verifier(
    private val masterKey: ByteArray,
    private val key3BaseKey: ByteArray = ByteArray(16) { 0x00 }, // FACTORY_KEY (all zeros) by default
    private val key3SystemIdentifier: ByteArray = "testing".toByteArray(),
    private val key3Version: Int = 1
) {
    companion object {
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

    // ==========================================
    // 请在此处填入你的 16字节 Master Key 2 (Hex)
    // ==========================================
    private val MASTER_KEY_2_HEX = "00000000000000000000000000000000"

    fun runVerification(): Boolean {
        val masterKey = hexToBytes(MASTER_KEY_2_HEX)

        // 1. 从你的 URL 提取的参数
        val uidHex = "0464171A282290"
        val counterHex = "00009E"
        val receivedMacHex = "A0BDA87E01B7516E"

        // 2. 派生标签专属密钥 (Diversify Key)
        val tagKey = deriveTagKey(masterKey, hexToBytes(uidHex))
        println("1. 派生出的标签密钥 (Key 2): ${tagKey.toHex()}")

        // 3. 构造 PICCData (C7 + UID + Counter小端)
        // URL 是 00009E -> 数组是 [00, 00, 9E] -> 小端计算是 [9E, 00, 00]
        val counterBytes = hexToBytes(counterHex).reversedArray()
        val piccData = byteArrayOf(0xC7.toByte()) + hexToBytes(uidHex) + counterBytes
        println("2. 待计算的 PICCData 明文: ${piccData.toHex()}")

        // 4. 计算 CMAC 并按照 NXP 规则截断
        val fullMac = calculateCMAC(tagKey, piccData)
        val calculatedMac = truncateNXP(fullMac)

        println("3. 服务器计算出的 MAC: $calculatedMac")
        println("4. URL 中收到的 MAC:   $receivedMacHex")

        if (calculatedMac.equals(receivedMacHex, ignoreCase = true)) {
            println("\n✅ 验证成功！Master Key 正确，且数据未被篡改。")
            return true
        } else {
            println("\n❌ 验证失败！请检查 Master Key 是否正确。")
            return false
        }
    }

    private fun deriveTagKey(masterKey: ByteArray, uid: ByteArray): ByteArray {
        val sv = ByteArray(32)
        sv[0] = 0x01
        "SDMMACKey".toByteArray(Charsets.US_ASCII).copyInto(sv, 1)
        uid.copyInto(sv, 16)
        sv[25] = 0x80.toByte() // 128位密钥长度标识
        return calculateCMAC(masterKey, sv)
    }

    private fun calculateCMAC(key: ByteArray, data: ByteArray): ByteArray {
        val cmac = CMac(AESEngine())
        cmac.init(KeyParameter(key))
        cmac.update(data, 0, data.size)
        val out = ByteArray(16)
        cmac.doFinal(out, 0)
        return out
    }

    private fun truncateNXP(fullMac: ByteArray): String {
        val truncated = ByteArray(8)
        for (i in 0 until 8) {
            // 关键：NTAG424 提取 16字节 CMAC 中的奇数索引位 (1, 3, 5, 7, 9, 11, 13, 15)
            truncated[i] = fullMac[2 * i + 1]
        }
        return truncated.toHex()
    }

    private fun hexToBytes(s: String): ByteArray =
        s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02X".format(it) }
    
    /**
     * Verifies the SDM MAC from a URL string.
     * Parses the URL to extract UID, counter, and MAC parameters, then calls verifySDMMAC.
     * @param url The URL string containing SDM parameters (e.g., "https://example.com/nfc?u=044B6A4A4E6880&c=000021&m=3E12626CBBFB3FB9")
     * @return true if the MAC is valid, false otherwise
     */
    fun verifySDMMAC(url: String): Boolean {
        try {
            val uri = url.toUri()
            Log.d("NTAG424Verifier", "verifySDMMAC: url:$url")
            val uidHex = uri.getQueryParameter("u") ?: return false
            val counterHex = uri.getQueryParameter("c") ?: return false
            val receivedMacHex = uri.getQueryParameter("m") ?: return false
            Log.d("NTAG424Verifier", "verifySDMMAC: uidHex:$uidHex counterHex:$counterHex macHex:$receivedMacHex")

            return verifySDMMAC(uidHex, counterHex, receivedMacHex)
        } catch (e: Exception) {
            Log.e("NTAG424Verifier",e.localizedMessage ?: "")
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
            // 1. Derive the tag-specific diversified key (macFileKey)
            val uid = hexToBytes(uidHex)
            val diversifiedKey = deriveKey(uid)

            // 2. Use PiccData.decodeAndVerifyMac to verify the MAC
            // This method correctly:
            // - Creates a PiccData object with uid and counter
            // - Derives session key from macFileKey
            // - Calculates CMAC using the session key
            // - Shortens the CMAC
            // - Compares with received MAC
            val piccData = PiccData.decodeAndVerifyMac(
                uidString = uidHex,
                readCounterString = counterHex,
                macString = receivedMacHex,
                macFileKey = diversifiedKey,
                usesLrp = false // Using AES, not LRP
            )

            val isValid = piccData != null
            Log.d("NTAG424Verifier", "verifySDMMAC: ${if (isValid) "PASSED" else "FAILED"} - uid:$uidHex counter:$counterHex mac:$receivedMacHex")
            return isValid
        } catch (e: Exception) {
            Log.e("NTAG424Verifier", "verifySDMMAC error: ${e.message}", e)
            return false
        }
    }

    private fun deriveKey(uid: ByteArray): ByteArray {
        // KEY3 is used for SDM MAC (sdmFileReadPerm = ACCESS_KEY3)
        // KEY3 is diversified with systemIdentifier "testing" and version 1
        // Base key is FACTORY_KEY (all zeros)
        
        // Construct SV for KEY3 Diversification (NIST SP800-108)
        // Format: [Label] + [System Identifier] + [Version] + [UID] + [Output Length]
        val label = "SDMMACKey".toByteArray()
        val systemId = key3SystemIdentifier
        val version = key3Version.toByte()
        
        // Calculate SV size: label (9) + systemId + version (1) + uid (7) + output length (1) + padding
        val svSize = 32 // Standard size for NIST SP800-108
        val sv = ByteArray(svSize)
        var offset = 0
        
        // Label
        sv[offset] = 0x01 // Label length indicator
        offset++
        System.arraycopy(label, 0, sv, offset, label.size)
        offset += label.size
        
        // System Identifier
        System.arraycopy(systemId, 0, sv, offset, systemId.size)
        offset += systemId.size
        
        // Version
        sv[offset] = version
        offset++
        
        // UID (7 bytes)
        System.arraycopy(uid, 0, sv, offset, 7)
        offset += 7
        
        // Output length: 128 bits (0x80)
        sv[offset] = 0x80.toByte()
        
        Log.d("NTAG424Verifier", "deriveKey: Using KEY3 base key (FACTORY_KEY), systemId=${String(systemId)}, version=$key3Version")
        Log.d("NTAG424Verifier", "deriveKey: SV hex=${sv.toHex()}")
        
        // Use KEY3's base key (FACTORY_KEY) for diversification
        // Use Crypto.simpleAesCmac for key derivation (NIST SP800-108)
        val diversifiedKey = Crypto.simpleAesCmac(key3BaseKey, sv)
        Log.d("NTAG424Verifier", "deriveKey: Diversified key hex=${diversifiedKey.toHex()}")
        return diversifiedKey
    }
}
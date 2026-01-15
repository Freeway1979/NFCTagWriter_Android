package com.liu.andy.demo.nfctagwriter.nfc

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NTAgUtilsTest {

    @Test
    fun testGenerateKey0Pass() {
        val boxLicenseId = "f6cfb225-d69b-4fbb-9eda-624b0b20516e"
        val key0Pass = NTagUtils.generateKey0Pass(boxLicenseId)
        assertTrue(key0Pass == "4EBC6FCF836695687EBFA56CCC049271")
    }

    @Test
    fun testECCSignatureWithUid() {
        val uid = "0464171A282290"
        val isValid = NTagUtils.testECCSignatureWithUid(uid)
        assertTrue(isValid)
    }
    @Test
    fun testWithStoredKeys() {
        val pubHex = "3059301306072A8648CE3D020106082A8648CE3D03010703420004A802CDC3DE42CFA61970BD84B80D68506F67FB1AF6AEE4912BF13C38AE17F49E24AAD6EBBD4F43D8B7F4976229865CFC253FCFFD9B1D99383DCEAEAC6D3A4A36"
        val privHex = "308193020100301306072A8648CE3D020106082A8648CE3D03010704793077020101042060211B0892A704FC584854586F23079C3E9538882F383FE8DCFC4298737E52D8A00A06082A8648CE3D030107A14403420004A802CDC3DE42CFA61970BD84B80D68506F67FB1AF6AEE4912BF13C38AE17F49E24AAD6EBBD4F43D8B7F4976229865CFC253FCFFD9B1D99383DCEAEAC6D3A4A36"

        try {
            // 加载 Key
            val publicKey = NTagUtils.loadPublicKeyFromHex(pubHex)
            val privateKey = NTagUtils.loadPrivateKeyFromHex(privHex)

            // 模拟数据 (NTAG UID)
            val uid = NTagUtils.hexStringToByteArray("0464171A282290")

            // 签名
            val signature = NTagUtils.signData(privateKey, uid)
            val signatureHex = NTagUtils.bytesToHex(signature)
            println("Signature: $signatureHex")

            // 验证
            val isValid = NTagUtils.verifySignature(publicKey, uid, signature)
            assertTrue(isValid)
            println("Load and Verify Result: $isValid")
        } catch (e: Exception) {
            e.printStackTrace()
            assertTrue(false)
        }
    }
}
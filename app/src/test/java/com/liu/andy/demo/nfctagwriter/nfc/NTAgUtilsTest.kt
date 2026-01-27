package com.liu.andy.demo.nfctagwriter.nfc

import org.junit.Assert.assertTrue
import org.junit.Test

class NTAgUtilsTest {

    @Test
    fun testGenerateKey0Pass() {
        val boxLicenseId = "f6cfb225-d69b-4fbb-9eda-624b0b20516e"
        val key0Pass = NTagUtils.generateMasterKey0Pass(boxLicenseId)
        assertTrue(key0Pass == "4EBC6FCF836695687EBFA56CCC049271")
    }

    @Test
    fun testGenerateDefaultPass() {
        val uid = "0464171A282290"
        val key0pass = NTagUtils.generateDefaultKeyPass(uid, 0)
        val key1pass = NTagUtils.generateDefaultKeyPass(uid, 1)
        val key2pass = NTagUtils.generateDefaultKeyPass(uid, 2)
        val key3pass = NTagUtils.generateDefaultKeyPass(uid, 3)
        val key4pass = NTagUtils.generateDefaultKeyPass(uid, 4)
        val key5pass = NTagUtils.generateDefaultKeyPass(uid, 5)
        print("$key0pass $key1pass $key2pass $key3pass $key4pass $key5pass")
        assertTrue(key0pass == "E84ED2DA7103AB27972F4FEB6D2A1959")
        assertTrue(key1pass == "EF683A400DB0AF9B8A0F4FDF2DE3DE71")
        assertTrue(key2pass== "A6806DC7BA49B5C0321FDB3A1A13B18B")
        assertTrue(key3pass == "CCE9AABF868371BCC02C9DF13D2C1CC0")
        assertTrue(key4pass == "D8483FEFF73493A431031114BAA96B22")
        assertTrue(key5pass == "7E48BDCB88E5CE84A5A00E765EA16837")
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
            val paddedSignData = NTagUtils.padSignatureTo72Bytes(signature)
            val paddedSignDataHex = NTagUtils.bytesToHex(paddedSignData)
            println("paddedSignData: $paddedSignDataHex")

            // 验证的时候先删除尾部补足的字节.
            val unpaddedSignedData = NTagUtils.unpadByAsn1Length(paddedSignData)
            val unpaddedSignedDataHex =  NTagUtils.bytesToHex(unpaddedSignedData)
            println("unpaddedSignedDataHex: $unpaddedSignedDataHex")

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
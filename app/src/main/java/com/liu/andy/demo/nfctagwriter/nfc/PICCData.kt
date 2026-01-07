package com.liu.andy.demo.nfctagwriter.nfc

import net.bplearning.ntag424.CMAC
import net.bplearning.ntag424.aes.AESCMAC
import net.bplearning.ntag424.lrp.LRPCMAC
import net.bplearning.ntag424.lrp.LRPMultiCipher
import net.bplearning.ntag424.util.ByteUtil
import net.bplearning.ntag424.util.Crypto
import java.security.InvalidAlgorithmParameterException
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import javax.crypto.Cipher
import javax.crypto.NoSuchPaddingException
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec


open class PiccData {
    /** Returns the UID of the card as a byte array  */
    lateinit var uid: ByteArray

    /** Returns the card's read counter  */
    var readCounter: Int = 0
    var usesLrp: Boolean = false
    lateinit var macFileKey: ByteArray

    constructor(uid: ByteArray, readCounter: Int, usesLrp: Boolean) {
        this.uid = uid
        this.readCounter = readCounter
        this.usesLrp = usesLrp
    }

    protected constructor()

    protected fun generateLRPSessionKey(macKey: ByteArray): ByteArray {
        // Pg. 42
        val multiCipher = LRPMultiCipher(macKey)
        val cipher = multiCipher.generateCipher(0)
        val sv = generateLRPSessionVector()
        return cipher.cmac(sv)
    }

    protected fun generateAESSessionEncKey(macKey: ByteArray): ByteArray? {
        // pg. 41
        val sv = generateAESEncSessionVector()
        return Crypto.simpleAesCmac(macKey, sv)
    }

    protected fun generateAESSessionMacKey(macKey: ByteArray): ByteArray? {
        // pg. 41
        val sv = generateAESMACSessionVector()
        return Crypto.simpleAesCmac(macKey, sv)
    }

    protected fun generateAESMACSessionVector(): ByteArray {
        return generateSessionVector(
            byteArrayOf(
                0x3c,
                0xc3.toByte(),
                0x00,
                0x01,
                0x00,
                0x80.toByte()
            ), null
        )
    }

    protected fun generateAESEncSessionVector(): ByteArray {
        return generateSessionVector(
            byteArrayOf(
                0xc3.toByte(),
                0x3c,
                0x00,
                0x01,
                0x00,
                0x80.toByte()
            ), null
        )
    }

    protected fun generateSessionVector(prefix: ByteArray, suffix: ByteArray?): ByteArray {
        // pg. 42
        val sv = ByteArray(16)
        System.arraycopy(prefix, 0, sv, 0, prefix.size)
        var svIdx = prefix.size
        if (uid != null) {
            if (!ByteUtil.arraysEqual(uid, byteArrayOf(0, 0, 0, 0, 0, 0, 0))) {
                System.arraycopy(uid, 0, sv, svIdx, uid!!.size)
                svIdx += uid!!.size
            }
        }

        if (readCounter > 0) {
            val readCounterBytes = byteArrayOf(
                ByteUtil.getByteLSB(readCounter.toLong(), 0),
                ByteUtil.getByteLSB(readCounter.toLong(), 1),
                ByteUtil.getByteLSB(readCounter.toLong(), 2)
            )
            System.arraycopy(readCounterBytes, 0, sv, svIdx, readCounterBytes.size)
            svIdx += readCounterBytes.size
        }
        if (suffix != null) {
            System.arraycopy(suffix, 0, sv, sv.size - suffix.size, suffix.size)
        }
        return sv
    }

    protected fun generateLRPSessionVector(): ByteArray {
        return generateSessionVector(
            byteArrayOf(
                0x00,
                0x01,
                0x00,
                0x80.toByte()
            ), byteArrayOf(
                0x1e,
                0xe1.toByte()
            )
        )
    }

    protected fun generateLRPCMAC(key: ByteArray): CMAC {
        val multiCipher = LRPMultiCipher(generateLRPSessionKey(key))
        val cipher = multiCipher.generateCipher(0)
        return LRPCMAC(cipher)
    }

    protected fun generateAESCMAC(key: ByteArray): CMAC? {
        try {
            val keySpec = SecretKeySpec(generateAESSessionMacKey(key), "AES")

            val cipher = Cipher.getInstance("AES/CBC/NoPadding")

            cipher.init(
                Cipher.ENCRYPT_MODE,
                keySpec,
                net.bplearning.ntag424.constants.Crypto.zeroIVPS
            )
            val mac = AESCMAC(cipher, keySpec)
            return mac
        } catch (e: NoSuchPaddingException) {
            e.printStackTrace()
            return null
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
            return null
        } catch (e: InvalidKeyException) {
            e.printStackTrace()
            return null
        } catch (e: InvalidAlgorithmParameterException) {
            e.printStackTrace()
            return null
        }
    }

    fun performCMAC(message: ByteArray?): ByteArray? {
        val cmac = if (usesLrp) generateLRPCMAC(macFileKey) else generateAESCMAC(macFileKey)
        val result = cmac!!.perform(message, net.bplearning.ntag424.constants.Crypto.CMAC_SIZE)
        return result
    }

    /** Performs the CMAC algorithm and shortens the response.  */
    fun performShortCMAC(message: ByteArray?): ByteArray? {
        return Crypto.shortenCMAC(performCMAC(message))
    }

    /** Sets the encryption key used for both file encryption and MAC calculation  */
//    fun setMacFileKey(key: ByteArray) {
//        macFileKey = key
//    }

    fun decryptFileData(encryptedData: ByteArray?): ByteArray? {
        if (usesLrp) {
            // Counter transformation is defined on pg. 39
            val counterBytes = byteArrayOf(
                ByteUtil.getByteLSB(readCounter.toLong(), 0),
                ByteUtil.getByteLSB(readCounter.toLong(), 1),
                ByteUtil.getByteLSB(readCounter.toLong(), 2),
                0, 0, 0
            )

            val sessionKey = generateLRPSessionKey(macFileKey)
            return Crypto.simpleLrpDecrypt(sessionKey, 1, counterBytes, encryptedData)
        } else {
            val sessionKey = generateAESSessionEncKey(macFileKey)
            val ivInput = ByteArray(16)
            ivInput[0] = ByteUtil.getByteLSB(readCounter.toLong(), 0)
            ivInput[1] = ByteUtil.getByteLSB(readCounter.toLong(), 1)
            ivInput[2] = ByteUtil.getByteLSB(readCounter.toLong(), 2)
            val ivBytes = Crypto.simpleAesEncrypt(sessionKey, ivInput)
            val ivps = IvParameterSpec(ivBytes)
            return Crypto.simpleAesDecrypt(sessionKey, encryptedData, ivps)
        }
    }

    val uidString: String?
        /** Returns the UID as a String.  This is also helpful if you want to be sure you have a normalized version of the UID.  */
        get() = ByteUtil.byteToHex(uid)

    companion object {
        fun decodeFromBytes(piccRecord: ByteArray, usesLrp: Boolean): PiccData {
            val pdata = PiccData()

            val tag = piccRecord[0]
            var curIdx = 1
            if ((tag.toInt() and 128) == 0) {
                // No UID Mirroring
            } else {
                pdata.uid = ByteArray(7)
                System.arraycopy(piccRecord, 1, pdata.uid, 0, 7)
                curIdx += 7
            }

            if ((tag.toInt() and 64) == 0) {
                // No tag counter
            } else {
                pdata.readCounter =
                    ByteUtil.lsbBytesToInt(ByteUtil.subArrayOf(piccRecord, curIdx, 3))
            }

            pdata.usesLrp = usesLrp

            return pdata
        }

        /**
         * NOTE - Uses a non-diversified key
         * @param encryptedData
         * @param key
         * @param usesLrp
         * @return
         */
        fun decodeFromEncryptedBytes(
            encryptedData: ByteArray?,
            key: ByteArray?,
            usesLrp: Boolean
        ): PiccData {
            val alldata: ByteArray
            if (usesLrp) {
                // LRP encodes the LRP counter in the first 8 bytes
                alldata = Crypto.simpleLrpDecrypt(
                    key, 0,
                    ByteUtil.subArrayOf(encryptedData, 0, 8),
                    ByteUtil.subArrayOf(encryptedData, 8, 16)
                )
            } else {
                alldata = Crypto.simpleAesDecrypt(key, encryptedData)
            }

            return decodeFromBytes(alldata, usesLrp)
        }

        /**
         * This is an all-in-one function for the most common
         * case - having a UID, COUNTER, and MAC string (likely
         * from URL parameters) and decoding it into a PiccData
         * object.  This returns null if the MAC does not verify.
         */
        fun decodeAndVerifyMac(
            uidString: String,
            readCounterString: String,
            macString: String,
            macFileKey: ByteArray,
            usesLrp: Boolean
        ): PiccData? {
            val piccData = PiccData(
                ByteUtil.hexToByte(uidString),
                ByteUtil.msbBytesToLong(ByteUtil.hexToByte(readCounterString)).toInt(),
                usesLrp
            )
            piccData.macFileKey = macFileKey
            val expectedMac = piccData.performShortCMAC(null)
            val actualMac = ByteUtil.hexToByte(macString)
            return if (ByteUtil.arraysEqual(expectedMac, actualMac)) {
                piccData
            } else {
                null
            }
        }
    }
}

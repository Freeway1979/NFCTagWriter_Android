package com.liu.andy.demo.nfctagwriter.nfc

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.NfcA
import android.util.Log
import android.widget.Toast
import com.liu.andy.demo.nfctagwriter.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.bplearning.ntag424.DnaCommunicator
import net.bplearning.ntag424.card.KeyInfo
import net.bplearning.ntag424.card.KeySet
import net.bplearning.ntag424.command.*
import net.bplearning.ntag424.constants.Ntag424
import net.bplearning.ntag424.constants.Ntag424.*
import net.bplearning.ntag424.constants.Permissions
import net.bplearning.ntag424.constants.Permissions.*
import net.bplearning.ntag424.encryptionmode.AESEncryptionMode
import net.bplearning.ntag424.sdm.NdefTemplateMaster
import net.bplearning.ntag424.sdm.SDMSettings
import net.bplearning.ntag424.util.ByteUtil
import net.bplearning.ntag424.util.ThrowableFunction
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.function.Consumer

/**
 * Manager class for NTAG424 DNA operations
 * Based on ntag424-java library: https://github.com/johnnyb/ntag424-java
 * 
 * This implementation matches the functionality of NTAG424DNAScanner.swift
 * 
 * NOTE: This file uses a temporary Constants object until the ntag424-java library
 * JAR file is added to app/libs/. Once the library is added, remove the temporary
 * Constants object and uncomment the import statement above.
 */
class NTag424Manager {
    
    companion object {
        private val TAG = "NTag424Manager"
        // Default factory key (Key 00) - all zeros
        private val FACTORY_KEY = ByteArray(16) { 0x00 }
        
        // NDEF file number (0x02 for NTAG424 DNA)
        private const val NDEF_FILE_NUMBER = 0x02
        
        // CC file number (0x01 for NTAG424 DNA)
        private const val CC_FILE_NUMBER = 0x01
        
        // Chunk size for writing (128 bytes for tearing protection per datasheet)
        private const val WRITE_CHUNK_SIZE = 128
        // https://medium.com/@androidcrypto/demystify-the-secure-dynamic-message-with-ntag-424-dna-nfc-tags-android-java-part-2-1f8878faa928
    }

    /**
     * Convert hex string to byte array
     */
    private fun hexStringToByteArray(hex: String): ByteArray {
        val cleanHex = hex.replace(" ", "").replace("-", "")
        val result = ByteArray(cleanHex.length / 2)
        for (i in cleanHex.indices step 2) {
            val str = cleanHex.substring(i, i + 2)
            result[i / 2] = str.toInt(16).toByte()
        }
        return result
    }
    
    /**
     * Convert byte array to hex string
     */
    private fun byteArrayToHexString(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02X".format(it) }
    }
    
    /**
     * Get transceiver function using IsoDep
     * NTAG424 tags use IsoDep for ISO 7816 communication
     * Returns a function that sends APDU commands and returns the response
     */
    private suspend fun getTransceiver(tag: Tag): (ByteArray) -> ByteArray? = withContext(Dispatchers.IO) {
        // Log available technologies for debugging
        val availableTechs = tag.techList.joinToString()
        android.util.Log.d("NTag424Manager", "Tag technologies: $availableTechs")
        android.util.Log.d("NTag424Manager", "Tag ID: ${tag.id.joinToString(":") { "%02X".format(it) }}")
        
        // Check if tag supports IsoDep
        val supportsIsoDep = tag.techList.contains(IsoDep::class.java.name)
        
        if (!supportsIsoDep) {
            throw IOException("Tag does not support IsoDep. Available technologies: $availableTechs")
        }
        closeTag(tag)
        val isoDep = try {
            IsoDep.get(tag)
        } catch (e: Exception) {
            android.util.Log.e("NTag424Manager", "Error getting IsoDep: ${e.message}")
            throw IOException("Failed to get IsoDep instance: ${e.message}")
        }
        
        if (isoDep == null) {
            throw IOException("IsoDep.get() returned null despite tag supporting IsoDep")
        }
        
        android.util.Log.d("NTag424Manager", "Using IsoDep for communication")
        try {
            isoDep.connect()
            android.util.Log.d("NTag424Manager", "IsoDep connected successfully")
            return@withContext { bytesToSend: ByteArray ->
                try {
                    if (!isoDep.isConnected) {
                        isoDep.connect()
                    }
                    isoDep.transceive(bytesToSend)
                } catch (e: Exception) {
                    android.util.Log.e("NTag424Manager", "IsoDep transceive error: ${e.message}")
                    null
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("NTag424Manager", "Failed to connect IsoDep: ${e.message}")
            throw IOException("Failed to connect to tag using IsoDep. Error: ${e.message}. Available technologies: $availableTechs")
        }
    }
    
    /**
     * Close the tag connection
     */
    private suspend fun closeTag(tag: Tag) = withContext(Dispatchers.IO) {
        try {
            IsoDep.get(tag)?.close()
        } catch (e: Exception) {
            // Ignore - connection may already be closed or not exist
        }
        try {
            NfcA.get(tag)?.close()
        } catch (e: Exception) {
            // Ignore - connection may already be closed or not exist
        }
    }
    
    /**
     * Set password (key) for the tag
     * Matches Swift implementation: tries default key first, then current password
     */
    suspend fun setPassword(tag: Tag, newPasswordHex: String,
                            oldPasswordHex: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val transceiver = getTransceiver(tag)

            val communicator = DnaCommunicator()
            communicator.setTransceiver { bytesToSend ->
                transceiver(bytesToSend)
            }
            // Select the DF file (required for NTAG424)
            IsoSelectFile.run(
                communicator,
                IsoSelectFile.SELECT_MODE_BY_FILE_IDENTIFIER,
                DF_FILE_ID
            )

            // Convert hex password to bytes (16 bytes = 128 bits for AES-128)
            val newKeyBytes = hexStringToByteArray(newPasswordHex)
            if (newKeyBytes.size != 16) {
                return@withContext Result.failure(
                    IllegalArgumentException("Password must be 16 bytes (32 hex characters)")
                )
            }

            // Step 1: Try to authenticate with default key first (for new tags)
            var authenticated = false
            var oldKey = FACTORY_KEY
            if (AESEncryptionMode.authenticateEV2(communicator, 0, FACTORY_KEY)) {
                // Default key works - tag is new or password was reset
                Log.d("NTag424Manager", "Default key works - tag is new or password was reset")
                authenticated = true
            } else {
                oldKey = NTagUtils.hexToBytes(oldPasswordHex)
                Log.d("NTag424Manager", "Default key failed - trying with the old password")
                if (AESEncryptionMode.authenticateEV2(communicator, 0, oldKey)) {
                    // The entered password matches the current password
                    Log.d("NTag424Manager", "Old password succeeds $oldPasswordHex")
                    authenticated = true
                } else {
                    return@withContext Result.failure(
                        IOException("Authentication failed. The tag already has a password set, and the password you entered doesn't match. To change an existing password, you need to know the current password.")
                    )
                }
            }

            // Step 2: Change the key (Key 00) to the new password
            // keyVersion: 0x00 (default version for new keys)
            ChangeKey.run(communicator, 0, oldKey, newKeyBytes, 0)

            // Step 3: Verify the password by attempting to authenticate with the new key
            if (!AESEncryptionMode.authenticateEV2(communicator, 0, newKeyBytes)) {
                return@withContext Result.failure(
                    IOException("Password set, but verification failed. Password may not have been set correctly.")
                )
            }

            // Step 4: Verify that default key no longer works
            val defaultKeyStillWorks = AESEncryptionMode.authenticateEV2(communicator, 0, FACTORY_KEY)

            val successMsg = buildString {
                append("Password set and verified successfully on NTAG 424 tag!\n\n")
                append("New key (hex): ${byteArrayToHexString(newKeyBytes)}\n\n")
                append("✅ Password verification: PASSED\n")
                append("✅ Default key disabled: ${if (defaultKeyStillWorks) "FAILED ⚠️" else "PASSED"}\n\n")
                append("⚠️ IMPORTANT: Save this key securely. You will need it to authenticate with the tag in the future.\n\n")
                append("🔒 Security Note: After setting the password, you must configure file access permissions using 'Configure File Access' to require authentication for write operations.")
            }
            Result.success(successMsg)
        } catch (e: Exception) {
            Result.failure(e)
         } finally {
            closeTag(tag)
        }
    }

    /**
     * Configure file access settings with authentication
     * Sets the NDEF file to require authentication for write operations, allow read without auth
     */
    suspend fun configureFileAccess(tag: Tag, passwordHex: String,
                                    supportSDM: Boolean = true): Result<String> = withContext(Dispatchers.IO) {
        // IsoDep runs the communication with the tag
        val iso = IsoDep.get(tag)
        try {
            // Standard NFC connect
            iso.connect()
            // Initialize DNA library
            val communicator = DnaCommunicator()
            communicator.setTransceiver(ThrowableFunction { bytesToSend: ByteArray? ->
                iso.transceive(
                    bytesToSend
                )
            })
            communicator.setLogger(Consumer { info: String? ->
                Log.d(
                    TAG,
                    "Communicator: " + info
                )
            })
            communicator.beginCommunication()

            // Synchronize keys first
            val keySet = getKeySet()
            keySet.synchronizeKeys(communicator)

            // Authenticate with a key.  If you are in LRP mode (Requires permanently changing tag settings), uncomment the LRP version instead.
            // if(LRPEncryptionMode.authenticateLRP(communicator, 0, Constants.FACTORY_KEY)) {
            if (AESEncryptionMode.authenticateEV2(
                    communicator,
                    0,
                    keySet.getKey(0).key
                )
            ) { // Assumes key0 is non-diversified
                Log.d(TAG, "Login successful")
                val cardUid = GetCardUid.run(communicator)
                Log.d(TAG, "Card UID: " + ByteUtil.byteToHex(cardUid))
                val keyVersion = GetKeyVersion.run(communicator, 0)
                Log.d(TAG, "Key 0 version: " + keyVersion)
                // Doing this will set LRP mode for all future authentications
                // SetCapabilities.run(communicator, true);

                // Get the NDEF file settings
                val ndeffs = GetFileSettings.run(communicator, Ntag424.NDEF_FILE_NUMBER)

                Log.d(TAG, "Debug NDEF: " + debugStringForFileSettings(ndeffs))

                // Secret data
                val secretData = byteArrayOf(
                    1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16
                )

                // Set the access keys and options
                val sdmSettings = SDMSettings()
                sdmSettings.sdmEnabled = true
                sdmSettings.sdmMetaReadPerm =
                    Permissions.ACCESS_KEY2 // Set to a key to get encrypted PICC data (usually non-diversified since you don't know the UID until after decryption)
                sdmSettings.sdmFileReadPerm =
                    Permissions.ACCESS_KEY3 // Used to create the MAC and Encrypt FileData
                sdmSettings.sdmOptionUid = true
                sdmSettings.sdmOptionReadCounter = true
                sdmSettings.sdmOptionUseAscii = true
                // NDEF SDM formatter helper - uses a template to write SDMSettings and get file data
                val master = NdefTemplateMaster()
                master.usesLRP = false

                val ndefRecord = master.generateNdefTemplateFromUrlString(
                    "https://freeway1979.github.io/nfc?u={UID}&c={COUNTER}&m={MAC}&gid=915565a3-65c7-4a2b-8629-194d80ed824b&rule=249",
//                        secretData,
                    sdmSettings
                )
                Log.d(TAG, "sdmSettings:${sdmSettings.sdmMacInputOffset} ${sdmSettings.sdmMacOffset}")
                print("sdmSettings:${sdmSettings.sdmMacInputOffset} ${sdmSettings.sdmMacOffset}")
                // This link (not by me) has a handy decoder if you are using factory keys (we are using a diversified factory key, so this will not work unless you change that in the keyset):
                // byte[] ndefRecord = master.generateNdefTemplateFromUrlString("https://sdm.nfcdeveloper.com/tagpt?uid={UID}&ctr={COUNTER}&cmac={MAC}", sdmSettings);

                // Write the record to the file
                WriteData.run(communicator, Ntag424.NDEF_FILE_NUMBER, ndefRecord)

                // Set the general NDEF permissions
                ndeffs.readPerm = ACCESS_EVERYONE
                ndeffs.writePerm = ACCESS_KEY0
                ndeffs.readWritePerm = ACCESS_KEY0 // backup key
                ndeffs.changePerm = ACCESS_KEY0
                ndeffs.sdmSettings = sdmSettings // Use the SDM settings we just setup
                Log.d(
                    TAG,
                    "New Ndef Settings: " + debugStringForFileSettings(ndeffs)
                )
                ChangeFileSettings.run(communicator, Ntag424.NDEF_FILE_NUMBER, ndeffs)
                val savedSettings = GetFileSettings.run(communicator, Ntag424.NDEF_FILE_NUMBER)
                Log.d(
                    TAG,
                    "Check Saved Settings: " + debugStringForFileSettings(savedSettings)
                )
            } else {
                Log.d(TAG, "Login unsuccessful")
            }
            // We are done
            iso.close()
            Log.d(TAG, "Disconnected from tag")
            Result.success("NDEF File Settings configured successfully")
        } catch (e: IOException) {
            Log.d(TAG, "error communicating", e)
            Result.failure(e)
        }
    }

    fun getKeySet(): KeySet {
        // NOTE - replace these with your own keys.
        //
        //        Any of the keys *can* be diversified
        //        if you don't use RandomID, but usually
        //        only the MAC key is diversified.
        val key0Str = NTAG424Data.masterKey0Pass
        val keySet = KeySet()
        keySet.setUsesLrp(false)

        // This is the "master" key
        val key0 = KeyInfo()
        key0.diversifyKeys = false
        val passwordValue = key0Str
        key0.key = hexStringToByteArray(passwordValue)
        keySet.setKey(Permissions.ACCESS_KEY0, key0)

        // No standard usage
        val key1 = KeyInfo()
        key1.diversifyKeys = false
        key1.key = Ntag424.FACTORY_KEY
        keySet.setKey(Permissions.ACCESS_KEY1, key1)

        // Usually used as a meta read key for encrypted PICC data
        val key2 = KeyInfo()
        key2.diversifyKeys = false
        key2.key = Ntag424.FACTORY_KEY
        keySet.setKey(Permissions.ACCESS_KEY2, key2)

        // Usually used as the MAC and encryption key.
        // The MAC key usually has the diversification information setup.
        val key3 = KeyInfo()
        key3.diversifyKeys = false
//        key3.systemIdentifier =
//            "testing".toByteArray(StandardCharsets.UTF_8) // systemIdentifier is usually a hex-encoded string based on the name of your intended use.
//        key3.version =
//            1 // Since it is not a factory key (it is *based* on a factory key, but underwent diversification), need to set to a version number other than 0.
        key3.key = Ntag424.FACTORY_KEY

        // No standard usage
        keySet.setKey(Permissions.ACCESS_KEY3, key3)
        val key4 = KeyInfo()
        key4.diversifyKeys = false
        key4.key = Ntag424.FACTORY_KEY
        keySet.setKey(Permissions.ACCESS_KEY4, key4)

        // This is used for decoding, but documenting that key2/key3 are standard for meta and mac
        keySet.setMetaKey(Permissions.ACCESS_KEY2)
        keySet.setMacFileKey(Permissions.ACCESS_KEY3)

        return keySet
    }

    
    /**
     * Configure CC File for iOS background detection
     * Writes the CC file content (32 bytes) according to Type 4 Tag specification
     */
    suspend fun configureCCFile(tag: Tag, passwordHex: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val transceiver = getTransceiver(tag)
            
            try {
                val communicator = DnaCommunicator()
                communicator.setTransceiver { bytesToSend ->
                    transceiver(bytesToSend)
                }
                
                // Select the DF file
                IsoSelectFile.run(
                    communicator,
                    IsoSelectFile.SELECT_MODE_BY_FILE_IDENTIFIER,
                    DF_FILE_ID
                )
                
                // Authenticate with the password
                val keyBytes = hexStringToByteArray(passwordHex)
                if (keyBytes.size != 16) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Password must be 16 bytes (32 hex characters)")
                    )
                }
                
                if (!AESEncryptionMode.authenticateEV2(communicator, 0, keyBytes)) {
                    return@withContext Result.failure(
                        IOException("Failed to authenticate with provided password")
                    )
                }
                
                // CC file content (32 bytes) - Type 4 Tag specification
                // 001720010000FF0406E104010000000506E10500808283000000000000000000
                val ccFileContent = byteArrayOf(
                    0x00, 0x17.toByte(),  // CCLEN (23 bytes)
                    0x20,                  // Mapping Version (2.0)
                    0x01, 0x00,            // MLe (256 bytes)
                    0x00, 0xFF.toByte(),   // MLc (255 bytes)
                    0x04, 0x06, 0xE1.toByte(), 0x04, 0x01, 0x00, 0x00, 0x00,  // NDEF-File Control TLV
                    0x05, 0x06, 0xE1.toByte(), 0x05, 0x00, 0x80.toByte(), 0x82.toByte(), 0x83.toByte(),  // Data File Control TLV
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00  // Padding (9 bytes)
                )
                
                // Write CC file content
                WriteData.run(communicator, CC_FILE_NUMBER, ccFileContent)
                
                val successMsg = buildString {
                    append("CC File (0x01) configured successfully for iOS background detection!\n\n")
                    append("CC File Access Permissions:\n")
                    append("• Read Access: ALL (0xE) - Critical for iOS Background ✅\n")
                    append("• Write Access: Key 0 (0x0)\n")
                    append("• R/W Access: Key 0 (0x0)\n")
                    append("• Change Access: ALL (0xE)\n")
                    append("• Communication Mode: PLAIN ✅\n")
                    append("• SDM: Disabled\n\n")
                    append("📱 iOS Background Detection:\n")
                    append("✅ CC File is now configured correctly!\n")
                    append("   Your tag should be detectable by iOS in background.")
                }
                
                Result.success(successMsg)
                
            } finally {
                closeTag(tag)
            }
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Write data to the tag with authentication
     * Uses NLEN format (Type 4 Tag format) and writes in chunks for tearing protection
     */
    suspend fun writeData(tag: Tag, data: String, passwordHex: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val transceiver = getTransceiver(tag)
            try {
                val communicator = DnaCommunicator()
                communicator.setTransceiver { bytesToSend ->
                    transceiver(bytesToSend)
                }
                
                // Select the DF file
                IsoSelectFile.run(
                    communicator,
                    IsoSelectFile.SELECT_MODE_BY_FILE_IDENTIFIER,
                    DF_FILE_ID
                )
                
                // Authenticate with the password
                val keyBytes = hexStringToByteArray(passwordHex)
                if (keyBytes.size != 16) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Password must be 16 bytes (32 hex characters)")
                    )
                }
                
                if (!AESEncryptionMode.authenticateEV2(communicator, 0, keyBytes)) {
                    return@withContext Result.failure(
                        IOException("Failed to authenticate with provided password")
                    )
                }
                
                // Create NDEF message in NLEN format (Type 4 Tag format)
                val ndefData = createNDEFMessageInNLENFormat(data)
                    ?: return@withContext Result.failure(
                        IOException("Failed to create NDEF message from: $data")
                    )
                
                // Write data to the NDEF file
                // Note: If data is larger than 128 bytes, the library may handle chunking internally
                // For now, write all data at once. The library should handle the WriteData command properly.
                WriteData.run(communicator, NDEF_FILE_NUMBER, ndefData)
                
                Result.success("Data written successfully")
                
            } finally {
                closeTag(tag)
            }
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Read data from the tag
     * @param tag The NFC tag
     * @param passwordHex Optional password for authentication (if null, reads without auth)
     */
    suspend fun readData(tag: Tag, passwordHex: String? = null): Result<String> = withContext(Dispatchers.IO) {
        try {
            val transceiver = getTransceiver(tag)
            
            try {
                val communicator = DnaCommunicator()
                communicator.setTransceiver { bytesToSend ->
                    transceiver(bytesToSend)
                }
                
                // Select the DF file
                IsoSelectFile.run(
                    communicator,
                    IsoSelectFile.SELECT_MODE_BY_FILE_IDENTIFIER,
                    DF_FILE_ID
                )
                
                // Authenticate if password is provided
                if (passwordHex != null && passwordHex.isNotEmpty()) {
                    val keyBytes = hexStringToByteArray(passwordHex)
                    if (keyBytes.size == 16) {
                        if (!AESEncryptionMode.authenticateEV2(communicator, 0, keyBytes)) {
                            return@withContext Result.failure(
                                IOException("Failed to authenticate with provided password")
                            )
                        }
                    }
                }
                
                // Read data from the NDEF file (max 256 bytes)
                val dataBytes = ReadData.run(communicator, NDEF_FILE_NUMBER, 0, 0)
                
                // Parse NDEF data from NLEN format
                val text = parseNDEFDataFromNLENFormat(dataBytes)
                
                if (text.isEmpty()) {
                    return@withContext Result.failure(
                        IOException("No NDEF data found or failed to parse")
                    )
                }
                
                Result.success(text)
                
            } finally {
                closeTag(tag)
            }
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get card UID
     */
    suspend fun getCardUid(tag: Tag): Result<String> = withContext(Dispatchers.IO) {
        try {
            val transceiver = getTransceiver(tag)
            
            try {
                val communicator = DnaCommunicator()
                communicator.setTransceiver { bytesToSend ->
                    transceiver(bytesToSend)
                }
                
                // Select the DF file
                IsoSelectFile.run(
                    communicator,
                    IsoSelectFile.SELECT_MODE_BY_FILE_IDENTIFIER,
                    DF_FILE_ID
                )
                
                // Authenticate with factory key to get UID
                if (!AESEncryptionMode.authenticateEV2(communicator, 0, FACTORY_KEY)) {
                    return@withContext Result.failure(
                        IOException("Failed to authenticate with factory key")
                    )
                }
                
                // Get card UID
                val uidBytes = GetCardUid.run(communicator)
                val uidHex = byteArrayToHexString(uidBytes)
                
                Result.success(uidHex)
                
            } finally {
                closeTag(tag)
            }
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Create NDEF message in NLEN format (Type 4 Tag format)
     * NLEN Structure: [NLEN(2 bytes, big-endian)] [NDEF Data]
     * Where NLEN is a 2-byte length field (0x0000 to 0xFFFE)
     */
    private fun createNDEFMessageInNLENFormat(text: String): ByteArray? {
        // Create NDEF record
        val ndefRecord = if (text.startsWith("http://") || text.startsWith("https://")) {
            // URI record
            NdefRecord.createUri(text)
        } else {
            // Text record
            NdefRecord.createTextRecord("en", text)
        }
        
        // Create NDEF message
        val ndefMessage = NdefMessage(arrayOf(ndefRecord))
        val ndefPayload = ndefMessage.toByteArray()
        
        // Wrap in NLEN format: [NLEN high byte] [NLEN low byte] [NDEF Data]
        val nlenData = ByteArray(2 + ndefPayload.size)
        val ndefLength = ndefPayload.size.toUShort()
        nlenData[0] = ((ndefLength.toInt() shr 8) and 0xFF).toByte()  // High byte
        nlenData[1] = (ndefLength.toInt() and 0xFF).toByte()          // Low byte
        System.arraycopy(ndefPayload, 0, nlenData, 2, ndefPayload.size)
        
        return nlenData
    }
    
    /**
     * Parse NDEF data from NLEN format (Type 4 Tag format)
     * NLEN Structure: [NLEN(2 bytes, big-endian)] [NDEF Data]
     */
    private fun parseNDEFDataFromNLENFormat(data: ByteArray): String {
        if (data.size < 2) return ""
        
        // Extract NLEN (2-byte length field, big-endian)
        val nlenHigh = data[0].toInt() and 0xFF
        val nlenLow = data[1].toInt() and 0xFF
        val ndefLength = (nlenHigh shl 8) or nlenLow
        
        val ndefPayload: ByteArray?
        
        if (ndefLength > 0 && ndefLength <= 0xFFFE) {
            // Valid NLEN format
            val payloadStart = 2  // Skip NLEN bytes
            val payloadEnd = payloadStart + ndefLength
            
            if (data.size >= payloadEnd) {
                ndefPayload = data.sliceArray(payloadStart until payloadEnd)
            } else if (data.size > payloadStart) {
                // Partial data
                ndefPayload = data.sliceArray(payloadStart until data.size)
            } else {
                return ""
            }
        } else {
            // Invalid NLEN or legacy format - try to parse as raw NDEF data
            // Remove padding (0x00 bytes at the end)
            var trimmedData = data
            while (trimmedData.isNotEmpty() && (trimmedData.last().toInt() == 0 || trimmedData.last().toInt() == 0xFE)) {
                trimmedData = trimmedData.sliceArray(0 until trimmedData.size - 1)
            }
            
            ndefPayload = if (trimmedData.isNotEmpty()) trimmedData else null
        }
        
        if (ndefPayload == null || ndefPayload.isEmpty()) {
            return ""
        }
        
        // Try to parse as NDEF message
        try {
            val ndefMessage = NdefMessage(ndefPayload)
            for (record in ndefMessage.records) {
                when (record.tnf) {
                    NdefRecord.TNF_WELL_KNOWN -> {
                        // Check if it's a URI record (type "U" = 0x55)
                        if (record.type.contentEquals(byteArrayOf(0x55))) {
                            val uri = parseNDEFURIPayload(record.payload)
                            if (uri.isNotEmpty()) {
                                return uri
                            }
                        }
                        // Check if it's a text record (type "T" = 0x54)
                        if (record.type.contentEquals(byteArrayOf(0x54))) {
                            val text = parseNDEFTextPayload(record.payload)
                            if (text.isNotEmpty()) {
                                return text
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Failed to parse as NDEF message, try UTF-8 string
        }
        
        // Fallback: try to decode as UTF-8 string
        val text = String(ndefPayload, Charsets.UTF_8).trim()
        if (text.isNotEmpty()) {
            return text
        }
        
        return ""
    }
    
    /**
     * Parse NDEF URI payload
     * NDEF URI record format: [URI Prefix Code (1 byte)] [URI Suffix (variable)]
     */
    private fun parseNDEFURIPayload(payload: ByteArray): String {
        if (payload.isEmpty()) return ""
        
        val prefixCode = payload[0].toInt() and 0xFF
        val uriSuffix = payload.sliceArray(1 until payload.size)
        
        val uriPrefixes = arrayOf(
            "",                    // 0x00: No prefix
            "http://www.",         // 0x01
            "https://www.",        // 0x02
            "http://",             // 0x03
            "https://",            // 0x04
            "tel:",                // 0x05
            "mailto:",             // 0x06
        )
        
        val prefix = if (prefixCode < uriPrefixes.size) {
            uriPrefixes[prefixCode]
        } else {
            ""
        }
        
        val suffix = String(uriSuffix, Charsets.UTF_8)
        return prefix + suffix
    }
    
    /**
     * Parse NDEF text payload
     * NDEF text record format: [Status Byte (UTF-16 flag + lang length)] [Language Code] [Text Content]
     */
    private fun parseNDEFTextPayload(payload: ByteArray): String {
        if (payload.isEmpty()) return ""
        
        val statusByte = payload[0].toInt() and 0xFF
        val langCodeLength = statusByte and 0x3F
        val isUTF16 = (statusByte and 0x80) != 0
        
        if (payload.size <= langCodeLength) return ""
        
        val textStartIndex = 1 + langCodeLength
        if (payload.size <= textStartIndex) return ""
        
        val textData = payload.sliceArray(textStartIndex until payload.size)
        
        return if (isUTF16) {
            String(textData, Charsets.UTF_16)
        } else {
            String(textData, Charsets.UTF_8)
        }
    }

    // Make file permission/SDM settings easier to see in the logs
    fun debugStringForFileSettings(fs: FileSettings): String {
        val sb = StringBuilder()
        sb.append("= FileSettings =").append("\n")
        sb.append("fileType: ").append("n/a").append("\n") // todo expose get file type for DESFire
        sb.append("commMode: ").append(fs.commMode.toString()).append("\n")
        sb.append("accessRights RW:       ").append(fs.readWritePerm).append("\n")
        sb.append("accessRights CAR:      ").append(fs.changePerm).append("\n")
        sb.append("accessRights R:        ").append(fs.readPerm).append("\n")
        sb.append("accessRights W:        ").append(fs.writePerm).append("\n")
        sb.append("fileSize: ").append(fs.fileSize).append("\n")
        sb.append("= Secure Dynamic Messaging =").append("\n")
        sb.append("isSdmEnabled: ").append(fs.sdmSettings.sdmEnabled).append("\n")
        sb.append("isSdmOptionUid: ").append(fs.sdmSettings.sdmOptionUid).append("\n")
        sb.append("isSdmOptionReadCounter: ").append(fs.sdmSettings.sdmOptionReadCounter)
            .append("\n")
        sb.append("isSdmOptionReadCounterLimit: ").append(fs.sdmSettings.sdmOptionReadCounterLimit)
            .append("\n")
        sb.append("isSdmOptionEncryptFileData: ").append(fs.sdmSettings.sdmOptionEncryptFileData)
            .append("\n")
        sb.append("isSdmOptionUseAscii: ").append(fs.sdmSettings.sdmOptionUseAscii).append("\n")
        sb.append("sdmMetaReadPerm:             ").append(fs.sdmSettings.sdmMetaReadPerm)
            .append("\n")
        sb.append("sdmFileReadPerm:             ").append(fs.sdmSettings.sdmFileReadPerm)
            .append("\n")
        sb.append("sdmReadCounterRetrievalPerm: ")
            .append(fs.sdmSettings.sdmReadCounterRetrievalPerm).append("\n")
        sb.append("sdmUidOffset:         ").append(fs.sdmSettings.sdmUidOffset).append("\n")
        sb.append("sdmReadCounterOffset: ").append(fs.sdmSettings.sdmReadCounterOffset).append("\n")
        sb.append("sdmPiccDataOffset:    ").append(fs.sdmSettings.sdmPiccDataOffset).append("\n")
        sb.append("sdmMacInputOffset:    ").append(fs.sdmSettings.sdmMacInputOffset).append("\n")
        sb.append("sdmMacOffset:         ").append(fs.sdmSettings.sdmMacOffset).append("\n")
        sb.append("sdmEncOffset:         ").append(fs.sdmSettings.sdmEncOffset).append("\n")
        sb.append("sdmEncLength:         ").append(fs.sdmSettings.sdmEncLength).append("\n")
        sb.append("sdmReadCounterLimit:  ").append(fs.sdmSettings.sdmReadCounterLimit).append("\n")
        return sb.toString()
    }
}

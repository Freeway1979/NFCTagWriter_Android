package com.liu.andy.demo.nfctagwriter.nfc

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.tech.NfcA
import android.nfc.tech.Ndef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.experimental.or

/**
 * Tag Information Structure
 */
data class NFCTagInfo(
    var serialNumber: String = "",
    var memorySize: String = "",
    var tagType: String = "",
    var ndefMessageSize: String = "",
    var isPasswordProtected: Boolean = false,
    var details: String = ""
)

/**
 * NTAG21X Password Protection Pages Structure
 * Handles different tag types (NTAG213/215/216) with their specific page locations
 */
data class NTAG21XPasswordPages(
    val tagType: String,
    val auth0Page: Int,
    val accessPage: Int,
    val pwdPage: Int,
    val packPage: Int,
    val totalMemoryBytes: Int,
    val totalMemoryPages: Int
) {
    companion object {
        const val PWD_AUTH: Byte = 0x1B
        
        /**
         * Convert NDEF memory size to actual total tag memory size
         * NDEF size is what's reported in CC, but actual tag has more memory
         */
        fun totalMemorySize(ndefBytes: Int): Int {
            return when (ndefBytes) {
                144 -> 180  // NTAG213: 180 bytes (45 pages)
                496 -> 504  // NTAG215: 504 bytes (126 pages)
                872 -> 888  // NTAG216: 888 bytes (222 pages)
                else -> ndefBytes  // Fallback: assume NDEF size = total size
            }
        }
        
        /**
         * Factory method to get pages based on NDEF memory size (from CC)
         */
        fun pages(ndefBytes: Int): NTAG21XPasswordPages? {
            val totalBytes = totalMemorySize(ndefBytes)
            val totalPages = totalBytes / 4
            
            return when (ndefBytes) {
                144 -> {  // NTAG213 NDEF memory size
                    NTAG21XPasswordPages(
                        tagType = "NTAG213",
                        auth0Page = 0x29,  // Page 41
                        accessPage = 0x2A, // Page 42
                        pwdPage = 0x2B,    // Page 43
                        packPage = 0x2C,   // Page 44
                        totalMemoryBytes = totalBytes,
                        totalMemoryPages = totalPages
                    )
                }
                496 -> {  // NTAG215 NDEF memory size
                    NTAG21XPasswordPages(
                        tagType = "NTAG215",
                        auth0Page = 0x83,  // Page 131
                        accessPage = 0x84, // Page 132
                        pwdPage = 0x85,     // Page 133
                        packPage = 0x86,    // Page 134
                        totalMemoryBytes = totalBytes,
                        totalMemoryPages = totalPages
                    )
                }
                872 -> {  // NTAG216 NDEF memory size
                    NTAG21XPasswordPages(
                        tagType = "NTAG216",
                        auth0Page = 0xE3,  // Page 227
                        accessPage = 0xE4, // Page 228
                        pwdPage = 0xE5,     // Page 229
                        packPage = 0xE6,    // Page 230
                        totalMemoryBytes = totalBytes,
                        totalMemoryPages = totalPages
                    )
                }
                else -> null
            }
        }
    }
}

/**
 * Manager class for NTAG21X (NTAG213/215/216) operations
 * Based on NTAG21X datasheet specifications
 * 
 * This implementation matches the functionality of NFCScanner.swift
 */
class NTag21XManager {
    
    companion object {
        // Command codes
        private const val CMD_PWD_AUTH: Byte = 0x1B.toByte()
        private const val CMD_READ: Byte = 0x30.toByte()
        private const val CMD_WRITE: Byte = 0xA2.toByte()
        
        // Page 3 (Capability Container)
        private const val PAGE_CC = 0x03
    }
    
    /**
     * Convert password string to 4-byte array
     * Password can be 4 characters (converted to bytes) or 8 hex characters
     */
    private fun passwordToBytes(password: String): ByteArray {
        // Take first 4 characters and convert to bytes
        val passwordData = password.substring(0, minOf(4, password.length)).toByteArray(Charsets.UTF_8)
        return ByteArray(4) { if (it < passwordData.size) passwordData[it] else 0x00 }
    }
    
    /**
     * Authenticate with password
     * Returns true if authentication successful (response is 2-byte PACK)
     */
    private suspend fun authenticate(nfcA: NfcA, password: ByteArray): Boolean = withContext(Dispatchers.IO) {
        try {
            val authCmd = byteArrayOf(CMD_PWD_AUTH) + password
            val response = nfcA.transceive(authCmd)
            
            // Successful authentication returns 2-byte PACK
            // If response is 2 bytes and not all 0x00, authentication likely succeeded
            response != null && response.size == 2 && !response.contentEquals(byteArrayOf(0x00, 0x00))
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Read tag information and capabilities
     * Matches Swift readTagInfo function
     */
    suspend fun readTagInfo(tag: Tag): Result<NFCTagInfo> = withContext(Dispatchers.IO) {
        try {
            val nfcA = NfcA.get(tag) ?: return@withContext Result.failure(
                IOException("Tag does not support NfcA")
            )
            
            nfcA.connect()
            
            try {
                var tagInfo = NFCTagInfo()
                
                // Read pages 0-3 to get serial number and capability container
                // Page 0-2: Serial number (UID) - 7 bytes
                // Page 3: Capability Container (CC)
                val readCommand = byteArrayOf(CMD_READ, 0x00) // Read from page 0 (returns 4 pages)
                val response = nfcA.transceive(readCommand)
                
                if (response == null || response.size < 16) {
                    return@withContext Result.failure(
                        IOException("Invalid tag response")
                    )
                }
                
                // Extract serial number from pages 0-2 (first 7 bytes)
                val serialBytes = response.sliceArray(0 until 7)
                tagInfo.serialNumber = serialBytes.joinToString(":") { "%02X".format(it) }
                
                // Extract Capability Container from page 3 (bytes 12-15)
                val ccPage = response.sliceArray(12 until 16)
                
                // CC structure: [Magic (0xE1), Version, SIZE(MLEN), Access]
                if (ccPage[0].toInt() and 0xFF == 0xE1) {
                    val mlen = ccPage[2].toInt() and 0xFF
                    val ndefBytes = mlen * 8  // This is NDEF memory size, not total tag memory
                    
                    tagInfo.ndefMessageSize = "$ndefBytes bytes"
                    
                    // Get password protection pages based on NDEF memory size to determine correct tag type
                    val passwordPages = NTAG21XPasswordPages.pages(ndefBytes)
                    
                    if (passwordPages == null) {
                        // Tag doesn't support password protection - determine type from NDEF memory size
                        tagInfo.tagType = when (ndefBytes) {
                            144 -> "NTAG213"
                            496 -> "NTAG215"
                            872 -> "NTAG216"
                            else -> "NTAG (Unknown variant)"
                        }
                        
                        val totalBytes = NTAG21XPasswordPages.totalMemorySize(ndefBytes)
                        val totalPages = totalBytes / 4
                        tagInfo.memorySize = "$totalBytes bytes ($totalPages pages)"
                        
                        // Build details string
                        val details = buildString {
                            appendLine("Serial: ${tagInfo.serialNumber}")
                            appendLine("Type: ${tagInfo.tagType}")
                            appendLine("Total Memory: $totalBytes bytes ($totalPages pages)")
                            appendLine("NDEF Size: $ndefBytes bytes")
                            if (ccPage.size >= 4) {
                                appendLine("CC Version: 0x%02X".format(ccPage[1].toInt() and 0xFF))
                                appendLine("CC Access: 0x%02X".format(ccPage[3].toInt() and 0xFF))
                            }
                            appendLine("Password Protected: No (Tag does not support password protection)")
                        }
                        tagInfo.details = details
                        
                        return@withContext Result.success(tagInfo)
                    }
                    
                    // Use the correct tag type from passwordPages
                    tagInfo.tagType = passwordPages.tagType
                    tagInfo.memorySize = "${passwordPages.totalMemoryBytes} bytes (${passwordPages.totalMemoryPages} pages)"
                    
                    // Build details string
                    val details = buildString {
                        appendLine("Serial: ${tagInfo.serialNumber}")
                        appendLine("Type: ${tagInfo.tagType}")
                        appendLine("Total Memory: ${passwordPages.totalMemoryBytes} bytes (${passwordPages.totalMemoryPages} pages)")
                        appendLine("NDEF Size: $ndefBytes bytes")
                        if (ccPage.size >= 4) {
                            appendLine("CC Version: 0x%02X".format(ccPage[1].toInt() and 0xFF))
                            appendLine("CC Access: 0x%02X".format(ccPage[3].toInt() and 0xFF))
                        }
                        appendLine("")
                        appendLine("Password Protection Pages:")
                        appendLine("  AUTH0: Page 0x%02X (Page ${passwordPages.auth0Page})".format(passwordPages.auth0Page))
                        appendLine("  ACCESS: Page 0x%02X (Page ${passwordPages.accessPage})".format(passwordPages.accessPage))
                        appendLine("  PWD: Page 0x%02X (Page ${passwordPages.pwdPage})".format(passwordPages.pwdPage))
                        appendLine("  PACK: Page 0x%02X (Page ${passwordPages.packPage})".format(passwordPages.packPage))
                    }
                    
                    // Read password protection pages (read from AUTH0 page to get all 4 pages)
                    val readAuth0Command = byteArrayOf(CMD_READ, passwordPages.auth0Page.toByte())
                    val auth0Response = nfcA.transceive(readAuth0Command)
                    
                    if (auth0Response != null && auth0Response.size >= 16) {
                        // Response structure: Reading from AUTH0 page returns 4 pages (16 bytes)
                        // Bytes 0-3: AUTH0 page [AUTH0, ...]
                        // Bytes 4-7: ACCESS page [..., ACCESS, ...]
                        // Bytes 8-11: PWD page [PWD]
                        // Bytes 12-15: PACK page [PACK]
                        
                        val auth0 = auth0Response[0].toInt() and 0xFF
                        val accessByte = if (auth0Response.size > 6) {
                            auth0Response[6].toInt() and 0xFF
                        } else {
                            0x00
                        }
                        
                        // Password protection status:
                        // - AUTH0 != 0xFF means password protection is configured
                        // - ACCESS bit 7 (0x80) means password protection is actively enforced
                        val hasAuth0Configured = (auth0 != 0xFF)
                        val hasAccessEnabled = ((accessByte and 0x80) != 0)
                        
                        tagInfo.isPasswordProtected = hasAuth0Configured
                        
                        val detailsWithProtection = buildString {
                            append(details)
                            appendLine("")
                            appendLine("Password Protection Status:")
                            if (tagInfo.isPasswordProtected) {
                                if (hasAccessEnabled) {
                                    appendLine("  Status: Yes (Active)")
                                } else {
                                    appendLine("  Status: Yes (Configured but not enforced)")
                                }
                            } else {
                                appendLine("  Status: No")
                            }
                            appendLine("  AUTH0 value: 0x%02X".format(auth0))
                            appendLine("  ACCESS value: 0x%02X".format(accessByte))
                            if ((accessByte and 0x80) == 0) {
                                appendLine("  Note: ACCESS bit 7 not set - password not enforced")
                            }
                        }
                        tagInfo.details = detailsWithProtection
                    } else {
                        val detailsWithError = buildString {
                            append(details)
                            appendLine("")
                            appendLine("Password Protection Status:")
                            appendLine("  Status: Unknown (Could not read password pages)")
                        }
                        tagInfo.details = detailsWithError
                    }
                } else {
                    tagInfo.tagType = "Unknown (Not NDEF formatted)"
                    tagInfo.details = "Serial: ${tagInfo.serialNumber}\nTag is not NDEF formatted"
                }
                
                Result.success(tagInfo)
                
            } finally {
                nfcA.close()
            }
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Set password on NTAG tag and enable password protection
     * Matches Swift setPassword function with support for write-only vs read & write protection
     */
    suspend fun setPassword(
        tag: Tag,
        password: String,
        writeOnlyProtection: Boolean = true
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val nfcA = NfcA.get(tag) ?: return@withContext Result.failure(
                IOException("Tag does not support NfcA")
            )
            
            nfcA.connect()
            
            try {
                val passwordBytes = passwordToBytes(password)
                if (passwordBytes.size != 4) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Password must be 4 bytes")
                    )
                }
                
                // First, check the tag type by reading the Capability Container
                val readCCCommand = byteArrayOf(CMD_READ, 0x00) // Read page 0 (returns pages 0-3)
                val ccResponse = nfcA.transceive(readCCCommand)
                
                if (ccResponse == null || ccResponse.size < 16) {
                    return@withContext Result.failure(
                        IOException("Invalid tag response when reading capabilities")
                    )
                }
                
                // Extract Capability Container from page 3 (bytes 12-15)
                val ccPage = ccResponse.sliceArray(12 until 16)
                
                // Check if it's a valid NDEF tag
                if (ccPage[0].toInt() and 0xFF != 0xE1) {
                    return@withContext Result.failure(
                        IOException("Tag is not NDEF formatted")
                    )
                }
                
                // Calculate NDEF memory size (from CC)
                val mlen = ccPage[2].toInt() and 0xFF
                val ndefBytes = mlen * 8  // This is NDEF memory size, not total tag memory
                
                // Get password protection pages based on NDEF memory size
                val passwordPages = NTAG21XPasswordPages.pages(ndefBytes)
                    ?: return@withContext Result.failure(
                        IOException("Tag type not supported for password protection. NDEF memory: $ndefBytes bytes")
                    )
                
                // Verify pages are within tag's actual memory range
                if (passwordPages.totalMemoryPages <= passwordPages.auth0Page ||
                    passwordPages.totalMemoryPages <= passwordPages.pwdPage ||
                    passwordPages.totalMemoryPages <= passwordPages.accessPage
                ) {
                    return@withContext Result.failure(
                        IOException("Tag does not have enough pages for password protection")
                    )
                }
                
                // Step 1: Write password to PWD page
                val writePasswordCommand = byteArrayOf(
                    CMD_WRITE,
                    passwordPages.pwdPage.toByte(),
                    passwordBytes[0],
                    passwordBytes[1],
                    passwordBytes[2],
                    passwordBytes[3]
                )
                nfcA.transceive(writePasswordCommand)
                
                // Step 2: Set AUTH0 - specifies which page requires authentication
                // AUTH0 = 0x04 means page 4 and above require authentication
                val auth0PageData = byteArrayOf(
                    CMD_WRITE,
                    passwordPages.auth0Page.toByte(),
                    0x04, 0x00, 0x00, 0x00  // AUTH0=0x04, rest zeros
                )
                nfcA.transceive(auth0PageData)
                
                // Step 3: Read current ACCESS page to preserve existing values, then set ACCESS bit
                val readAccessCommand = byteArrayOf(CMD_READ, passwordPages.accessPage.toByte())
                val readAccessResponse = nfcA.transceive(readAccessCommand)
                
                if (readAccessResponse == null || readAccessResponse.size < 4) {
                    return@withContext Result.failure(
                        IOException("Invalid response when reading ACCESS page")
                    )
                }
                
                // Extract current ACCESS page values
                // ACCESS page structure: [PACK[0], PACK[1], ACCESS, RFUI]
                val currentPackLow = readAccessResponse[0]
                val currentPackHigh = readAccessResponse[1]
                val currentAccess = readAccessResponse[2]
                val currentRFUI = readAccessResponse[3]
                
                // Set ACCESS byte based on protection mode
                val newAccess: Byte = if (writeOnlyProtection) {
                    // Write Protected Only: Clear bit 7 (0x80) to allow read access without password
                    (currentAccess.toInt() and 0x7F).toByte()  // Clear bit 7, preserve other bits
                } else {
                    // Read & Write Protected: Set bit 7 (0x80) to require password for both read and write
                    (currentAccess.toInt() or 0x80).toByte()  // Set bit 7, preserve other bits
                }
                
                val accessPageData = byteArrayOf(
                    CMD_WRITE,
                    passwordPages.accessPage.toByte(),
                    currentPackLow,
                    currentPackHigh,
                    newAccess,
                    currentRFUI
                )
                nfcA.transceive(accessPageData)
                
                val protectionMode = if (writeOnlyProtection) {
                    "Write Protected Only (Read full access)"
                } else {
                    "Read & Write Protected"
                }
                val successMsg = "Password set successfully! Tag is now $protectionMode.\n\n" +
                        "IMPORTANT: Remove the tag from the RF field and re-present it for protection to take effect."
                
                Result.success(successMsg)
                
            } finally {
                nfcA.close()
            }
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Check if authentication is needed for read operations
     * If write-only protection is enabled (ACCESS bit 7 = 0), skip authentication
     */
    private suspend fun checkAuthenticationNeededForRead(
        nfcA: NfcA,
        passwordBytes: ByteArray,
        passwordPages: NTAG21XPasswordPages
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Read the ACCESS page to check if authentication is needed
            val readAccessCommand = byteArrayOf(CMD_READ, passwordPages.accessPage.toByte())
            val accessResponse = nfcA.transceive(readAccessCommand)
            
            if (accessResponse == null || accessResponse.size < 4) {
                // Can't read ACCESS, try authentication (fallback)
                return@withContext authenticate(nfcA, passwordBytes)
            }
            
            // ACCESS byte is at index 2 of the ACCESS page
            val accessByte = accessResponse[2].toInt() and 0xFF
            val isReadProtected = (accessByte and 0x80) != 0
            
            if (isReadProtected) {
                // ACCESS bit 7 is set - read/write protection is enabled, need authentication
                authenticate(nfcA, passwordBytes)
            } else {
                // ACCESS bit 7 is not set - write-only protection, skip authentication
                true
            }
        } catch (e: Exception) {
            // Error reading ACCESS, try authentication (fallback)
            authenticate(nfcA, passwordBytes)
        }
    }
    
    /**
     * Read data from the tag
     * Matches Swift readData function with smart authentication checking
     */
    suspend fun readData(tag: Tag, password: String? = null): Result<String> = withContext(Dispatchers.IO) {
        try {
            val nfcA = NfcA.get(tag) ?: return@withContext Result.failure(
                IOException("Tag does not support NfcA")
            )
            
            nfcA.connect()
            
            try {
                // If password is provided, check if authentication is needed
                if (password != null && password.isNotEmpty()) {
                    val passwordBytes = passwordToBytes(password)
                    
                    // First, read the Capability Container to determine tag type
                    val readCCCommand = byteArrayOf(CMD_READ, 0x00)
                    val ccResponse = nfcA.transceive(readCCCommand)
                    
                    if (ccResponse != null && ccResponse.size >= 16) {
                        val ccPage = ccResponse.sliceArray(12 until 16)
                        
                        if (ccPage[0].toInt() and 0xFF == 0xE1) {
                            val mlen = ccPage[2].toInt() and 0xFF
                            val ndefBytes = mlen * 8
                            
                            val passwordPages = NTAG21XPasswordPages.pages(ndefBytes)
                            if (passwordPages != null) {
                                // Check if authentication is needed
                                val authenticated = checkAuthenticationNeededForRead(
                                    nfcA,
                                    passwordBytes,
                                    passwordPages
                                )
                                
                                if (!authenticated) {
                                    return@withContext Result.failure(
                                        IOException("Authentication failed. Please check the password.")
                                    )
                                }
                            } else {
                                // Try authentication anyway
                                if (!authenticate(nfcA, passwordBytes)) {
                                    return@withContext Result.failure(
                                        IOException("Authentication failed. Please check the password.")
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Read and parse NDEF data
                val ndefMessage = readAndParseNDEF(nfcA)
                
                if (ndefMessage != null && ndefMessage.records.isNotEmpty()) {
                    val record = ndefMessage.records[0]
                    val text = parseNDEFRecord(record)
                    Result.success(text)
                } else {
                    Result.success("")
                }
                
            } finally {
                nfcA.close()
            }
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Write data to the tag with authentication
     * Matches Swift writeData function with proper TLV format
     */
    suspend fun writeData(tag: Tag, data: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val nfcA = NfcA.get(tag) ?: return@withContext Result.failure(
                IOException("Tag does not support NfcA")
            )
            
            nfcA.connect()
            
            try {
                // Authenticate with password first
                val passwordBytes = passwordToBytes(password)
                if (!authenticate(nfcA, passwordBytes)) {
                    return@withContext Result.failure(
                        IOException("Authentication failed. Please check the password.")
                    )
                }
                
                // Create NDEF message from the data string
                val ndefRecord = if (data.startsWith("http://") || data.startsWith("https://")) {
                    // URI record
                    NdefRecord.createUri(data)
                } else {
                    // Text record
                    NdefRecord.createTextRecord("en", data)
                }
                
                val ndefMessage = NdefMessage(arrayOf(ndefRecord))
                val serializedMessage = serializeNDEFMessage(ndefMessage)
                
                // Create TLV wrapper: [0x03 (NDEF Message)] [Length] [NDEF Data] [0xFE (Terminator)]
                val tlvData: ByteArray
                
                if (serializedMessage.size < 255) {
                    // Short format: [0x03] [Length (1 byte)] [NDEF Data] [0xFE]
                    tlvData = ByteArray(2 + serializedMessage.size + 1)
                    tlvData[0] = 0x03  // T: NDEF Message
                    tlvData[1] = serializedMessage.size.toByte()  // L: Short format
                    System.arraycopy(serializedMessage, 0, tlvData, 2, serializedMessage.size)
                    tlvData[2 + serializedMessage.size] = 0xFE.toByte()  // Terminator
                } else {
                    // Long format: [0x03] [0xFF] [Length High] [Length Low] [NDEF Data] [0xFE]
                    tlvData = ByteArray(4 + serializedMessage.size + 1)
                    tlvData[0] = 0x03  // T: NDEF Message
                    tlvData[1] = 0xFF.toByte()  // Long format marker
                    tlvData[2] = ((serializedMessage.size shr 8) and 0xFF).toByte()
                    tlvData[3] = (serializedMessage.size and 0xFF).toByte()
                    System.arraycopy(serializedMessage, 0, tlvData, 4, serializedMessage.size)
                    tlvData[4 + serializedMessage.size] = 0xFE.toByte()  // Terminator
                }
                
                // Write raw data starting at page 4
                writeRawData(nfcA, tlvData, 0x04)
                
                Result.success("Data written successfully")
                
            } finally {
                nfcA.close()
            }
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Write raw data to tag pages
     * Writes 4 bytes at a time starting from startPage
     */
    private suspend fun writeRawData(nfcA: NfcA, data: ByteArray, startPage: Int) {
        var currentPage = startPage
        var offset = 0
        
        while (offset < data.size) {
            val pageData = ByteArray(4)
            for (i in 0 until 4) {
                if (offset + i < data.size) {
                    pageData[i] = data[offset + i]
                } else {
                    pageData[i] = 0x00
                }
            }
            
            val writeCommand = byteArrayOf(
                CMD_WRITE,
                currentPage.toByte(),
                pageData[0],
                pageData[1],
                pageData[2],
                pageData[3]
            )
            
            nfcA.transceive(writeCommand)
            
            currentPage++
            offset += 4
            
            // Small delay between pages to avoid overwhelming the tag
            if (offset < data.size) {
                delay(10)
            }
        }
    }
    
    /**
     * Read and parse NDEF data from tag
     * Reads pages starting from page 3 to get CC, then reads NDEF data
     */
    private suspend fun readAndParseNDEF(nfcA: NfcA): NdefMessage? = withContext(Dispatchers.IO) {
        var accumulatedData = mutableListOf<Byte>()
        var maxUserPage = 0
        var currentPage = 0x03
        
        while (true) {
            if (maxUserPage > 0 && currentPage > maxUserPage) {
                break  // Reached end
            }
            
            val cmd = byteArrayOf(CMD_READ, currentPage.toByte())
            val response = nfcA.transceive(cmd)
            
            if (response == null || response.isEmpty()) {
                break
            }
            
            var currentChunk = response.toList()
            
            // Parse Capability Container (only on first read of Page 3)
            if (currentPage == 0x03) {
                if (currentChunk.size >= 16) {
                    val mlen = currentChunk[14].toInt() and 0xFF  // CC is at byte 14 (page 3, byte 2)
                    val totalBytes = mlen * 8
                    val totalPages = totalBytes / 4
                    maxUserPage = 3 + totalPages
                    
                    // Remove the first 4 bytes (Page 3) because they are config, not NDEF data
                    currentChunk = currentChunk.drop(4)
                }
            }
            
            accumulatedData.addAll(currentChunk)
            
            // Check for NDEF TLV header (0x03)
            var tlvStartIndex = 0
            var foundTLV = false
            var payloadLen = 0
            var contentStartIndex = 0
            
            while (tlvStartIndex < accumulatedData.size) {
                if (accumulatedData[tlvStartIndex].toInt() and 0xFF == 0x03) {
                    // Found NDEF TLV tag
                    foundTLV = true
                    val lengthByteIndex = tlvStartIndex + 1
                    
                    if (accumulatedData.size <= lengthByteIndex) {
                        // Need more data
                        break
                    }
                    
                    val lengthByte = accumulatedData[lengthByteIndex].toInt() and 0xFF
                    
                    if (lengthByte != 0xFF) {
                        // Short format
                        payloadLen = lengthByte
                        contentStartIndex = tlvStartIndex + 2
                    } else {
                        // Long format (FF LL LL)
                        if (accumulatedData.size < lengthByteIndex + 3) {
                            // Need more data
                            break
                        }
                        val lenHigh = accumulatedData[lengthByteIndex + 1].toInt() and 0xFF
                        val lenLow = accumulatedData[lengthByteIndex + 2].toInt() and 0xFF
                        payloadLen = (lenHigh shl 8) or lenLow
                        contentStartIndex = tlvStartIndex + 4
                    }
                    
                    val totalNeeded = contentStartIndex + payloadLen
                    if (accumulatedData.size >= totalNeeded) {
                        // We have the full message
                        val rawNdef = accumulatedData.subList(contentStartIndex, totalNeeded).toByteArray()
                        return@withContext try {
                            NdefMessage(rawNdef)
                        } catch (e: Exception) {
                            null
                        }
                    } else {
                        // Need more data, continue reading
                        break
                    }
                } else if (accumulatedData[tlvStartIndex].toInt() and 0xFF == 0xFE ||
                    accumulatedData[tlvStartIndex].toInt() and 0xFF == 0x00) {
                    // Skip terminator or padding
                    tlvStartIndex++
                } else {
                    tlvStartIndex++
                }
            }
            
            // If we found TLV but need more data, or haven't found TLV yet, read next block
            currentPage += 4
        }
        
        // If we have data but couldn't parse it, try to create NdefMessage from accumulated data
        if (accumulatedData.isNotEmpty()) {
            try {
                // Remove padding
                while (accumulatedData.isNotEmpty() && 
                       (accumulatedData.last().toInt() and 0xFF == 0xFE || 
                        accumulatedData.last().toInt() and 0xFF == 0x00)) {
                    accumulatedData.removeAt(accumulatedData.size - 1)
                }
                if (accumulatedData.isNotEmpty()) {
                    NdefMessage(accumulatedData.toByteArray())
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }
    
    /**
     * Serialize NDEF message to byte array
     * Matches Swift asData() extension
     */
    private fun serializeNDEFMessage(message: NdefMessage): ByteArray {
        val records = message.records
        val data = mutableListOf<Byte>()
        
        for ((index, record) in records.withIndex()) {
            val isFirst = (index == 0)
            val isLast = (index == records.size - 1)
            
            data.addAll(serializeNDEFRecord(record, isFirst, isLast).toList())
        }
        
        return data.toByteArray()
    }
    
    /**
     * Serialize a single NDEF record
     */
    private fun serializeNDEFRecord(record: NdefRecord, isFirst: Boolean, isLast: Boolean): ByteArray {
        val buffer = mutableListOf<Byte>()
        
        // Construct Header Byte
        var header = record.tnf
        if (isFirst) header = header or 0x80  // MB
        if (isLast) header = header or 0x40   // ME
        
        val isShort = record.payload.size <= 255
        if (isShort) header = header or 0x10  // SR
        
        val hasId = record.id != null && record.id.isNotEmpty()
        if (hasId) header = header or 0x08   // IL
        
        buffer.add(header.toByte())
        
        // Type Length
        buffer.add(record.type.size.toByte())
        
        // Payload Length
        if (isShort) {
            buffer.add(record.payload.size.toByte())
        } else {
            val length = record.payload.size
            buffer.add(((length shr 24) and 0xFF).toByte())
            buffer.add(((length shr 16) and 0xFF).toByte())
            buffer.add(((length shr 8) and 0xFF).toByte())
            buffer.add((length and 0xFF).toByte())
        }
        
        // ID Length (Optional)
        if (hasId) {
            buffer.add(record.id.size.toByte())
        }
        
        // Type
        buffer.addAll(record.type.toList())
        
        // ID (Optional)
        if (hasId) {
            buffer.addAll(record.id.toList())
        }
        
        // Payload
        buffer.addAll(record.payload.toList())
        
        return buffer.toByteArray()
    }
    
    /**
     * Parse NDEF record to extract text/URL
     */
    private fun parseNDEFRecord(record: NdefRecord): String {
        when (record.tnf) {
            NdefRecord.TNF_WELL_KNOWN -> {
                // Check if it's a URI record (type "U" = 0x55)
                if (record.type.contentEquals(byteArrayOf(0x55))) {
                    val uri = parseNDEFURIPayload(record.payload)
                    if (uri.isNotEmpty()) return uri
                }
                // Check if it's a text record (type "T" = 0x54)
                if (record.type.contentEquals(byteArrayOf(0x54))) {
                    val text = parseNDEFTextPayload(record.payload)
                    if (text.isNotEmpty()) return text
                }
            }
        }
        
        // Fallback: try to decode as UTF-8 string
        return String(record.payload, Charsets.UTF_8)
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
            "ftp://anonymous:anonymous@", // 0x07
            "ftp://ftp.",          // 0x08
            "ftps://",             // 0x09
            "sftp://",             // 0x0A
            "smb://",              // 0x0B
            "nfs://",              // 0x0C
            "ftp://",              // 0x0D
            "dav://",              // 0x0E
            "news:",               // 0x0F
            "telnet://",           // 0x10
            "imap:",               // 0x11
            "rtsp://",             // 0x12
            "urn:",                // 0x13
            "pop:",                // 0x14
            "sip:",                // 0x15
            "sips:",               // 0x16
            "tftp://",             // 0x17
            "btspp://",            // 0x18
            "btl2cap://",          // 0x19
            "btgoep://",           // 0x1A
            "tcpobex://",          // 0x1B
            "irdaobex://",         // 0x1C
            "file://",             // 0x1D
            "urn:epc:id:",         // 0x1E
            "urn:epc:tag:",        // 0x1F
            "urn:epc:pat:",        // 0x20
            "urn:epc:raw:",        // 0x21
            "urn:epc:",            // 0x22
            "urn:nfc:"             // 0x23
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
}

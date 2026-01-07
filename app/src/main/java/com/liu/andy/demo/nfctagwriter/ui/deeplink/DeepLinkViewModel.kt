package com.liu.andy.demo.nfctagwriter.ui.deeplink

import android.nfc.Tag
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.liu.andy.demo.nfctagwriter.nfc.NTag424Manager
import com.liu.andy.demo.nfctagwriter.nfc.NTAG424Verifier
import net.bplearning.ntag424.constants.Ntag424
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Data class to hold parsed URL parameters
 */
data class FirewallaUrlParams(
    val gid: String? = null,
    val rule: String? = null,
    val uid: String? = null,
    val counter: String? = null,
    val cmac: String? = null,
    val fullUrl: String? = null
)

class DeepLinkViewModel : ViewModel() {
    
    private val nfcManager = NTag424Manager()
    
    private val _urlParams = MutableStateFlow<FirewallaUrlParams?>(null)
    val urlParams: StateFlow<FirewallaUrlParams?> = _urlParams.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    private val _verificationResult = MutableStateFlow<Boolean?>(null)
    val verificationResult: StateFlow<Boolean?> = _verificationResult.asStateFlow()
    
    /**
     * Parse URL and extract parameters, then verify SDM MAC
     */
    fun parseUrl(url: String) {
        try {
            android.util.Log.d("DeepLinkViewModel", "parseUrl called with: $url")
            // URL might already be decoded, but decode it anyway to be safe
            val decodedUrl = try {
                URLDecoder.decode(url, "UTF-8")
            } catch (e: Exception) {
                url // Use original if decoding fails
            }
            android.util.Log.d("DeepLinkViewModel", "Decoded URL: $decodedUrl")
            
            // Use Android's Uri class instead of java.net.URI for better Android compatibility
            val uri = android.net.Uri.parse(decodedUrl)
            val query = uri.query ?: ""
            android.util.Log.d("DeepLinkViewModel", "Query string: $query")
            
            val params = query.split("&").associate { param ->
                val parts = param.split("=", limit = 2)
                if (parts.size == 2) {
                    val key = parts[0]
                    val value = try {
                        URLDecoder.decode(parts[1], "UTF-8")
                    } catch (e: Exception) {
                        parts[1]
                    }
                    android.util.Log.d("DeepLinkViewModel", "Param: $key = $value")
                    key to value
                } else {
                    parts[0] to ""
                }
            }
            
            val urlParams = FirewallaUrlParams(
                gid = params["gid"],
                rule = params["rule"],
                uid = params["u"],
                counter = params["c"],
                cmac = params["m"],
                fullUrl = decodedUrl
            )
            android.util.Log.d("DeepLinkViewModel", "Parsed params - gid: ${urlParams.gid}, rule: ${urlParams.rule}, uid: ${urlParams.uid}, counter: ${urlParams.counter}, cmac: ${urlParams.cmac}")
            _urlParams.value = urlParams
            _errorMessage.value = null
            
            // Verify SDM MAC if uid, counter, and cmac are present
            if (urlParams.uid != null && urlParams.counter != null && urlParams.cmac != null) {
                verifySDMMAC(urlParams.uid, urlParams.counter, urlParams.cmac)
            } else {
                _verificationResult.value = null
                android.util.Log.d("DeepLinkViewModel", "SDM parameters missing, skipping verification")
            }
            
            android.util.Log.d("DeepLinkViewModel", "URL params state updated - _urlParams.value is now: ${_urlParams.value}")
        } catch (e: Exception) {
            android.util.Log.e("DeepLinkViewModel", "Parse error: ${e.message}", e)
            _errorMessage.value = "Failed to parse URL: ${e.message}"
        }
    }
    
    /**
     * Read data from NTAG424 tag and parse URL
     */
    fun readTagData(tag: Tag) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            nfcManager.readData(tag)
                .onSuccess { data ->
                    if (data.isNotEmpty()) {
                        parseUrl(data)
                    } else {
                        _errorMessage.value = "Tag contains no data"
                    }
                    _isLoading.value = false
                }
                .onFailure { exception ->
                    _errorMessage.value = "Failed to read tag: ${exception.message}"
                    _isLoading.value = false
                }
        }
    }
    
    /**
     * Verify SDM MAC using the extracted parameters
     */
    private fun verifySDMMAC(uidHex: String, counterHex: String, cmacHex: String) {
        viewModelScope.launch {
            try {
                android.util.Log.d("DeepLinkViewModel", "Verifying SDM MAC - uid: $uidHex, counter: $counterHex, cmac: $cmacHex")
                
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
                
                val isValid = verifier.verifySDMMAC(uidHex, counterHex, cmacHex)
                _verificationResult.value = isValid
                android.util.Log.d("DeepLinkViewModel", "SDM MAC verification result: ${if (isValid) "PASSED" else "FAILED"}")
            } catch (e: Exception) {
                android.util.Log.e("DeepLinkViewModel", "SDM MAC verification error: ${e.message}", e)
                _verificationResult.value = false
                _errorMessage.value = "SDM MAC verification failed: ${e.message}"
            }
        }
    }
    
    /**
     * Clear the parsed URL data
     */
    fun clearData() {
        _urlParams.value = null
        _errorMessage.value = null
        _verificationResult.value = null
    }
}


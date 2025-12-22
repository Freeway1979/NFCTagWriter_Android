package com.liu.andy.demo.nfctagwriter.ui.deeplink

import android.nfc.Tag
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.liu.andy.demo.nfctagwriter.nfc.NTag424Manager
import java.net.URLDecoder

/**
 * Data class to hold parsed URL parameters
 */
data class FirewallaUrlParams(
    val gid: String? = null,
    val rule: String? = null,
    val chksum: String? = null,
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
    
    /**
     * Parse URL and extract parameters
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
                chksum = params["chksum"],
                fullUrl = decodedUrl
            )
            android.util.Log.d("DeepLinkViewModel", "Parsed params - gid: ${urlParams.gid}, rule: ${urlParams.rule}, chksum: ${urlParams.chksum}")
            _urlParams.value = urlParams
            _errorMessage.value = null
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
     * Clear the parsed URL data
     */
    fun clearData() {
        _urlParams.value = null
        _errorMessage.value = null
    }
}


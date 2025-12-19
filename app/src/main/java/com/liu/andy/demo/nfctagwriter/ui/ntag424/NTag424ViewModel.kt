package com.liu.andy.demo.nfctagwriter.ui.ntag424

import android.nfc.Tag
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.liu.andy.demo.nfctagwriter.nfc.NTag424Manager

class NTag424ViewModel : ViewModel() {

    private val nfcManager = NTag424Manager()
    
    private val _password = MutableStateFlow("915565AB915565AB")
    val password: StateFlow<String> = _password.asStateFlow()

    private val _dataToWrite = MutableStateFlow("https://mesh.firewalla.net/nfc?gid=915565a3-65c7-4a2b-8629-194d80ed824b&rule=248")
    val dataToWrite: StateFlow<String> = _dataToWrite.asStateFlow()
    
    private val _readResult = MutableStateFlow("")
    val readResult: StateFlow<String> = _readResult.asStateFlow()
    
    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()
    
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()
    
    private val _logMessages = MutableStateFlow<List<String>>(emptyList())
    val logMessages: StateFlow<List<String>> = _logMessages.asStateFlow()
    
    private var currentTag: Tag? = null
    
    /**
     * Add a log message to the log view
     * @param message The log message to add
     */
    fun addLog(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val logEntry = "[$timestamp] $message"
        _logMessages.value = (_logMessages.value + logEntry).takeLast(50) // Keep last 50 log entries
    }
    
    /**
     * Clear all log messages
     */
    fun clearLogs() {
        _logMessages.value = emptyList()
    }

    fun updatePassword(password: String) {
        _password.value = password
    }

    fun updateDataToWrite(data: String) {
        _dataToWrite.value = data
    }
    
    fun setCurrentTag(tag: Tag?) {
        currentTag = tag
        if (tag != null) {
            _statusMessage.value = "Tag detected. Ready to operate."
            addLog("NTAG424 tag detected")
        } else {
            _statusMessage.value = "No tag detected. Please scan a tag."
            addLog("Tag disconnected")
        }
    }

    /**
     * Convert string to hex string using ASCII mapping
     * Example: "915565AB915565AB" -> "39313535363541423931353536354142"
     */
    private fun stringToHexString(input: String): String {
        return input.map { char ->
            String.format("%02X", char.code)
        }.joinToString("")
    }
    
    fun setPassword() {
        val tag = currentTag
        if (tag == null) {
            _statusMessage.value = "Error: No tag detected. Please scan a tag first."
            addLog("ERROR: No tag detected for password setting")
            return
        }
        
        val passwordInput = _password.value.trim()
        // Convert password string to hex string using ASCII mapping
        // TODO: with iOS, the 32 hex characters should be better. Like "F93E13535363E414F39313535F6F54142", not use ASCII mapping.
        val passwordValue = stringToHexString(passwordInput)
        
        if (passwordValue.length != 32) {
            _statusMessage.value = "Error: Password must be exactly 32 hex characters (16 bytes) after conversion"
            addLog("ERROR: Invalid password length after conversion (${passwordValue.length} chars, expected 32)")
            return
        }
        
        viewModelScope.launch {
            _isProcessing.value = true
            _statusMessage.value = "Setting password..."
            addLog("Starting password setting operation...")
            addLog("Password: ${passwordValue.take(8)}...${passwordValue.takeLast(8)}")
            
            nfcManager.setPassword(tag, passwordValue)
                .onSuccess {
                    _statusMessage.value = "Success: Password set successfully"
                    addLog("SUCCESS: Password set successfully")
                }
                .onFailure { exception ->
                    val errorMsg = exception.message ?: "Failed to set password"
                    _statusMessage.value = "Error: $errorMsg"
                    addLog("ERROR: Password setting failed - $errorMsg")
                }
            
            _isProcessing.value = false
        }
    }

    fun configureCcFile() {
        val tag = currentTag
        if (tag == null) {
            _statusMessage.value = "Error: No tag detected. Please scan a tag first."
            addLog("ERROR: No tag detected for CC file configuration")
            return
        }
        
        val passwordInput = _password.value.trim()
        // Convert password string to hex string using ASCII mapping
        val passwordValue = stringToHexString(passwordInput)
        
        if (passwordValue.length != 32) {
            _statusMessage.value = "Error: Password must be exactly 32 hex characters (16 bytes) after conversion"
            addLog("ERROR: Invalid password length for CC file configuration (${passwordValue.length} chars, expected 32)")
            return
        }
        
        viewModelScope.launch {
            _isProcessing.value = true
            _statusMessage.value = "Configuring CC file..."
            addLog("Starting CC file configuration for iOS background detection...")
            
            nfcManager.configureCCFile(tag, passwordValue)
                .onSuccess {
                    _statusMessage.value = "Success: CC file configured successfully"
                    addLog("SUCCESS: CC file configured for iOS background detection")
                }
                .onFailure { exception ->
                    val errorMsg = exception.message ?: "Failed to configure CC file"
                    _statusMessage.value = "Error: $errorMsg"
                    addLog("ERROR: CC file configuration failed - $errorMsg")
                }
            
            _isProcessing.value = false
        }
    }

    fun configureFileAccess() {
        val tag = currentTag
        if (tag == null) {
            _statusMessage.value = "Error: No tag detected. Please scan a tag first."
            addLog("ERROR: No tag detected for file access configuration")
            return
        }
        
        val passwordInput = _password.value.trim()
        // Convert password string to hex string using ASCII mapping
        val passwordValue = stringToHexString(passwordInput)
        
        if (passwordValue.length != 32) {
            _statusMessage.value = "Error: Password must be exactly 32 hex characters (16 bytes) after conversion"
            addLog("ERROR: Invalid password length for file access configuration (${passwordValue.length} chars, expected 32)")
            return
        }
        
        viewModelScope.launch {
            _isProcessing.value = true
            _statusMessage.value = "Configuring file access..."
            addLog("Starting file access configuration...")
            addLog("Setting: Write requires auth, Read is public")
            
            nfcManager.configureFileAccess(tag, passwordValue)
                .onSuccess {
                    _statusMessage.value = "Success: File access configured successfully"
                    addLog("SUCCESS: File access configured - Write protected, Read public")
                }
                .onFailure { exception ->
                    val errorMsg = exception.message ?: "Failed to configure file access"
                    _statusMessage.value = "Error: $errorMsg"
                    addLog("ERROR: File access configuration failed - $errorMsg")
                }
            
            _isProcessing.value = false
        }
    }

    fun readData() {
        val tag = currentTag
        if (tag == null) {
            _statusMessage.value = "Error: No tag detected. Please scan a tag first."
            addLog("ERROR: No tag detected for read operation")
            return
        }
        
        viewModelScope.launch {
            _isProcessing.value = true
            _statusMessage.value = "Reading data..."
            addLog("Starting data read operation (no authentication required)...")
            
            nfcManager.readData(tag)
                .onSuccess { data ->
                    _readResult.value = data
                    _statusMessage.value = "Success: Data read successfully"
                    val dataLength = data.length
                    addLog("SUCCESS: Data read successfully (${dataLength} characters)")
                    if (data.isNotEmpty()) {
                        addLog("Data preview: ${data.take(50)}${if (data.length > 50) "..." else ""}")
                    } else {
                        addLog("Data is empty")
                    }
                }
                .onFailure { exception ->
                    _readResult.value = ""
                    val errorMsg = exception.message ?: "Failed to read data"
                    _statusMessage.value = "Error: $errorMsg"
                    addLog("ERROR: Read operation failed - $errorMsg")
                }
            
            _isProcessing.value = false
        }
    }

    fun writeData() {
        val tag = currentTag
        if (tag == null) {
            _statusMessage.value = "Error: No tag detected. Please scan a tag first."
            addLog("ERROR: No tag detected for write operation")
            return
        }
        
        val passwordInput = _password.value.trim()
        // Convert password string to hex string using ASCII mapping
        val passwordValue = stringToHexString(passwordInput)
        
        if (passwordValue.length != 32) {
            _statusMessage.value = "Error: Password must be exactly 32 hex characters (16 bytes) after conversion"
            addLog("ERROR: Invalid password length for write operation (${passwordValue.length} chars, expected 32)")
            return
        }
        
        val dataValue = _dataToWrite.value.trim()
        if (dataValue.isEmpty()) {
            _statusMessage.value = "Error: Data to write cannot be empty"
            addLog("ERROR: Data to write is empty")
            return
        }
        
        viewModelScope.launch {
            _isProcessing.value = true
            _statusMessage.value = "Writing data..."
            addLog("Starting data write operation (with authentication)...")
            addLog("Data length: ${dataValue.length} characters")
            addLog("Data preview: ${dataValue.take(50)}${if (dataValue.length > 50) "..." else ""}")
            
            nfcManager.writeData(tag, dataValue, passwordValue)
                .onSuccess {
                    _statusMessage.value = "Success: Data written successfully"
                    addLog("SUCCESS: Data written successfully to tag")
                }
                .onFailure { exception ->
                    val errorMsg = exception.message ?: "Failed to write data"
                    _statusMessage.value = "Error: $errorMsg"
                    addLog("ERROR: Write operation failed - $errorMsg")
                }
            
            _isProcessing.value = false
        }
    }
}


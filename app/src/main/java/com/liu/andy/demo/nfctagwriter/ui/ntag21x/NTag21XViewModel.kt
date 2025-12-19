package com.liu.andy.demo.nfctagwriter.ui.ntag21x

import android.nfc.Tag
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.liu.andy.demo.nfctagwriter.nfc.NTag21XManager
import com.liu.andy.demo.nfctagwriter.nfc.NFCTagInfo

enum class PasswordProtectionMode {
    WriteProtected,
    ReadWriteProtected
}

class NTag21XViewModel : ViewModel() {

    private val nfcManager = NTag21XManager()

    private val _readResult = MutableStateFlow("")
    val readResult: StateFlow<String> = _readResult.asStateFlow()

    private val _textToWrite = MutableStateFlow("https://mesh.firewalla.net/nfc?gid=915565a3-65c7-4a2b-8629-194d80ed824b&rule=248")
    val textToWrite: StateFlow<String> = _textToWrite.asStateFlow()

    private val _passwordProtectionMode = MutableStateFlow(PasswordProtectionMode.WriteProtected)
    val passwordProtectionMode: StateFlow<PasswordProtectionMode> = _passwordProtectionMode.asStateFlow()

    private val _password = MutableStateFlow("5678")
    val password: StateFlow<String> = _password.asStateFlow()
    
    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()
    
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()
    
    private var currentTag: Tag? = null

    fun updateTextToWrite(text: String) {
        _textToWrite.value = text
    }

    fun updateReadResult(result: String) {
        _readResult.value = result
    }

    fun setPasswordProtectionMode(mode: PasswordProtectionMode) {
        _passwordProtectionMode.value = mode
    }

    fun updatePassword(password: String) {
        _password.value = password
    }
    
    fun setCurrentTag(tag: Tag?) {
        currentTag = tag
        if (tag != null) {
            _statusMessage.value = "Tag detected. Ready to operate."
        } else {
            _statusMessage.value = "No tag detected. Please scan a tag."
        }
    }

    fun setPassword() {
        val tag = currentTag
        if (tag == null) {
            _statusMessage.value = "Error: No tag detected. Please scan a tag first."
            return
        }
        
        val passwordValue = _password.value.trim()
        if (passwordValue.isEmpty()) {
            _statusMessage.value = "Error: Password cannot be empty"
            return
        }
        
        viewModelScope.launch {
            _isProcessing.value = true
            _statusMessage.value = "Setting password..."
            
            val writeOnlyProtection = (_passwordProtectionMode.value == PasswordProtectionMode.WriteProtected)
            nfcManager.setPassword(tag, passwordValue, writeOnlyProtection)
                .onSuccess {
                    _statusMessage.value = "Success: $it"
                }
                .onFailure { exception ->
                    _statusMessage.value = "Error: ${exception.message ?: "Failed to set password"}"
                }
            
            _isProcessing.value = false
        }
    }

    fun readNfcTag() {
        val tag = currentTag
        if (tag == null) {
            _statusMessage.value = "Error: No tag detected. Please scan a tag first."
            return
        }
        
        viewModelScope.launch {
            _isProcessing.value = true
            _statusMessage.value = "Reading data..."
            
            // Pass password if provided (for authenticated reads)
            val passwordValue = _password.value.trim()
            val password = if (passwordValue.isNotEmpty()) passwordValue else null
            
            nfcManager.readData(tag, password)
                .onSuccess { data ->
                    _readResult.value = data
                    _statusMessage.value = if (data.isEmpty()) {
                        "Success: Tag read (empty or no data)"
                    } else {
                        "Success: Data read successfully"
                    }
                }
                .onFailure { exception ->
                    _readResult.value = ""
                    _statusMessage.value = "Error: ${exception.message ?: "Failed to read data"}"
                }
            
            _isProcessing.value = false
        }
    }

    fun writeNfcTag() {
        val tag = currentTag
        if (tag == null) {
            _statusMessage.value = "Error: No tag detected. Please scan a tag first."
            return
        }
        
        val passwordValue = _password.value.trim()
        if (passwordValue.isEmpty()) {
            _statusMessage.value = "Error: Password cannot be empty for write operation"
            return
        }
        
        val dataValue = _textToWrite.value.trim()
        if (dataValue.isEmpty()) {
            _statusMessage.value = "Error: Data to write cannot be empty"
            return
        }
        
        viewModelScope.launch {
            _isProcessing.value = true
            _statusMessage.value = "Writing data..."
            
            nfcManager.writeData(tag, dataValue, passwordValue)
                .onSuccess {
                    _statusMessage.value = "Success: Data written successfully"
                }
                .onFailure { exception ->
                    _statusMessage.value = "Error: ${exception.message ?: "Failed to write data"}"
                }
            
            _isProcessing.value = false
        }
    }

    fun readTagInformation() {
        val tag = currentTag
        if (tag == null) {
            _statusMessage.value = "Error: No tag detected. Please scan a tag first."
            return
        }
        
        viewModelScope.launch {
            _isProcessing.value = true
            _statusMessage.value = "Reading tag information..."
            
            nfcManager.readTagInfo(tag)
                .onSuccess { tagInfo ->
                    _readResult.value = tagInfo.details
                    _statusMessage.value = "Success: Tag information read\n" +
                            "Type: ${tagInfo.tagType}\n" +
                            "Serial: ${tagInfo.serialNumber}\n" +
                            "Password Protected: ${if (tagInfo.isPasswordProtected) "Yes" else "No"}"
                }
                .onFailure { exception ->
                    _readResult.value = ""
                    _statusMessage.value = "Error: ${exception.message ?: "Failed to read tag information"}"
                }
            
            _isProcessing.value = false
        }
    }
}


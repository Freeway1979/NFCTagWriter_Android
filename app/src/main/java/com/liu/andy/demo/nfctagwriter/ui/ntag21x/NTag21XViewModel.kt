package com.liu.andy.demo.nfctagwriter.ui.ntag21x

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class PasswordProtectionMode {
    WriteProtected,
    ReadWriteProtected
}

class NTag21XViewModel : ViewModel() {

    private val _readResult = MutableStateFlow("")
    val readResult: StateFlow<String> = _readResult.asStateFlow()

    private val _textToWrite = MutableStateFlow("https://mesh.firewalla.net/nfc?gid=915565...")
    val textToWrite: StateFlow<String> = _textToWrite.asStateFlow()

    private val _passwordProtectionMode = MutableStateFlow(PasswordProtectionMode.WriteProtected)
    val passwordProtectionMode: StateFlow<PasswordProtectionMode> = _passwordProtectionMode.asStateFlow()

    private val _password = MutableStateFlow("5678")
    val password: StateFlow<String> = _password.asStateFlow()

    fun updateTextToWrite(text: String) {
        _textToWrite.value = text
    }

    fun updateReadResult(result: String) {
        _readResult.value = result
    }

    fun setPasswordProtectionMode(mode: PasswordProtectionMode) {
        _passwordProtectionMode.value = mode
    }

    fun setPassword(password: String) {
        _password.value = password
    }

    fun readNfcTag() {
        // TODO: Implement NFC tag reading
        viewModelScope.launch {
            _readResult.value = "Tag read successfully"
        }
    }

    fun writeNfcTag() {
        // TODO: Implement NFC tag writing
        viewModelScope.launch {
            // Writing logic here
        }
    }

    fun readTagInformation() {
        // TODO: Implement tag information reading
        viewModelScope.launch {
            _readResult.value = "Tag information read"
        }
    }
}


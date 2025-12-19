package com.liu.andy.demo.nfctagwriter.ui.ntag424

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NTag424ViewModel : ViewModel() {

    private val _password = MutableStateFlow("915565AB915565AB")
    val password: StateFlow<String> = _password.asStateFlow()

    private val _dataToWrite = MutableStateFlow("https://mesh.firewalla.net/nfc?gi...")
    val dataToWrite: StateFlow<String> = _dataToWrite.asStateFlow()

    fun updatePassword(password: String) {
        _password.value = password
    }

    fun updateDataToWrite(data: String) {
        _dataToWrite.value = data
    }

    fun setPassword() {
        // TODO: Implement password setting
        viewModelScope.launch {
            // Password setting logic here
        }
    }

    fun configureCcFile() {
        // TODO: Implement CC file configuration
        viewModelScope.launch {
            // CC file configuration logic here
        }
    }

    fun configureFileAccess() {
        // TODO: Implement file access configuration
        viewModelScope.launch {
            // File access configuration logic here
        }
    }

    fun readData() {
        // TODO: Implement data reading
        viewModelScope.launch {
            // Data reading logic here
        }
    }

    fun writeData() {
        // TODO: Implement data writing
        viewModelScope.launch {
            // Data writing logic here
        }
    }
}


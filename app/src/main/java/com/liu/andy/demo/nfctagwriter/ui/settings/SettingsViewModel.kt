package com.liu.andy.demo.nfctagwriter.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel : ViewModel() {

    private val _text = MutableStateFlow("This is settings Fragment")
    val text: StateFlow<String> = _text.asStateFlow()
}

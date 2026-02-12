package com.liu.andy.demo.nfctagwriter.ui.validation

import android.nfc.Tag
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liu.andy.demo.nfctagwriter.NfcTagHolder
import com.liu.andy.demo.nfctagwriter.nfc.NTag424Manager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class StepStatus {
    PENDING, IN_PROGRESS, SUCCESS, FAILED, SKIPPED
}

data class ValidationStep(
    val title: String,
    val description: String,
    val status: StepStatus = StepStatus.PENDING,
    val detail: String = ""
)

class ValidationFlowViewModel : ViewModel() {

    private val nfcManager = NTag424Manager()

    private val _steps = MutableStateFlow(createInitialSteps())
    val steps: StateFlow<List<ValidationStep>> = _steps.asStateFlow()

    private val _overallResult = MutableStateFlow<Boolean?>(null)
    val overallResult: StateFlow<Boolean?> = _overallResult.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _showApproachTagDialog = MutableStateFlow(false)
    val showApproachTagDialog: StateFlow<Boolean> = _showApproachTagDialog.asStateFlow()

    private var pendingOperation: (() -> Unit)? = null
    private var currentTag: Tag? = null

    private fun createInitialSteps(): List<ValidationStep> = listOf(
        ValidationStep("Generate Keys", "Generate Key0-Key4 passwords from tag UID"),
        ValidationStep("Verify Key 0", "Authenticate with generated Key 0"),
        ValidationStep("Verify Key 3", "Authenticate with generated Key 3"),
        ValidationStep("Read File 03", "Read 72 bytes signed data from offset 0"),
        ValidationStep("Verify Signature", "Unpad and verify signature with public key")
    )

    fun startValidation() {
        // Reset state
        _steps.value = createInitialSteps()
        _overallResult.value = null

        // Clear tag to require fresh detection
        currentTag = null
        NfcTagHolder.currentTag = null

        // Set pending operation and show approach dialog
        pendingOperation = { runValidation() }
        _showApproachTagDialog.value = true
    }

    fun setCurrentTag(tag: Tag?) {
        currentTag = tag
        if (tag != null) {
            pendingOperation?.let { op ->
                pendingOperation = null
                _showApproachTagDialog.value = false
                op()
            }
        }
    }

    fun dismissApproachTagDialog() {
        _showApproachTagDialog.value = false
        pendingOperation = null
    }

    private fun runValidation() {
        val tag = currentTag ?: return

        viewModelScope.launch {
            _isRunning.value = true

            val result = nfcManager.runValidationFlow(
                tag = tag,
                onStepStarted = { stepIndex ->
                    updateStep(stepIndex, StepStatus.IN_PROGRESS, "")
                },
                onStepCompleted = { stepIndex, success, detail ->
                    updateStep(
                        stepIndex,
                        if (success) StepStatus.SUCCESS else StepStatus.FAILED,
                        detail
                    )
                    if (!success) {
                        markRemainingAsSkipped(stepIndex + 1)
                    }
                }
            )

            result.onSuccess { isValid ->
                _overallResult.value = isValid
            }.onFailure { error ->
                _overallResult.value = false
                handleValidationError(error)
            }

            _isRunning.value = false
            currentTag = null
            NfcTagHolder.currentTag = null
        }
    }

    private fun updateStep(index: Int, status: StepStatus, detail: String) {
        val currentSteps = _steps.value.toMutableList()
        if (index < currentSteps.size) {
            currentSteps[index] = currentSteps[index].copy(status = status, detail = detail)
            _steps.value = currentSteps
        }
    }

    private fun markRemainingAsSkipped(fromIndex: Int) {
        val currentSteps = _steps.value.toMutableList()
        for (i in fromIndex until currentSteps.size) {
            currentSteps[i] = currentSteps[i].copy(
                status = StepStatus.SKIPPED,
                detail = "Skipped due to previous failure"
            )
        }
        _steps.value = currentSteps
    }

    private fun handleValidationError(error: Throwable) {
        val currentSteps = _steps.value.toMutableList()
        var errorHandled = false

        for (i in currentSteps.indices) {
            when (currentSteps[i].status) {
                StepStatus.IN_PROGRESS -> {
                    currentSteps[i] = currentSteps[i].copy(
                        status = StepStatus.FAILED,
                        detail = error.message ?: "Unknown error"
                    )
                    errorHandled = true
                }
                StepStatus.PENDING -> {
                    if (!errorHandled) {
                        currentSteps[i] = currentSteps[i].copy(
                            status = StepStatus.FAILED,
                            detail = error.message ?: "Unknown error"
                        )
                        errorHandled = true
                    } else {
                        currentSteps[i] = currentSteps[i].copy(
                            status = StepStatus.SKIPPED,
                            detail = "Skipped due to previous failure"
                        )
                    }
                }
                else -> { /* Leave SUCCESS, FAILED, SKIPPED states as is */ }
            }
        }
        _steps.value = currentSteps
    }
}

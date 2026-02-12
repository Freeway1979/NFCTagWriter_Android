package com.liu.andy.demo.nfctagwriter.ui.validation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.liu.andy.demo.nfctagwriter.ui.ntag424.ApproachTagDialog

@Composable
fun ValidationFlowScreen(
    viewModel: ValidationFlowViewModel = viewModel(),
    navController: NavController? = null
) {
    val steps by viewModel.steps.collectAsState()
    val overallResult by viewModel.overallResult.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val showApproachTagDialog by viewModel.showApproachTagDialog.collectAsState()

    val scrollState = rememberScrollState()

    if (showApproachTagDialog) {
        ApproachTagDialog(onDismiss = { viewModel.dismissApproachTagDialog() })
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Security,
                contentDescription = "Validation",
                modifier = Modifier.size(48.dp),
                tint = Color(0xFF81D4FA)
            )
            Text(
                text = "Validation Flow",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Verify NTAG424 tag authenticity step by step",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Start / Run Again Button
        Button(
            onClick = { viewModel.startValidation() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isRunning,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (overallResult == null) Color(0xFF4CAF50) else Color(0xFFFF9800)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = if (overallResult == null) Icons.Filled.PlayArrow else Icons.Filled.Refresh,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (overallResult == null) "Start Validation" else "Run Again")
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Steps (stepper layout with no spacing between steps for continuous lines)
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            steps.forEachIndexed { index, step ->
                ValidationStepCard(
                    stepNumber = index + 1,
                    step = step,
                    isLast = index == steps.size - 1
                )
            }
        }

        // Overall Result Banner
        overallResult?.let { isSuccess ->
            Spacer(modifier = Modifier.height(8.dp))
            OverallResultBanner(isSuccess = isSuccess)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun ValidationStepCard(
    stepNumber: Int,
    step: ValidationStep,
    isLast: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Step indicator column with connecting line
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxHeight()
        ) {
            // Status circle
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        when (step.status) {
                            StepStatus.SUCCESS -> Color(0xFF4CAF50)
                            StepStatus.FAILED -> Color(0xFFF44336)
                            StepStatus.IN_PROGRESS -> Color(0xFFFF9800)
                            StepStatus.SKIPPED -> Color(0xFF616161)
                            StepStatus.PENDING -> Color(0xFF424242)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                when (step.status) {
                    StepStatus.SUCCESS -> Icon(
                        Icons.Filled.Check,
                        contentDescription = "Success",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    StepStatus.FAILED -> Icon(
                        Icons.Filled.Close,
                        contentDescription = "Failed",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    StepStatus.IN_PROGRESS -> CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    else -> Text(
                        "$stepNumber",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Connecting line to next step
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .weight(1f)
                        .background(
                            when (step.status) {
                                StepStatus.SUCCESS -> Color(0xFF4CAF50).copy(alpha = 0.5f)
                                StepStatus.FAILED -> Color(0xFFF44336).copy(alpha = 0.3f)
                                else -> Color(0xFF424242)
                            }
                        )
                )
            }
        }

        // Step content
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (!isLast) 20.dp else 0.dp)
        ) {
            Text(
                text = step.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = when (step.status) {
                    StepStatus.SKIPPED -> Color(0xFF9E9E9E)
                    StepStatus.PENDING -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
            Text(
                text = step.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )

            if (step.detail.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = Color(0xFF1E1E1E),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(8.dp)
                ) {
                    Text(
                        text = step.detail,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = when (step.status) {
                            StepStatus.SUCCESS -> Color(0xFF51CF66)
                            StepStatus.FAILED -> Color(0xFFFF6B6B)
                            StepStatus.SKIPPED -> Color(0xFF888888)
                            else -> Color(0xFFE0E0E0)
                        },
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
fun OverallResultBanner(isSuccess: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (isSuccess) Color(0xFF1B5E20) else Color(0xFFB71C1C),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = if (isSuccess) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
            Text(
                text = if (isSuccess) "VALIDATION PASSED" else "VALIDATION FAILED",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

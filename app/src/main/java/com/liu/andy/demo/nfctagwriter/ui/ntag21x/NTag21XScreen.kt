package com.liu.andy.demo.nfctagwriter.ui.ntag21x

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.liu.andy.demo.nfctagwriter.navigation.Screen

@Composable
fun NTag21XScreen(
    viewModel: NTag21XViewModel = viewModel(),
    navController: NavController? = null
) {
    val readResult by viewModel.readResult.collectAsState()
    val textToWrite by viewModel.textToWrite.collectAsState()
    val passwordProtectionMode by viewModel.passwordProtectionMode.collectAsState()
    val password by viewModel.password.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // NFC Icon and Title Section
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // NFC Icon
            Icon(
                imageVector = Icons.Filled.Nfc,
                contentDescription = "NFC Icon",
                modifier = Modifier.size(64.dp),
                tint = Color(0xFF2196F3) // Blue color
            )
            
            Text(
                text = "NTAG213/215/216",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        
        // Status Message
        if (statusMessage.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = if (statusMessage.startsWith("Error:")) {
                            Color(0xFFFFEBEE) // Light red
                        } else if (statusMessage.startsWith("Success:")) {
                            Color(0xFFE8F5E9) // Light green
                        } else {
                            Color(0xFFE3F2FD) // Light blue
                        },
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(12.dp)
            ) {
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (statusMessage.startsWith("Error:")) {
                        Color(0xFFC62828) // Dark red
                    } else if (statusMessage.startsWith("Success:")) {
                        Color(0xFF2E7D32) // Dark green
                    } else {
                        Color(0xFF1976D2) // Dark blue
                    }
                )
            }
        }
        
        // Read Result Section
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Read Result:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            OutlinedTextField(
                value = readResult,
                onValueChange = { },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                placeholder = {
                    Text(
                        text = "",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = Color(0xFFF5F5F5), // Light gray
                    focusedContainerColor = Color(0xFFF5F5F5)
                ),
                shape = RoundedCornerShape(12.dp),
                maxLines = 5
            )
            
            Button(
                onClick = { viewModel.readNfcTag() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isProcessing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2196F3) // Blue
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowDownward,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Read NFC Tag")
            }
        }

        // Text to Write Section
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Text to Write:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            OutlinedTextField(
                value = textToWrite,
                onValueChange = { viewModel.updateTextToWrite(it) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            )
            
            Button(
                onClick = { viewModel.writeNfcTag() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isProcessing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50) // Green
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowUpward,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Write NFC Tag")
            }
        }

        // Password Protection Mode Section
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Password Protection Mode:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Write Protected Button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (passwordProtectionMode == PasswordProtectionMode.WriteProtected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                Color.White
                            }
                        )
                        .border(
                            width = if (passwordProtectionMode != PasswordProtectionMode.WriteProtected) 1.dp else 0.dp,
                            color = Color(0xFFE0E0E0),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { viewModel.setPasswordProtectionMode(PasswordProtectionMode.WriteProtected) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Write Protected",
                        color = if (passwordProtectionMode == PasswordProtectionMode.WriteProtected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                
                // Read & Write Protected Button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (passwordProtectionMode == PasswordProtectionMode.ReadWriteProtected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                Color.White
                            }
                        )
                        .border(
                            width = if (passwordProtectionMode != PasswordProtectionMode.ReadWriteProtected) 1.dp else 0.dp,
                            color = Color(0xFFE0E0E0),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { viewModel.setPasswordProtectionMode(PasswordProtectionMode.ReadWriteProtected) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Read & Write Protected",
                        color = if (passwordProtectionMode == PasswordProtectionMode.ReadWriteProtected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        fontSize = 12.sp,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            
            // Password input field
            OutlinedTextField(
                value = password,
                onValueChange = { viewModel.updatePassword(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Password (4 digits or 8 hex chars)") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
            
            Button(
                onClick = { viewModel.setPassword() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isProcessing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF9800) // Orange
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Set Password")
            }
        }

        // Read Tag Information Button
        Button(
            onClick = { viewModel.readTagInformation() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isProcessing,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF9C27B0) // Purple
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Read Tag Information")
        }
    }
}

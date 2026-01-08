package com.liu.andy.demo.nfctagwriter.ui.deeplink

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeepLinkScreen(
    viewModel: DeepLinkViewModel = viewModel(),
    navController: NavController? = null,
    initialUrl: String? = null,
    onDismiss: (() -> Unit)? = null
) {
    val urlParams by viewModel.urlParams.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val verificationResult by viewModel.verificationResult.collectAsState()
    
    // Parse initial URL if provided
    LaunchedEffect(initialUrl) {
        if (initialUrl != null) {
            android.util.Log.d("DeepLinkScreen", "Parsing URL: $initialUrl")
            viewModel.parseUrl(initialUrl)
        } else {
            android.util.Log.d("DeepLinkScreen", "No initial URL provided")
        }
    }
    
    // Debug: Log state changes
    LaunchedEffect(urlParams) {
        android.util.Log.d("DeepLinkScreen", "urlParams changed: $urlParams")
    }
    
    // Don't automatically read tag data - only when user explicitly requests it
    
    // Debug log on composition
    androidx.compose.runtime.LaunchedEffect(Unit) {
        android.util.Log.d("DeepLinkScreen", "DeepLinkScreen composed - initialUrl: $initialUrl")
    }
    
    // Log when screen is actually displayed
    androidx.compose.runtime.LaunchedEffect(urlParams, isLoading, errorMessage) {
        android.util.Log.d("DeepLinkScreen", "UI State - isLoading: $isLoading, errorMessage: $errorMessage, urlParams: ${urlParams != null}")
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top App Bar
        TopAppBar(
            title = { Text("Firewalla NFC Deep Link") },
            navigationIcon = {
                IconButton(onClick = { 
                    if (navController != null) {
                        navController.popBackStack()
                    } else if (onDismiss != null) {
                        onDismiss()
                    }
                }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        )
        
        // Content with scroll support
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            when {
                isLoading -> {
                    android.util.Log.d("DeepLinkScreen", "Showing loading state")
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Reading tag data...",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                
                errorMessage != null -> {
                    android.util.Log.d("DeepLinkScreen", "Showing error: $errorMessage")
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Error,
                            contentDescription = "Error",
                            modifier = Modifier.size(64.dp),
                            tint = Color(0xFFD32F2F)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = errorMessage ?: "Unknown error",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color(0xFFD32F2F),
                            textAlign = TextAlign.Center
                        )
                    }
                }
                
                urlParams != null -> {
                    val params = urlParams!! // Store in local variable for smart cast
                    android.util.Log.d("DeepLinkScreen", "Showing urlParams: gid=${params.gid}, rule=${params.rule}, uid=${params.uid}, counter=${params.counter}, cmac=${params.cmac}, fullUrl=${params.fullUrl}")
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        UrlDetailsCard(params, verificationResult)
                    }
                }
                
                else -> {
                    android.util.Log.d("DeepLinkScreen", "Showing empty state - isLoading=$isLoading, errorMessage=$errorMessage, urlParams=$urlParams, initialUrl=$initialUrl")
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = "Info",
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (initialUrl != null) {
                                "Parsing URL..."
                            } else {
                                "No URL data available"
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun UrlDetailsCard(urlParams: FirewallaUrlParams, verificationResult: Boolean? = null) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Success",
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    text = "URL Details",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            
            // GID
            if (urlParams.gid != null) {
                ParameterRow(
                    label = "GID",
                    value = urlParams.gid
                )
            }
            
            // Rule
            if (urlParams.rule != null) {
                ParameterRow(
                    label = "Rule",
                    value = urlParams.rule
                )
            }
            
            // UID
            if (urlParams.uid != null) {
                ParameterRow(
                    label = "UID",
                    value = urlParams.uid
                )
            }
            
            // Counter
            if (urlParams.counter != null) {
                ParameterRow(
                    label = "Counter",
                    value = urlParams.counter
                )
            }
            
            // CMAC
            if (urlParams.cmac != null) {
                ParameterRow(
                    label = "CMAC",
                    value = urlParams.cmac
                )
            }
            
            // SDM MAC Verification Result
            if (verificationResult != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = if (verificationResult) {
                                Color(0xFF4CAF50).copy(alpha = 0.2f)
                            } else {
                                Color(0xFFD32F2F).copy(alpha = 0.2f)
                            },
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (verificationResult) Icons.Filled.CheckCircle else Icons.Filled.Error,
                        contentDescription = "Verification Result",
                        tint = if (verificationResult) Color(0xFF4CAF50) else Color(0xFFD32F2F),
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = if (verificationResult) {
                            "SDM MAC Verification: PASSED ✓"
                        } else {
                            "SDM MAC Verification: FAILED ✗"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (verificationResult) Color(0xFF4CAF50) else Color(0xFFD32F2F)
                    )
                }
            }
            
            // Full URL - Always show with divider for better visibility
            Divider(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.outlineVariant,
                thickness = 1.dp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Full URL:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = urlParams.fullUrl ?: "Not available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun ParameterRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(100.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}


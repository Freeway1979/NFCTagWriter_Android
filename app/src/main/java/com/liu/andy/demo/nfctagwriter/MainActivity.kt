package com.liu.andy.demo.nfctagwriter

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.liu.andy.demo.nfctagwriter.navigation.Screen
import com.liu.andy.demo.nfctagwriter.ui.ntag424.NTag424Screen
import com.liu.andy.demo.nfctagwriter.ui.ntag424.NTag424ViewModel
import com.liu.andy.demo.nfctagwriter.ui.ntag21x.NTag21XScreen
import com.liu.andy.demo.nfctagwriter.ui.ntag21x.NTag21XViewModel
import com.liu.andy.demo.nfctagwriter.ui.settings.SettingsScreen
import com.liu.andy.demo.nfctagwriter.ui.deeplink.DeepLinkScreen
import com.liu.andy.demo.nfctagwriter.ui.deeplink.DeepLinkViewModel
import com.liu.andy.demo.nfctagwriter.ui.theme.NFCTagWriterTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.liu.andy.demo.nfctagwriter.nfc.NTag424Manager

class MainActivity : ComponentActivity() {
    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null
    private val nfcManager = NTag424Manager()
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var navController: androidx.navigation.NavController? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize NFC adapter
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        
        // Create pending intent for NFC
        val intent = Intent(this, javaClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_MUTABLE
        )
        
        setContent {
            NFCTagWriterTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen { navController ->
                        this@MainActivity.navController = navController
                    }
                }
            }
        }
        
        // Handle NFC intent if present (check both getIntent() and intent parameter)
        handleNfcIntent(intent)
        // Also check if we were launched with ACTION_VIEW (from background NFC tag)
        handleNfcIntent(getIntent())
    }
    
    override fun onResume() {
        super.onResume()
        // Enable foreground dispatch for NFC
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, null)
    }
    
    override fun onPause() {
        super.onPause()
        // Disable foreground dispatch for NFC
        nfcAdapter?.disableForegroundDispatch(this)
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent) // Important: update the intent so getIntent() returns the latest
        handleNfcIntent(intent)
    }
    
    private fun handleNfcIntent(intent: Intent) {
        // Check if this is an ACTION_VIEW intent with a URL (from NFC tag in background)
        if (Intent.ACTION_VIEW == intent.action) {
            val data = intent.data
            if (data != null) {
                val url = data.toString()
                Log.d("MainActivity", "Received ACTION_VIEW intent with URL: $url")
                // Navigate to DeepLinkScreen with the URL
                activityScope.launch {
                    var attempts = 0
                    while (navController == null && attempts < 50) {
                        kotlinx.coroutines.delay(100)
                        attempts++
                    }
                    val encodedUrl = android.net.Uri.encode(url, "")
                    navController?.navigate("${Screen.DeepLink.route}?url=$encodedUrl") {
                        popUpTo(Screen.NTag424.route) { inclusive = false }
                        launchSingleTop = true
                    } ?: run {
                        Log.w("MainActivity", "NavController not ready for ACTION_VIEW")
                    }
                }
                return
            }
        }
        
        // Handle NFC tag intents (foreground detection)
        if (NfcAdapter.ACTION_TAG_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_TECH_DISCOVERED == intent.action) {
            val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
            if (tag != null) {
                // Store tag in a way that can be accessed by the ViewModel
                NfcTagHolder.currentTag = tag
                Log.d("MainActivity", "Tag detected: ${tag.techList.joinToString()}")
                
                // Check if this is an NTAG424 tag (supports IsoDep or NfcA)
                val isNTAG424 = tag.techList.contains(android.nfc.tech.IsoDep::class.java.name) ||
                               tag.techList.contains(android.nfc.tech.NfcA::class.java.name)
                
                if (isNTAG424) {
                    // Read URL from NTAG424 tag and check if it matches Firewalla pattern
                    activityScope.launch {
                        try {
                            val result = nfcManager.readData(tag)
                            result.onSuccess { url ->
                                Log.d("MainActivity", "Read URL from tag: $url")
                                
                                // Check if URL matches Firewalla pattern (mesh.firewalla.net/nfc)
                                // Use regex to match the pattern more precisely
                                val firewallaPattern = Regex(
                                    "https?://(?:www\\.)?mesh\\.firewalla\\.net/nfc",
                                    RegexOption.IGNORE_CASE
                                )
                                val isFirewallaUrl = firewallaPattern.containsMatchIn(url)
                                
                                if (isFirewallaUrl) {
                                    // Wait for navController to be ready (with timeout)
                                    var attempts = 0
                                    while (navController == null && attempts < 50) {
                                        kotlinx.coroutines.delay(100)
                                        attempts++
                                    }
                                    // Navigate to DeepLinkScreen with the URL
                                    val encodedUrl = android.net.Uri.encode(url, "")
                                    navController?.navigate("${Screen.DeepLink.route}?url=$encodedUrl") {
                                        popUpTo(Screen.NTag424.route) { inclusive = false }
                                        launchSingleTop = true
                                    } ?: run {
                                        Log.w("MainActivity", "NavController not ready, storing URL for later navigation")
                                    }
                                } else {
                                    // Not a Firewalla URL, just store the tag for NTag424Screen
                                    Log.d("MainActivity", "URL does not match Firewalla pattern, tag stored for manual operations")
                                }
                            }.onFailure { exception ->
                                Log.e("MainActivity", "Failed to read tag: ${exception.message}")
                                // Even if read fails, store the tag so user can try manually
                            }
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Error reading tag: ${e.message}", e)
                            // Even if read fails, store the tag so user can try manually
                        }
                    }
                } else {
                    Toast.makeText(this, "Tag detected by MY app!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

// Helper object to hold the current NFC tag
// Using MutableState so Compose can track changes
object NfcTagHolder {
    var currentTagState = mutableStateOf<Tag?>(null)
    var currentTag: Tag?
        get() = currentTagState.value
        set(value) {
            currentTagState.value = value
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(onNavControllerReady: (androidx.navigation.NavController) -> Unit = {}) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    
    // Notify parent about navController
    androidx.compose.runtime.LaunchedEffect(navController) {
        onNavControllerReady(navController)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            NavigationBar {
                val items = listOf(
                    Screen.NTag424,
                    Screen.NTag21X,
                    Screen.Settings
                )
                items.forEach { screen ->
                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = when (screen) {
                                    is Screen.NTag424 -> Icons.Filled.Refresh
                                    is Screen.NTag21X -> Icons.Filled.Slideshow
                                    is Screen.Settings -> Icons.Filled.Settings
                                    else -> Icons.Filled.Settings
                                },
                                contentDescription = screen.route
                            )
                        },
                        label = { Text(screen.route.replaceFirstChar { it.uppercase() }) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.NTag424.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.NTag424.route) {
                val viewModel: NTag424ViewModel = viewModel()
                // Use the state value so LaunchedEffect can track changes
                val currentTag by NfcTagHolder.currentTagState
                
                // Update tag when screen is displayed or tag changes
                LaunchedEffect(currentTag) {
                    currentTag?.let { tag ->
                        Log.d("MainScreen", "Tag detected 2: ${tag.techList.joinToString()}")
                        viewModel.setCurrentTag(tag)
                    }
                }
                NTag424Screen(navController = navController, viewModel = viewModel)
            }
            composable(Screen.NTag21X.route) {
                val viewModel: NTag21XViewModel = viewModel()
                // Use the state value so LaunchedEffect can track changes
                val currentTag by NfcTagHolder.currentTagState
                
                // Update tag when screen is displayed or tag changes
                LaunchedEffect(currentTag) {
                    currentTag?.let { tag ->
                        viewModel.setCurrentTag(tag)
                    }
                }
                NTag21XScreen(navController = navController, viewModel = viewModel)
            }
            composable(Screen.Settings.route) {
                SettingsScreen()
            }
            composable(
                route = "${Screen.DeepLink.route}?url={url}",
                arguments = listOf(
                    navArgument("url") {
                        type = NavType.StringType
                        defaultValue = null
                        nullable = true
                    }
                )
            ) { backStackEntry ->
                val viewModel: DeepLinkViewModel = viewModel()
                val url = backStackEntry.arguments?.getString("url")
                DeepLinkScreen(
                    viewModel = viewModel,
                    navController = navController,
                    initialUrl = url
                )
            }
        }
    }
}

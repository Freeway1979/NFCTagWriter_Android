package com.liu.andy.demo.nfctagwriter

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
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
import com.liu.andy.demo.nfctagwriter.nfc.NTAG424Verifier
import com.liu.andy.demo.nfctagwriter.nfc.NTag424Manager
import com.liu.andy.demo.nfctagwriter.ui.deeplink.DeepLinkScreen
import com.liu.andy.demo.nfctagwriter.ui.deeplink.DeepLinkViewModel
import com.liu.andy.demo.nfctagwriter.ui.ntag21x.NTag21XScreen
import com.liu.andy.demo.nfctagwriter.ui.ntag21x.NTag21XViewModel
import com.liu.andy.demo.nfctagwriter.ui.ntag424.NTag424Action
import com.liu.andy.demo.nfctagwriter.ui.ntag424.NTag424ActionHandler
import com.liu.andy.demo.nfctagwriter.ui.ntag424.NTag424Screen
import com.liu.andy.demo.nfctagwriter.ui.ntag424.NTag424ViewModel
import com.liu.andy.demo.nfctagwriter.ui.settings.SettingsScreen
import com.liu.andy.demo.nfctagwriter.ui.theme.NFCTagWriterTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.bplearning.ntag424.DnaCommunicator
import net.bplearning.ntag424.card.KeyInfo
import net.bplearning.ntag424.card.KeySet
import net.bplearning.ntag424.command.ChangeFileSettings
import net.bplearning.ntag424.command.FileSettings
import net.bplearning.ntag424.command.GetCardUid
import net.bplearning.ntag424.command.GetFileSettings
import net.bplearning.ntag424.command.GetKeyVersion
import net.bplearning.ntag424.command.WriteData
import net.bplearning.ntag424.constants.Ntag424
import net.bplearning.ntag424.constants.Permissions
import net.bplearning.ntag424.encryptionmode.AESEncryptionMode
import net.bplearning.ntag424.sdm.NdefTemplateMaster
import net.bplearning.ntag424.sdm.SDMSettings
import net.bplearning.ntag424.util.ByteUtil
import net.bplearning.ntag424.util.ThrowableFunction
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.function.Consumer

class MainActivity : ComponentActivity() {
    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null
    private val nfcManager = NTag424Manager()
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var navController: androidx.navigation.NavController? = null

    companion object {
        val LOG_TAG = MainActivity::javaClass.name
    }
    
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
        if (BackgroundNfcHandler.isEnabled) {
            handleNfcIntent(getIntent())
        }
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
        Log.d(LOG_TAG, "onNewIntent ${intent.action}")
        handleNfcIntent(intent)
    }

    fun getKeySet(): KeySet {
        // NOTE - replace these with your own keys.
        //
        //        Any of the keys *can* be diversified
        //        if you don't use RandomID, but usually
        //        only the MAC key is diversified.
        val key0Str = "915565AB915565AB"
        val keySet = KeySet()
        keySet.setUsesLrp(false)

        // This is the "master" key
        val key0 = KeyInfo()
        key0.diversifyKeys = false
        key0.key =  key0Str.toByteArray() // Ntag424.FACTORY_KEY
        keySet.setKey(Permissions.ACCESS_KEY0, key0)

        // No standard usage
        val key1 = KeyInfo()
        key1.diversifyKeys = false
        key1.key = Ntag424.FACTORY_KEY
        keySet.setKey(Permissions.ACCESS_KEY1, key1)

        // Usually used as a meta read key for encrypted PICC data
        val key2 = KeyInfo()
        key2.diversifyKeys = false
        key2.key = Ntag424.FACTORY_KEY
        keySet.setKey(Permissions.ACCESS_KEY2, key2)

        // Usually used as the MAC and encryption key.
        // The MAC key usually has the diversification information setup.
        val key3 = KeyInfo()
        key3.diversifyKeys = true
        key3.systemIdentifier =
            "testing".toByteArray(StandardCharsets.UTF_8) // systemIdentifier is usually a hex-encoded string based on the name of your intended use.
        key3.version =
            1 // Since it is not a factory key (it is *based* on a factory key, but underwent diversification), need to set to a version number other than 0.
        key3.key = Ntag424.FACTORY_KEY

        // No standard usage
        keySet.setKey(Permissions.ACCESS_KEY3, key3)
        val key4 = KeyInfo()
        key4.diversifyKeys = false
        key4.key = Ntag424.FACTORY_KEY
        keySet.setKey(Permissions.ACCESS_KEY4, key4)

        // This is used for decoding, but documenting that key2/key3 are standard for meta and mac
        keySet.setMetaKey(Permissions.ACCESS_KEY2)
        keySet.setMacFileKey(Permissions.ACCESS_KEY3)

        return keySet
    }

    // This is the nitty-gritty
    fun communicateWithTag(tag: Tag?) {
        // IsoDep runs the communication with the tag
        val iso = IsoDep.get(tag)

        // Communication needs to be on its own thread
        Thread(Runnable {
            try {
                // Standard NFC connect
                iso.connect()

                // Initialize DNA library
                val communicator = DnaCommunicator()
                communicator.setTransceiver(ThrowableFunction { bytesToSend: ByteArray? ->
                    iso.transceive(
                        bytesToSend
                    )
                })
                communicator.setLogger(Consumer { info: String? ->
                    Log.d(
                        MainActivity.LOG_TAG,
                        "Communicator: " + info
                    )
                })
                communicator.beginCommunication()

                // Synchronize keys first
                val keySet = getKeySet()
                keySet.synchronizeKeys(communicator)

                // Authenticate with a key.  If you are in LRP mode (Requires permanently changing tag settings), uncomment the LRP version instead.
                // if(LRPEncryptionMode.authenticateLRP(communicator, 0, Constants.FACTORY_KEY)) {
                if (AESEncryptionMode.authenticateEV2(
                        communicator,
                        0,
                        keySet.getKey(0).key
                    )
                ) { // Assumes key0 is non-diversified
                    Log.d(MainActivity.LOG_TAG, "Login successful")
                    val cardUid = GetCardUid.run(communicator)
                    Log.d(MainActivity.LOG_TAG, "Card UID: " + ByteUtil.byteToHex(cardUid))
                    val keyVersion = GetKeyVersion.run(communicator, 0)
                    Log.d(MainActivity.LOG_TAG, "Key 0 version: " + keyVersion)

                    // Doing this will set LRP mode for all future authentications
                    // SetCapabilities.run(communicator, true);

                    // Get the NDEF file settings
                    val ndeffs = GetFileSettings.run(communicator, Ntag424.NDEF_FILE_NUMBER)

                    Log.d(MainActivity.LOG_TAG, "Debug NDEF: " + debugStringForFileSettings(ndeffs))

                    // Secret data
                    val secretData = byteArrayOf(
                        1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16
                    )

                    // Set the access keys and options
                    val sdmSettings = SDMSettings()
                    sdmSettings.sdmEnabled = true
                    sdmSettings.sdmMetaReadPerm =
                        Permissions.ACCESS_KEY2 // Set to a key to get encrypted PICC data (usually non-diversified since you don't know the UID until after decryption)
                    sdmSettings.sdmFileReadPerm =
                        Permissions.ACCESS_KEY3 // Used to create the MAC and Encrypt FileData
                    sdmSettings.sdmOptionUid = true
                    sdmSettings.sdmOptionReadCounter = true

                    // NDEF SDM formatter helper - uses a template to write SDMSettings and get file data
                    val master = NdefTemplateMaster()
                    master.usesLRP = false

                    val ndefRecord = master.generateNdefTemplateFromUrlString(
                        "https://freeway1979.github.io/nfc?gid=915565a3-65c7-4a2b-8629-194d80ed824b&rule=249&u={UID}&c={COUNTER}&m={MAC}",
//                        secretData,
                        sdmSettings
                    )

                    // This link (not by me) has a handy decoder if you are using factory keys (we are using a diversified factory key, so this will not work unless you change that in the keyset):
                    // byte[] ndefRecord = master.generateNdefTemplateFromUrlString("https://sdm.nfcdeveloper.com/tagpt?uid={UID}&ctr={COUNTER}&cmac={MAC}", sdmSettings);

                    // Write the record to the file
                    WriteData.run(communicator, Ntag424.NDEF_FILE_NUMBER, ndefRecord)

                    // Set the general NDEF permissions
                    ndeffs.readPerm = Permissions.ACCESS_EVERYONE
                    ndeffs.writePerm = Permissions.ACCESS_KEY0
                    ndeffs.readWritePerm = Permissions.ACCESS_KEY0 // backup key
                    ndeffs.changePerm = Permissions.ACCESS_KEY0
                    ndeffs.sdmSettings = sdmSettings // Use the SDM settings we just setup
                    Log.d(
                        MainActivity.LOG_TAG,
                        "New Ndef Settings: " + debugStringForFileSettings(ndeffs)
                    )
                    ChangeFileSettings.run(communicator, Ntag424.NDEF_FILE_NUMBER, ndeffs)

                    runOnUiThread({
                        Toast.makeText(this, "Tag Sync Successful", Toast.LENGTH_SHORT).show()
                    })
                } else {
                    Log.d(MainActivity.LOG_TAG, "Login unsuccessful")
                    runOnUiThread({
                        Toast.makeText(this, "Invalid Application Key", Toast.LENGTH_SHORT).show()
                    })
                }

                // We are done
                iso.close()
                Log.d(MainActivity.LOG_TAG, "Disconnected from tag")
            } catch (e: IOException) {
                Log.d(MainActivity.LOG_TAG, "error communicating", e)
                runOnUiThread({
                    Toast.makeText(this, "Error Communicating: Try again", Toast.LENGTH_SHORT)
                        .show()
                })
            }
        }).start()
    }


    // Make file permission/SDM settings easier to see in the logs
    fun debugStringForFileSettings(fs: FileSettings): String {
        val sb = StringBuilder()
        sb.append("= FileSettings =").append("\n")
        sb.append("fileType: ").append("n/a").append("\n") // todo expose get file type for DESFire
        sb.append("commMode: ").append(fs.commMode.toString()).append("\n")
        sb.append("accessRights RW:       ").append(fs.readWritePerm).append("\n")
        sb.append("accessRights CAR:      ").append(fs.changePerm).append("\n")
        sb.append("accessRights R:        ").append(fs.readPerm).append("\n")
        sb.append("accessRights W:        ").append(fs.writePerm).append("\n")
        sb.append("fileSize: ").append(fs.fileSize).append("\n")
        sb.append("= Secure Dynamic Messaging =").append("\n")
        sb.append("isSdmEnabled: ").append(fs.sdmSettings.sdmEnabled).append("\n")
        sb.append("isSdmOptionUid: ").append(fs.sdmSettings.sdmOptionUid).append("\n")
        sb.append("isSdmOptionReadCounter: ").append(fs.sdmSettings.sdmOptionReadCounter)
            .append("\n")
        sb.append("isSdmOptionReadCounterLimit: ").append(fs.sdmSettings.sdmOptionReadCounterLimit)
            .append("\n")
        sb.append("isSdmOptionEncryptFileData: ").append(fs.sdmSettings.sdmOptionEncryptFileData)
            .append("\n")
        sb.append("isSdmOptionUseAscii: ").append(fs.sdmSettings.sdmOptionUseAscii).append("\n")
        sb.append("sdmMetaReadPerm:             ").append(fs.sdmSettings.sdmMetaReadPerm)
            .append("\n")
        sb.append("sdmFileReadPerm:             ").append(fs.sdmSettings.sdmFileReadPerm)
            .append("\n")
        sb.append("sdmReadCounterRetrievalPerm: ")
            .append(fs.sdmSettings.sdmReadCounterRetrievalPerm).append("\n")
        sb.append("sdmUidOffset:         ").append(fs.sdmSettings.sdmUidOffset).append("\n")
        sb.append("sdmReadCounterOffset: ").append(fs.sdmSettings.sdmReadCounterOffset).append("\n")
        sb.append("sdmPiccDataOffset:    ").append(fs.sdmSettings.sdmPiccDataOffset).append("\n")
        sb.append("sdmMacInputOffset:    ").append(fs.sdmSettings.sdmMacInputOffset).append("\n")
        sb.append("sdmMacOffset:         ").append(fs.sdmSettings.sdmMacOffset).append("\n")
        sb.append("sdmEncOffset:         ").append(fs.sdmSettings.sdmEncOffset).append("\n")
        sb.append("sdmEncLength:         ").append(fs.sdmSettings.sdmEncLength).append("\n")
        sb.append("sdmReadCounterLimit:  ").append(fs.sdmSettings.sdmReadCounterLimit).append("\n")
        return sb.toString()
    }
    
    private fun handleNfcIntent(intent: Intent) {
        val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
        if (tag != null) {
            // Store tag in a way that can be accessed by the ViewModel
            NfcTagHolder.currentTag = tag
        }
        // Check if this is an ACTION_VIEW intent with a URL (from NFC tag in background)
        if (BackgroundNfcHandler.isEnabled) {
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
        } else {
            // Handle NFC tag intents (foreground detection)
            
            if (NfcAdapter.ACTION_TAG_DISCOVERED == intent.action ||
                NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action ||
                NfcAdapter.ACTION_TECH_DISCOVERED == intent.action
            ) {
                val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
                if (tag != null) {
                    // Check if there's an active action - if so, skip processing
                    if (NTag424ActionHandler.currentAction != null) {
                        Log.d("MainActivity", "Skipping tag processing - active action: ${NTag424ActionHandler.currentAction}")
                        return
                    }
                    
                    // Store tag in a way that can be accessed by the ViewModel
                    NfcTagHolder.currentTag = tag
                    Log.d("MainActivity", "Tag detected: ${tag.techList.joinToString()}")
                    // Check if this is an NTAG424 tag (supports IsoDep)
                    val isNTAG424 = tag.techList.contains(android.nfc.tech.IsoDep::class.java.name)

                    if (isNTAG424) {
                        // Read URL from NTAG424 tag and check if it matches Firewalla pattern
                        activityScope.launch {
                            try {
                                val result = nfcManager.readData(tag)
                                result.onSuccess { url ->
                                    Log.d("MainActivity", "Read URL from tag: $url")
                                    // KEY3 base key: FACTORY_KEY (all zeros)
                                    val key3BaseKey = Ntag424.FACTORY_KEY // All zeros
                                    val verifier = NTAG424Verifier(
                                        key3BaseKey = key3BaseKey,
                                    )
                                    val isValid = verifier.verifySDMMAC(url)
                                    Log.d(
                                        "MainActivity",
                                        "SDM MAC verification: ${if (isValid) "✅ PASSED" else "❌ FAILED"}"
                                    )
                                    // Check if URL matches Firewalla pattern (mesh.firewalla.net/nfc)
                                    // Use regex to match the pattern more precisely
                                    val firewallaPattern = Regex(
                                        NTAG424Verifier.nfcUrlPattern,
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
                                            Log.w(
                                                "MainActivity",
                                                "NavController not ready, storing URL for later navigation"
                                            )
                                        }
                                    } else {
                                        // Not a Firewalla URL, just store the tag for NTag424Screen
                                        Log.d(
                                            "MainActivity",
                                            "URL does not match Firewalla pattern, tag stored for manual operations"
                                        )
                                    }
                                }.onFailure { exception ->
                                    Log.e(
                                        "MainActivity",
                                        "Failed to read tag: ${exception.message}"
                                    )
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
}

// Object to store background NFC intent handling state
object BackgroundNfcHandler {
    var isEnabled: Boolean = false
        private set
    
    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        Log.d("BackgroundNfcHandler", "Background NFC intent handling: ${if (enabled) "ENABLED" else "DISABLED"}")
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

    // State for background NFC intent handling toggle
    var backgroundNfcEnabled by remember { mutableStateOf(BackgroundNfcHandler.isEnabled) }
    
    // Sync with BackgroundNfcHandler when it changes
    LaunchedEffect(backgroundNfcEnabled) {
        BackgroundNfcHandler.setEnabled(backgroundNfcEnabled)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    // Background NFC Intent Toggle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Nfc,
                            contentDescription = "Background NFC",
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        Text(
                            text = "Background",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        Switch(
                            checked = backgroundNfcEnabled,
                            onCheckedChange = { backgroundNfcEnabled = it }
                        )
                    }
                }
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

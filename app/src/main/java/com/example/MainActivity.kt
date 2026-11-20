package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.CallDirection
import com.example.data.ContactRuleType
import com.example.data.RecordingEntity
import com.example.service.RecordingService
import com.example.telecom.DeviceCompatHelper
import com.example.ui.RecordingViewModel
import com.example.ui.components.AddContactRuleDialog
import com.example.ui.components.AppLockScreen
import com.example.ui.components.BackupConsentDialog
import com.example.ui.components.CallForwardingDialog
import com.example.ui.components.CompatibilityDetailsDialog
import com.example.ui.components.ConsentDialog
import com.example.ui.components.DeleteConfirmDialog
import com.example.ui.components.RenameRecordingDialog
import com.example.ui.components.SetPinDialog
import com.example.ui.screens.CallHistoryScreen
import com.example.ui.screens.ContactsScreen
import com.example.ui.screens.DialerScreen
import com.example.ui.screens.RecordingsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.NarendraCallRecorderTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable

@Serializable
data object DialerRoute

@Serializable
data object HistoryRoute

@Serializable
data object ContactsRoute

@Serializable
data object RecordingsRoute

@Serializable
data object SettingsRoute

class MainActivity : FragmentActivity() {

    private val pendingDialUriNumber = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        RecordingService.ensureNotificationChannels(this)
        handleIncomingIntent(intent)

        setContent {
            val viewModel: RecordingViewModel = viewModel()
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val isAppLocked by viewModel.isAppLocked.collectAsStateWithLifecycle()
            val pendingDial by pendingDialUriNumber.collectAsStateWithLifecycle()

            NarendraCallRecorderTheme(darkTheme = settings.darkModeEnabled) {
                val biometricAvailable = remember { checkBiometricHardwareAvailable() }

                val triggerBiometric: () -> Unit = remember(viewModel) {
                    {
                        showBiometricPrompt(
                            onSuccess = { viewModel.unlockWithBiometrics() },
                            onError = { /* User can fall back to PIN or retry */ }
                        )
                    }
                }

                LaunchedEffect(isAppLocked, settings.biometricLockEnabled) {
                    if (isAppLocked && settings.biometricLockEnabled) {
                        triggerBiometric()
                    }
                }

                if (isAppLocked) {
                    AppLockScreen(
                        pinEnabled = settings.pinLockEnabled,
                        biometricEnabled = settings.biometricLockEnabled && biometricAvailable,
                        onVerifyPin = viewModel::verifyPin,
                        onTriggerBiometric = triggerBiometric
                    )
                } else {
                    MainAppShell(
                        viewModel = viewModel,
                        isBiometricAvailable = biometricAvailable,
                        pendingDialNumber = pendingDial,
                        onConsumePendingDial = { pendingDialUriNumber.value = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action == RecordingService.ACTION_START_AUTO_CALL) {
            val dirName = intent.getStringExtra(RecordingService.EXTRA_DIRECTION)
                ?: CallDirection.INCOMING.name
            val direction = runCatching { CallDirection.valueOf(dirName) }
                .getOrDefault(CallDirection.INCOMING)
            val number = intent.getStringExtra(RecordingService.EXTRA_PHONE_NUMBER).orEmpty()
            val contactName = intent.getStringExtra(RecordingService.EXTRA_CONTACT_NAME).orEmpty()
            intent.action = null
            RecordingService.startAutoCallRecording(
                context = this,
                direction = direction,
                phoneNumber = number,
                contactName = contactName
            )
            return
        }

        val action = intent.action
        if (action == Intent.ACTION_DIAL || action == Intent.ACTION_VIEW) {
            val uri = intent.data
            if (uri != null && uri.scheme == "tel") {
                val ssp = uri.schemeSpecificPart.orEmpty()
                if (ssp.isNotBlank()) {
                    pendingDialUriNumber.value = Uri.decode(ssp)
                }
            }
        }
    }

    private fun checkBiometricHardwareAvailable(): Boolean {
        val bm = BiometricManager.from(this)
        val result = bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun showBiometricPrompt(
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onError(errString.toString())
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.app_name))
            .setSubtitle("Authenticate to access Narendra Phone")
            .setNegativeButtonText("Use PIN / Cancel")
            .build()
        prompt.authenticate(info)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainAppShell(
    viewModel: RecordingViewModel,
    isBiometricAvailable: Boolean,
    pendingDialNumber: String?,
    onConsumePendingDial: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val stats by viewModel.dashboardStats.collectAsStateWithLifecycle()
    val dialNumber by viewModel.dialNumber.collectAsStateWithLifecycle()
    val dialSuggestedContacts by viewModel.dialSuggestedContacts.collectAsStateWithLifecycle()
    val filteredCallLogs by viewModel.filteredCallLogs.collectAsStateWithLifecycle()
    val callLogFilter by viewModel.callLogFilter.collectAsStateWithLifecycle()
    val callLogSearch by viewModel.callLogSearch.collectAsStateWithLifecycle()
    val filteredContacts by viewModel.filteredContacts.collectAsStateWithLifecycle()
    val contactSearch by viewModel.contactSearch.collectAsStateWithLifecycle()
    val allRecordings by viewModel.allRecordings.collectAsStateWithLifecycle()
    val filteredRecordings by viewModel.filteredRecordings.collectAsStateWithLifecycle()
    val contactRules by viewModel.contactRules.collectAsStateWithLifecycle()
    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val compatibility by viewModel.compatibility.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val sortOption by viewModel.sortOption.collectAsStateWithLifecycle()
    val directionFilter by viewModel.directionFilter.collectAsStateWithLifecycle()
    val uiMessage by viewModel.uiMessage.collectAsStateWithLifecycle()
    val carrierStatusMessage by viewModel.carrierStatusMessage.collectAsStateWithLifecycle()
    val isBackupBusy by viewModel.isBackupBusy.collectAsStateWithLifecycle()

    var showCompatibilityDialog by remember { mutableStateOf(false) }
    var showConsentDialogManual by remember { mutableStateOf(false) }
    var showCallForwardingDialog by remember { mutableStateOf(false) }
    var recordingToRename by remember { mutableStateOf<RecordingEntity?>(null) }
    var recordingToDelete by remember { mutableStateOf<RecordingEntity?>(null) }
    var showDeleteAllConfirm by remember { mutableStateOf(false) }
    var showClearLogsConfirm by remember { mutableStateOf(false) }
    var showResetAppDataConfirm by remember { mutableStateOf(false) }
    var addRuleDialogType by remember { mutableStateOf<ContactRuleType?>(null) }
    var pendingPickRuleType by remember { mutableStateOf<ContactRuleType?>(null) }
    var showSetPinDialog by remember { mutableStateOf(false) }
    var backupConsentExport by remember { mutableStateOf<Boolean?>(null) }

    val runtimePermissions = remember {
        buildList {
            add(Manifest.permission.CALL_PHONE)
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.READ_PHONE_STATE)
            add(Manifest.permission.READ_CALL_LOG)
            add(Manifest.permission.WRITE_CALL_LOG)
            add(Manifest.permission.READ_CONTACTS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshCompatibilityAndData()
    }

    val defaultDialerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshCompatibilityAndData()
    }

    val requestDefaultDialer: () -> Unit = {
        val intent = DeviceCompatHelper.createDefaultDialerIntent(context)
        if (intent != null) {
            defaultDialerLauncher.launch(intent)
        } else {
            DeviceCompatHelper.openOemAutoStartSettings(context)
        }
    }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.exportBackup(uri)
        }
    }

    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.restoreBackup(uri)
        }
    }

    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { contactUri: Uri? ->
        val ruleType = pendingPickRuleType ?: ContactRuleType.INCLUDE
        pendingPickRuleType = null
        if (contactUri != null) {
            val hasContacts = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
            if (hasContacts) {
                extractContactPhoneAndName(context, contactUri)?.let { (number, name) ->
                    viewModel.addContactRule(number, name, ruleType)
                }
            } else {
                permissionLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS))
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshCompatibilityAndData()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val navigateTab: (Any) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(DialerRoute) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    LaunchedEffect(pendingDialNumber) {
        if (!pendingDialNumber.isNullOrBlank()) {
            viewModel.setDialNumber(pendingDialNumber)
            navigateTab(DialerRoute)
            onConsumePendingDial()
        }
    }

    LaunchedEffect(uiMessage, playbackState.errorMessage) {
        val msg = uiMessage ?: playbackState.errorMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeMessage()
        }
    }

    val currentDest = backStackEntry?.destination

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        NavigationBarItem(
                            selected = currentDest?.hasRoute<DialerRoute>() == true,
                            onClick = { navigateTab(DialerRoute) },
                            icon = {
                                Icon(
                                    imageVector = if (currentDest?.hasRoute<DialerRoute>() == true) {
                                        Icons.Filled.Dialpad
                                    } else {
                                        Icons.Outlined.Dialpad
                                    },
                                    contentDescription = stringResource(R.string.nav_dialer)
                                )
                            },
                            label = {
                                Text(
                                    text = stringResource(R.string.nav_dialer),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            modifier = Modifier.testTag("nav_dialer_tab")
                        )
                        NavigationBarItem(
                            selected = currentDest?.hasRoute<HistoryRoute>() == true,
                            onClick = { navigateTab(HistoryRoute) },
                            icon = {
                                Icon(
                                    imageVector = if (currentDest?.hasRoute<HistoryRoute>() == true) {
                                        Icons.Filled.History
                                    } else {
                                        Icons.Outlined.History
                                    },
                                    contentDescription = stringResource(R.string.nav_history)
                                )
                            },
                            label = {
                                Text(
                                    text = stringResource(R.string.nav_history),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            modifier = Modifier.testTag("nav_history_tab")
                        )
                        NavigationBarItem(
                            selected = currentDest?.hasRoute<ContactsRoute>() == true,
                            onClick = { navigateTab(ContactsRoute) },
                            icon = {
                                Icon(
                                    imageVector = if (currentDest?.hasRoute<ContactsRoute>() == true) {
                                        Icons.Filled.Contacts
                                    } else {
                                        Icons.Outlined.Contacts
                                    },
                                    contentDescription = stringResource(R.string.nav_contacts)
                                )
                            },
                            label = {
                                Text(
                                    text = stringResource(R.string.nav_contacts),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            modifier = Modifier.testTag("nav_contacts_tab")
                        )
                        NavigationBarItem(
                            selected = currentDest?.hasRoute<RecordingsRoute>() == true,
                            onClick = { navigateTab(RecordingsRoute) },
                            icon = {
                                Icon(
                                    imageVector = if (currentDest?.hasRoute<RecordingsRoute>() == true) {
                                        Icons.Filled.LibraryMusic
                                    } else {
                                        Icons.Outlined.LibraryMusic
                                    },
                                    contentDescription = stringResource(R.string.nav_recordings)
                                )
                            },
                            label = {
                                Text(
                                    text = stringResource(R.string.nav_recordings),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            modifier = Modifier.testTag("nav_recordings_tab")
                        )
                        NavigationBarItem(
                            selected = currentDest?.hasRoute<SettingsRoute>() == true,
                            onClick = { navigateTab(SettingsRoute) },
                            icon = {
                                Icon(
                                    imageVector = if (currentDest?.hasRoute<SettingsRoute>() == true) {
                                        Icons.Filled.Settings
                                    } else {
                                        Icons.Outlined.Settings
                                    },
                                    contentDescription = stringResource(R.string.nav_settings)
                                )
                            },
                            label = {
                                Text(
                                    text = stringResource(R.string.nav_settings),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            modifier = Modifier.testTag("nav_settings_tab")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        NavigationRailItem(
                            selected = currentDest?.hasRoute<DialerRoute>() == true,
                            onClick = { navigateTab(DialerRoute) },
                            icon = { Icon(Icons.Filled.Dialpad, contentDescription = stringResource(R.string.nav_dialer)) },
                            label = { Text(stringResource(R.string.nav_dialer)) }
                        )
                        NavigationRailItem(
                            selected = currentDest?.hasRoute<HistoryRoute>() == true,
                            onClick = { navigateTab(HistoryRoute) },
                            icon = { Icon(Icons.Filled.History, contentDescription = stringResource(R.string.nav_history)) },
                            label = { Text(stringResource(R.string.nav_history)) }
                        )
                        NavigationRailItem(
                            selected = currentDest?.hasRoute<ContactsRoute>() == true,
                            onClick = { navigateTab(ContactsRoute) },
                            icon = { Icon(Icons.Filled.Contacts, contentDescription = stringResource(R.string.nav_contacts)) },
                            label = { Text(stringResource(R.string.nav_contacts)) }
                        )
                        NavigationRailItem(
                            selected = currentDest?.hasRoute<RecordingsRoute>() == true,
                            onClick = { navigateTab(RecordingsRoute) },
                            icon = { Icon(Icons.Filled.LibraryMusic, contentDescription = stringResource(R.string.nav_recordings)) },
                            label = { Text(stringResource(R.string.nav_recordings)) }
                        )
                        NavigationRailItem(
                            selected = currentDest?.hasRoute<SettingsRoute>() == true,
                            onClick = { navigateTab(SettingsRoute) },
                            icon = { Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.nav_settings)) },
                            label = { Text(stringResource(R.string.nav_settings)) }
                        )
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    NavHost(
                        navController = navController,
                        startDestination = DialerRoute
                    ) {
                        composable<DialerRoute> {
                            DialerScreen(
                                dialNumber = dialNumber,
                                suggestedContacts = dialSuggestedContacts,
                                settings = settings,
                                compatibility = compatibility,
                                activeSession = activeSession,
                                onDigitPress = viewModel::appendDialDigit,
                                onZeroLongPress = viewModel::replaceLastZeroWithPlus,
                                onBackspace = viewModel::backspaceDial,
                                onClearAll = viewModel::clearDial,
                                onSelectContact = { contact ->
                                    viewModel.setDialNumber(contact.number)
                                },
                                onCallClick = {
                                    if (!compatibility.hasCallPhonePermission) {
                                        permissionLauncher.launch(runtimePermissions)
                                    }
                                    viewModel.placeCall()
                                },
                                onRequestDefaultDialer = requestDefaultDialer,
                                onToggleAutoRecord = { enabled ->
                                    if (enabled && (!compatibility.hasRecordAudioPermission || !compatibility.hasPhoneStatePermission)) {
                                        permissionLauncher.launch(runtimePermissions)
                                    }
                                    viewModel.setAutoRecordEnabled(enabled)
                                },
                                onStartManualRecording = {
                                    if (compatibility.hasRecordAudioPermission) {
                                        viewModel.startManualRecording()
                                    } else {
                                        permissionLauncher.launch(runtimePermissions)
                                    }
                                },
                                onPauseRecording = viewModel::pauseRecording,
                                onResumeRecording = viewModel::resumeRecording,
                                onStopRecording = viewModel::stopRecording
                            )
                        }

                        composable<HistoryRoute> {
                            CallHistoryScreen(
                                callLogs = filteredCallLogs,
                                allRecordings = allRecordings,
                                hasCallLogPermission = compatibility.hasCallLogPermission,
                                filterType = callLogFilter,
                                searchQuery = callLogSearch,
                                onSearchChange = viewModel::setCallLogSearch,
                                onFilterChange = viewModel::setCallLogFilter,
                                onRequestCallLogPermission = { permissionLauncher.launch(runtimePermissions) },
                                onRefresh = viewModel::refreshDeviceData,
                                onCallNumber = { number ->
                                    viewModel.setDialNumber(number)
                                    if (!compatibility.hasCallPhonePermission) {
                                        permissionLauncher.launch(runtimePermissions)
                                    }
                                    viewModel.placeCall(number)
                                },
                                onDeleteEntry = viewModel::deleteCallLogEntry,
                                onClearAllLogs = { showClearLogsConfirm = true },
                                onOpenRecording = { recording ->
                                    viewModel.playOrToggle(recording)
                                    navigateTab(RecordingsRoute)
                                }
                            )
                        }

                        composable<ContactsRoute> {
                            ContactsScreen(
                                contacts = filteredContacts,
                                hasContactsPermission = compatibility.hasContactsPermission,
                                searchQuery = contactSearch,
                                onSearchChange = viewModel::setContactSearch,
                                onRequestContactsPermission = { permissionLauncher.launch(runtimePermissions) },
                                onRefresh = viewModel::refreshDeviceData,
                                onCallContact = { contact ->
                                    viewModel.setDialNumber(contact.number)
                                    if (!compatibility.hasCallPhonePermission) {
                                        permissionLauncher.launch(runtimePermissions)
                                    }
                                    viewModel.placeCall(contact.number)
                                }
                            )
                        }

                        composable<RecordingsRoute> {
                            RecordingsScreen(
                                recordings = filteredRecordings,
                                totalStorageBytes = stats.storageUsedBytes,
                                searchQuery = searchQuery,
                                sortOption = sortOption,
                                directionFilter = directionFilter,
                                playbackState = playbackState,
                                onSearchChange = viewModel::updateSearchQuery,
                                onSortChange = viewModel::updateSortOption,
                                onDirectionFilterChange = viewModel::updateDirectionFilter,
                                onPlayToggle = viewModel::playOrToggle,
                                onSeekPlayback = viewModel::seekPlayback,
                                onRenameRecording = { recordingToRename = it },
                                onShareRecording = { rec ->
                                    viewModel.buildShareIntent(rec)?.let { context.startActivity(it) }
                                },
                                onDeleteRecording = { recordingToDelete = it }
                            )
                        }

                        composable<SettingsRoute> {
                            SettingsScreen(
                                settings = settings,
                                contactRules = contactRules,
                                compatibility = compatibility,
                                carrierStatusMessage = carrierStatusMessage,
                                storageUsedBytes = stats.storageUsedBytes,
                                isBackupBusy = isBackupBusy,
                                isBiometricHardwareAvailable = isBiometricAvailable,
                                onRequestDefaultDialer = requestDefaultDialer,
                                onOpenBatteryOptimization = {
                                    DeviceCompatHelper.openBatteryOptimizationSettings(context)
                                },
                                onOpenOemAutoStart = {
                                    DeviceCompatHelper.openOemAutoStartSettings(context)
                                },
                                onOpenSystemCallSettings = {
                                    DeviceCompatHelper.openSystemCallSettings(context)
                                },
                                onCallWaitingChange = { enable ->
                                    if (!compatibility.hasCallPhonePermission) {
                                        permissionLauncher.launch(runtimePermissions)
                                    }
                                    viewModel.setCallWaiting(enable)
                                },
                                onCheckCallWaitingStatus = {
                                    if (!compatibility.hasCallPhonePermission) {
                                        permissionLauncher.launch(runtimePermissions)
                                    }
                                    viewModel.checkCallWaitingStatus()
                                },
                                onOpenCallForwardingDialog = { showCallForwardingDialog = true },
                                onCancelAllCallForwarding = {
                                    if (!compatibility.hasCallPhonePermission) {
                                        permissionLauncher.launch(runtimePermissions)
                                    }
                                    viewModel.cancelAllCallForwarding()
                                },
                                onToggleAutoRecord = { enabled ->
                                    if (enabled && (!compatibility.hasRecordAudioPermission || !compatibility.hasPhoneStatePermission)) {
                                        permissionLauncher.launch(runtimePermissions)
                                    }
                                    viewModel.setAutoRecordEnabled(enabled)
                                },
                                onToggleAutoIncoming = viewModel::setAutoRecordIncoming,
                                onToggleAutoOutgoing = viewModel::setAutoRecordOutgoing,
                                onSelectFilterMode = viewModel::setFilterMode,
                                onAddContactRuleClick = { type -> addRuleDialogType = type },
                                onDeleteContactRule = viewModel::deleteContactRule,
                                onSelectFormat = viewModel::setAudioFormat,
                                onSelectQuality = viewModel::setAudioQuality,
                                onTogglePreferTwoWay = viewModel::setPreferTwoWayAttempt,
                                onToggleDarkMode = viewModel::setDarkModeEnabled,
                                onTogglePinLock = { enable ->
                                    if (enable) {
                                        showSetPinDialog = true
                                    } else {
                                        viewModel.disablePinLock()
                                    }
                                },
                                onToggleBiometricLock = viewModel::setBiometricLockEnabled,
                                onLockNow = viewModel::lockNowIfConfigured,
                                onToggleRecordingNotif = viewModel::setShowRecordingNotification,
                                onToggleSavedNotif = viewModel::setShowSavedNotification,
                                onShowCompatibilityDialog = { showCompatibilityDialog = true },
                                onShowConsentDialog = { showConsentDialogManual = true },
                                onRequestExportBackup = { backupConsentExport = true },
                                onRequestRestoreBackup = { backupConsentExport = false },
                                onRequestDeleteAll = { showDeleteAllConfirm = true },
                                onRequestResetAppData = { showResetAppDataConfirm = true }
                            )
                        }
                    }
                }
            }
        }
    }

    if (!settings.consentAccepted || showConsentDialogManual) {
        ConsentDialog(
            onAccept = {
                viewModel.acceptConsent()
                showConsentDialogManual = false
            },
            onDismiss = if (settings.consentAccepted) {
                { showConsentDialogManual = false }
            } else {
                null
            }
        )
    }

    if (showCompatibilityDialog) {
        CompatibilityDetailsDialog(
            status = compatibility,
            onRequestPermissions = { permissionLauncher.launch(runtimePermissions) },
            onRequestDefaultDialer = requestDefaultDialer,
            onOpenAutoStart = { DeviceCompatHelper.openOemAutoStartSettings(context) },
            onDismiss = { showCompatibilityDialog = false }
        )
    }

    if (showCallForwardingDialog) {
        CallForwardingDialog(
            carrierStatusMessage = carrierStatusMessage,
            onActivate = { cond, number ->
                if (!compatibility.hasCallPhonePermission) {
                    permissionLauncher.launch(runtimePermissions)
                }
                viewModel.activateCallForwarding(cond, number)
            },
            onDeactivate = viewModel::deactivateCallForwarding,
            onCheckStatus = viewModel::checkCallForwardingStatus,
            onCancelAll = viewModel::cancelAllCallForwarding,
            onDismiss = { showCallForwardingDialog = false }
        )
    }

    recordingToRename?.let { target ->
        RenameRecordingDialog(
            initialTitle = target.title,
            onConfirm = { newTitle ->
                viewModel.renameRecording(target.id, newTitle)
                recordingToRename = null
            },
            onDismiss = { recordingToRename = null }
        )
    }

    recordingToDelete?.let { target ->
        DeleteConfirmDialog(
            title = "Delete Recording?",
            message = "Delete \"${target.title}\" permanently from device storage?",
            onConfirm = {
                viewModel.deleteRecording(target)
                recordingToDelete = null
            },
            onDismiss = { recordingToDelete = null }
        )
    }

    if (showDeleteAllConfirm) {
        DeleteConfirmDialog(
            title = "Delete All Recordings?",
            message = "This will permanently remove all audio files stored by Narendra Phone.",
            onConfirm = {
                viewModel.deleteAllRecordings()
                showDeleteAllConfirm = false
            },
            onDismiss = { showDeleteAllConfirm = false }
        )
    }

    if (showClearLogsConfirm) {
        DeleteConfirmDialog(
            title = "Clear Call History?",
            message = "This will permanently delete all entries from your device Call Log.",
            confirmLabel = "Clear Logs",
            onConfirm = {
                viewModel.clearAllCallLogs()
                showClearLogsConfirm = false
            },
            onDismiss = { showClearLogsConfirm = false }
        )
    }

    if (showResetAppDataConfirm) {
        DeleteConfirmDialog(
            title = "Reset All App Data?",
            message = "This deletes all local recordings, contact rules, disables auto-record, and resets PIN/fingerprint lock settings.",
            confirmLabel = "Reset All",
            onConfirm = {
                viewModel.resetAllAppData()
                showResetAppDataConfirm = false
            },
            onDismiss = { showResetAppDataConfirm = false }
        )
    }

    addRuleDialogType?.let { type ->
        AddContactRuleDialog(
            ruleType = type,
            onPickFromContacts = {
                pendingPickRuleType = type
                contactPickerLauncher.launch(null)
            },
            onAddManual = { number, name ->
                viewModel.addContactRule(number, name, type)
                addRuleDialogType = null
            },
            onDismiss = { addRuleDialogType = null }
        )
    }

    if (showSetPinDialog) {
        SetPinDialog(
            onConfirmPin = { pin ->
                viewModel.enablePinLock(pin)
                showSetPinDialog = false
            },
            onDismiss = { showSetPinDialog = false }
        )
    }

    backupConsentExport?.let { isExport ->
        BackupConsentDialog(
            isExport = isExport,
            onConfirm = {
                backupConsentExport = null
                if (isExport) {
                    val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                    exportBackupLauncher.launch("NarendraPhone_Backup_$stamp.zip")
                } else {
                    restoreBackupLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                }
            },
            onDismiss = { backupConsentExport = null }
        )
    }
}

private fun extractContactPhoneAndName(
    context: android.content.Context,
    contactUri: Uri
): Pair<String, String>? {
    return runCatching {
        context.contentResolver.query(
            contactUri,
            arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val id = cursor.getString(0) ?: return@use null
            val name = cursor.getString(1) ?: ""
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                arrayOf(id),
                null
            )?.use { phoneCursor ->
                if (phoneCursor.moveToFirst()) {
                    val phone = phoneCursor.getString(0) ?: ""
                    if (phone.isNotBlank()) Pair(phone, name) else null
                } else {
                    null
                }
            }
        }
    }.getOrNull()
}

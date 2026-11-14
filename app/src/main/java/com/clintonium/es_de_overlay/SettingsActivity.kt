package com.clintonium.es_de_overlay

import android.Manifest
import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.DocumentsContract
import android.provider.Settings
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.clintonium.es_de_overlay.core.LookSource
import com.clintonium.es_de_overlay.core.Overwrite
import com.clintonium.es_de_overlay.core.RESERVED_KEYS
import com.clintonium.es_de_overlay.core.mediaDirectory
import com.clintonium.es_de_overlay.core.reportsScreensaver
import rikka.shizuku.Shizuku

class SettingsActivity : ComponentActivity() {
    private var refresh by mutableIntStateOf(0)
    private var autoGrant = false

    // Opened by a capture just to grant consent: go back to the game afterwards.
    private var grantOnly = false

    // The permission the dialog was last launched for: its result only says whether it was granted.
    private var asked = ""

    // While set, the next controller button pressed becomes the menu button; [refused] says why the last one wasn't.
    private var learning by mutableStateOf(false)
    private var refused by mutableStateOf<String?>(null)

    // Shizuku's row changes while settings are open: Shizuku starts or stops, or its permission dialog is answered.
    private val shizukuStarted = Shizuku.OnBinderReceivedListener { refresh++ }
    private val shizukuStopped = Shizuku.OnBinderDeadListener { refresh++ }
    private val shizukuAnswered = Shizuku.OnRequestPermissionResultListener { _, _ -> refresh++ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Shizuku.addBinderReceivedListenerSticky(shizukuStarted)
        Shizuku.addBinderDeadListener(shizukuStopped)
        Shizuku.addRequestPermissionResultListener(shizukuAnswered)
        autoGrant = intent.getBooleanExtra(EXTRA_GRANT, false)
        grantOnly = autoGrant
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) { Screen() }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        autoGrant = intent.getBooleanExtra(EXTRA_GRANT, false)
        grantOnly = autoGrant
        refresh++
    }

    override fun onResume() {
        super.onResume()
        refresh++
    }

    /**
     * While [learning], takes the controller's buttons: the first one the menu can use becomes the menu button. Here
     * rather than in Compose, which moves focus on the D-pad before anything else sees it.
     */
    // Activity.dispatchKeyEvent is public; androidx marks only its own override of it as restricted.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!learning || !event.isFromSource(InputDevice.SOURCE_GAMEPAD)) return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            if (event.keyCode in RESERVED_KEYS) {
                refused = refusal(keyName(event.keyCode))
            } else {
                Prefs(this).menuKey = event.keyCode
                learning = false
                refresh++
            }
        }
        return true
    }

    /** While [learning], a D-pad that reports as a hat axis (the Nova's) is refused as one sending keys would be. */
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (!learning || !event.isFromSource(InputDevice.SOURCE_JOYSTICK)) return super.dispatchGenericMotionEvent(event)
        if (event.getAxisValue(MotionEvent.AXIS_HAT_X) != 0f || event.getAxisValue(MotionEvent.AXIS_HAT_Y) != 0f) refused = refusal("The D-pad")
        return true
    }

    private fun refusal(button: String) = "$button can't be the menu button: the menu, the viewer or Home use it. Press another."

    override fun onDestroy() {
        Shizuku.removeBinderReceivedListener(shizukuStarted)
        Shizuku.removeBinderDeadListener(shizukuStopped)
        Shizuku.removeRequestPermissionResultListener(shizukuAnswered)
        super.onDestroy()
    }

    @Composable
    private fun Screen() {
        // Reading the counter subscribes this screen, so prefs and permission states are re-read after each bump.
        val tick = refresh
        val prefs = remember { Prefs(this) }
        val esde = prefs.esdeTree
        val roms = prefs.romsTree
        // SAF lookups: once per bump, not on every recomposition.
        val esdeWarning = remember(tick, esde) { esde?.let(::esdeWarning) }
        val romsWarning = remember(tick, esde, roms) { if (esde != null && roms != null) romsWarning(esde, roms) else null }
        var mediaType by remember { mutableStateOf(prefs.mediaType) }
        var menuImageType by remember { mutableStateOf(prefs.menuImageType) }
        var clip by remember { mutableIntStateOf(prefs.clipSeconds) }
        var textScale by remember { mutableIntStateOf(prefs.textScale) }
        var overwrite by remember { mutableStateOf(prefs.overwrite) }
        var bootStart by remember { mutableStateOf(prefs.bootStart) }
        var sleepWhenIdle by remember { mutableStateOf(prefs.sleepWhenIdle) }
        var sleepMinutes by remember { mutableIntStateOf(prefs.sleepMinutes) }
        var quitOnHome by remember { mutableStateOf(prefs.quitOnHome) }
        var confirmQuit by remember { mutableStateOf(prefs.confirmQuit) }
        var systemScreenshots by remember { mutableStateOf(prefs.systemScreenshots) }
        val shizuku = remember(tick) { ShizukuShell.state(this) }
        val screensaverReported = remember(tick, esde) { esde?.let(::esdeReportsScreensaver) }
        // No game, so no system: the theme's own theme.xml.
        val lookSource = remember(tick, esde, mediaType, menuImageType) { ThemeLook(this, prefs).current("", prefs.menuImage).look.source }

        val pickEsde = treePicker { prefs.esdeTree = it }
        val pickRoms = treePicker { prefs.romsTree = it }
        val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            // Once Android stops showing the dialog (after repeated denials), App Info is the only place left to enable it.
            if (!ok && !shouldShowRequestPermissionRationale(asked)) {
                openSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, withPackage = true)
            }
            this.refresh++
        }
        val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            val data = r.data
            if (r.resultCode == RESULT_OK && data != null) {
                startForegroundService(
                    Intent(this, HelperService::class.java)
                        .putExtra(HelperService.EXTRA_CODE, r.resultCode)
                        .putExtra(HelperService.EXTRA_DATA, data),
                )
            }
            this.refresh++
            if (grantOnly) finish()
        }
        val requestConsent = {
            consent.launch(getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent())
        }
        if (autoGrant) {
            // After composition: launchers can't be used until they're registered.
            LaunchedEffect(Unit) {
                autoGrant = false
                requestConsent()
            }
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("ES-DE Overlay", style = MaterialTheme.typography.headlineSmall)

            // What the menu can't work without; a tap on one that isn't ready goes to where it's fixed.
            val overlay = Settings.canDrawOverlays(this@SettingsActivity)
            val usage = usageAccess()
            val menuService = menuServiceEnabled()
            val checklist = listOf(
                Triple("Folders", esde != null && roms != null, { if (esde == null) pickEsde.launch(null) else pickRoms.launch(null) }),
                Triple("Display over other apps", overlay, { openSettings(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, withPackage = true) }),
                Triple("Usage access", usage, { openSettings(Settings.ACTION_USAGE_ACCESS_SETTINGS, withPackage = false) }),
                Triple(MENU_SERVICE, menuService, { openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS, withPackage = false) }),
            )
            for ((label, ready, fix) in checklist) {
                Text(
                    (if (ready) "✓ " else "✗ Not ready: ") + label,
                    Modifier.fillMaxWidth().clickable(enabled = !ready, onClick = fix),
                    color = if (ready) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
            }

            Section("Service")
            val missing = checklist.firstOrNull { !it.second }?.first
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (HelperService.running) {
                    OutlinedButton(onClick = { stopService(Intent(this@SettingsActivity, HelperService::class.java)); this@SettingsActivity.refresh++ }) {
                        Text("Stop Service")
                    }
                    Text("Running")
                } else {
                    Button(
                        onClick = { startForegroundService(Intent(this@SettingsActivity, HelperService::class.java)); this@SettingsActivity.refresh++ },
                        enabled = missing == null,
                    ) { Text("Start Service") }
                    Text(missing?.let { "Not ready: $it" } ?: "Stopped")
                }
            }
            if (HelperService.running) {
                Text(
                    "Stopping ends the menu and captures. Turning the screen off when ES-DE is idle keeps working, " +
                        "because the accessibility service does it; turn that off under Screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Start after boot")
                    Text(
                        "Starts the service when the device starts, so the menu works with no visit here. Capture still " +
                            "asks for permission the first time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = bootStart, onCheckedChange = { bootStart = it; prefs.bootStart = it })
            }
            Setting(
                "Quick settings tile",
                if (prefs.tileAdded) "Added" else "Not added",
                if (prefs.tileAdded) null else "Add",
                "Turn the overlay on or off from the notification shade",
            ) {
                getSystemService(StatusBarManager::class.java).requestAddTileService(
                    ComponentName(this@SettingsActivity, CaptureTile::class.java),
                    "ES-DE Overlay",
                    Icon.createWithResource(this@SettingsActivity, android.R.drawable.ic_menu_camera),
                    mainExecutor,
                ) { result ->
                    if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                        result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
                    ) {
                        prefs.tileAdded = true
                    }
                    this@SettingsActivity.refresh++
                }
            }

            Section("Folders")
            Setting(
                "ES-DE data folder",
                treeLabel(esde) + esdeWarning?.let { " — $it" }.orEmpty(),
                "Set Folder",
                "The ES-DE folder on your SD card or internal storage, the one that holds downloaded_media, gamelists and " +
                    "logs. Captures and guides are saved in its downloaded_media folder.",
            ) { pickEsde.launch(null) }
            Setting(
                "ROMs folder",
                treeLabel(roms) + romsWarning?.let { " — $it" }.orEmpty(),
                "Set Folder",
                "The folder that holds your system folders (snes, gbc…) with the games ES-DE lists. Android can't pick a " +
                    "card's root, so it's a separate pick.",
            ) { pickRoms.launch(null) }

            Section("Controls")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Menu button")
                    Text(
                        "Opens and closes the menu during a game ES-DE launched. Games don't get this button while the " +
                            "overlay runs. A trigger that reports as an axis can't be chosen, and some devices keep buttons " +
                            "such as Home or Guide for themselves: if pressing one shows nothing here, the device doesn't " +
                            "pass it on.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(if (learning) refused ?: "Press the controller button to use…" else keyName(prefs.menuKey), style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(onClick = { learning = !learning; refused = null }) { Text(if (learning) "Cancel" else "Change") }
                if (prefs.menuKey != 0 && !learning) {
                    OutlinedButton(onClick = { prefs.menuKey = 0; this@SettingsActivity.refresh++ }) { Text("Reset") }
                }
            }

            Section("Capture")
            Dropdown(
                "Screenshot media type",
                mediaType,
                MEDIA_TYPES.keys.toList(),
                { MEDIA_TYPES[it] ?: it },
                "Where screenshots are saved: pick the image type your theme shows in the gamelist, so captures show there.",
            ) { mediaType = it; prefs.mediaType = it }
            Text(
                "Android asks for permission to capture the screen the first time you pick Capture, and again after " +
                    "the service or the device restarts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Dropdown("Clip length", clip, (5..60 step 5).toList(), { "$it s" }) { clip = it; prefs.clipSeconds = it }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Use system screenshots as game images")
                    Text(
                        "A system screenshot you take during a game (Power + Volume down, or the Screenshot tile in quick settings) " +
                            "also becomes that game's image in ES-DE, following the choice below for an existing file " +
                            "(Ask keeps it). Needs Read screenshots.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = systemScreenshots, onCheckedChange = { systemScreenshots = it; prefs.systemScreenshots = it })
            }
            Text("If the media file already exists")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (o in Overwrite.entries) {
                    FilterChip(selected = overwrite == o, onClick = { overwrite = o; prefs.overwrite = o }, label = { Text(o.label) })
                }
            }
            Text(
                "ES-DE only shows the file named exactly like the ROM. Replace puts the new capture in its place. Ask " +
                    "keeps the new capture beside it until you choose in the menu. Skip keeps the file and drops the new capture.",
                style = MaterialTheme.typography.bodySmall,
            )
            Section("Screen")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Turn the screen off when ES-DE is idle")
                    Text(
                        "ES-DE keeps the screen on while it's in front, even behind its screensaver. This locks the screen " +
                            "like the power button once ES-DE's screensaver has run for the time below. Needs the " +
                            "accessibility service and usage access.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (screensaverReported == false) {
                        Text(
                            "In ES-DE, turn on Custom Event Scripts and Debug Mode under Other Settings, so it reports its screensaver.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Switch(checked = sleepWhenIdle, onCheckedChange = { sleepWhenIdle = it; prefs.sleepWhenIdle = it })
            }
            Dropdown("After the screensaver has run for", sleepMinutes, (0..30).toList(), { if (it == 0) "At once" else "$it min" }) {
                sleepMinutes = it
                prefs.sleepMinutes = it
            }
            Section("Look")
            Setting(
                "Theme",
                describe(lookSource),
                null,
                "The menu, guides and browser use the colours and font of the ES-DE theme in use. Themes from " +
                    "ES-DE's theme downloader are read through Shizuku. Put an overlay-theme.xml in the ES-DE folder " +
                    "to change any of them; see the docs.",
            ) {}
            Dropdown(
                "Text size",
                textScale,
                listOf(90, 100, 110, 125, 150, 175, 200),
                { if (it == 100) "100 % (the theme's)" else "$it %" },
                "Scales the theme's text in the menu and guides, for themes whose small text fits ES-DE's busy screens. " +
                    "The menu takes it from the next game you start.",
            ) { textScale = it; prefs.textScale = it }
            Dropdown(
                "Menu image",
                menuImageType,
                listOf("") + MEDIA_TYPES.keys,
                { if (it.isEmpty()) "Same as captures" else MEDIA_TYPES[it] ?: it },
                "Which of the game's ES-DE images the menu shows.",
            ) { menuImageType = it; prefs.menuImageType = it }
            Section("Quitting games")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Confirm before quitting")
                    Text(
                        "Quit Game ends the game's app at once, so progress since the last save is lost. With this on, " +
                            "the menu asks first, with Keep Playing selected.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = confirmQuit, onCheckedChange = { confirmQuit = it; prefs.confirmQuit = it })
            }
            Setting(
                "Shizuku",
                when (shizuku) {
                    ShizukuShell.State.NOT_INSTALLED -> "Not installed"
                    ShizukuShell.State.NOT_RUNNING -> "Not running"
                    ShizukuShell.State.NOT_ALLOWED -> "Not allowed"
                    ShizukuShell.State.READY -> "Ready"
                },
                when (shizuku) {
                    ShizukuShell.State.NOT_RUNNING -> "Open Shizuku"
                    ShizukuShell.State.NOT_ALLOWED -> "Allow"
                    else -> null
                },
                "Lets Quit Game end a game without opening its App Info page, and is needed to quit games left with " +
                    "Home. Install Shizuku, or a fork that starts on boot such as Shizuku-Next.",
            ) {
                // After "Deny and don't ask again", Shizuku's own app list is the only place left to allow it.
                if (shizuku == ShizukuShell.State.NOT_ALLOWED && !Shizuku.shouldShowRequestPermissionRationale()) {
                    ShizukuShell.requestPermission()
                } else {
                    packageManager.getLaunchIntentForPackage(ShizukuShell.PACKAGE)?.let(::startActivity)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Quit games left with Home")
                    Text(
                        "Ends the game's app when you leave it with Home, as Quit Game does, so progress since the last " +
                            "save is lost and the game starts fresh next time. ES-DE can then launch a different " +
                            "RetroArch game the first time. Needs Shizuku.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = quitOnHome,
                    onCheckedChange = { quitOnHome = it; prefs.quitOnHome = it },
                    enabled = shizuku == ShizukuShell.State.READY,
                )
            }
            if (esde != null) {
                Section("Guides")
                Setting("Saved guides", "Browse guides by game and delete the ones you no longer need", "Manage Guides") {
                    startActivity(Intent(this@SettingsActivity, GuidesActivity::class.java))
                }
            }
            Section("Permissions")
            val ask = { permission: String ->
                asked = permission
                askPermission.launch(permission)
            }
            Group("Required")
            Setting("Display over other apps", overlay.label, overlay.action, "For the in-game menu and the guide viewer.") {
                openSettings(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, withPackage = true)
            }
            Setting("Usage access", usage.label, usage.action, "Tells which app is in front.") {
                openSettings(Settings.ACTION_USAGE_ACCESS_SETTINGS, withPackage = false)
            }
            Setting(
                MENU_SERVICE,
                menuService.label,
                menuService.action,
                "Opens the menu on the controller's menu button during a game ES-DE launched, and lets Quit Game end " +
                    "the game." + if (menuService) "" else " If Android says the setting is restricted, open this app's " +
                    "App Info, tap ⋮, then Allow restricted settings, and try again.",
            ) { openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS, withPackage = false) }
            Group("Recommended")
            PermissionRow(
                "Notifications",
                Manifest.permission.POST_NOTIFICATIONS,
                "Shows the service's notification with Open Menu, and capture results.",
                ask,
            )
            val battery = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
            Setting("Ignore battery optimization", battery.label, battery.action, "Keeps the service running in the background.") {
                if (battery) {
                    openSettings(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS, withPackage = false)
                } else {
                    @Suppress("BatteryLife")
                    openSettings(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, withPackage = true)
                }
            }
            Group("Optional")
            PermissionRow(
                "Game sound in clips",
                Manifest.permission.RECORD_AUDIO,
                "Records the game's sound only. Android calls it audio recording; the microphone is never recorded.",
                ask,
            )
            PermissionRow(
                "Read screenshots",
                Manifest.permission.READ_MEDIA_IMAGES,
                "Only needed for Use system screenshots as game images, under Capture.",
                ask,
            )
        }
    }

    /** A runtime permission's row: [ask] requests it, or App Info turns it off once it's on. */
    @Composable
    private fun PermissionRow(title: String, permission: String, description: String, ask: (String) -> Unit) {
        val on = granted(permission)
        Setting(title, on.label, on.action, description) {
            if (on) openSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, withPackage = true) else ask(permission)
        }
    }

    @Composable
    private fun treePicker(save: (Uri) -> Unit) =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
                save(uri)
            }
            refresh++
        }

    @Composable
    private fun Section(title: String) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
    }

    /** How much a group of rows in a section matters: Required, Recommended or Optional. */
    @Composable
    private fun Group(title: String) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }

    /** A row with a button labelled [action], or none when [action] is null. */
    @Composable
    private fun Setting(title: String, status: String, action: String?, description: String? = null, onClick: () -> Unit) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title)
                description?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(status, style = MaterialTheme.typography.bodySmall)
            }
            if (action != null) OutlinedButton(onClick = onClick) { Text(action) }
        }
    }

    private fun describe(source: LookSource): String = when (source) {
        is LookSource.Theme -> "Follows ES-DE's theme: ${source.name}" + source.scheme.takeIf { it.isNotEmpty() }?.let { " — $it" }.orEmpty()
        is LookSource.Default -> "Default look (Linear): ${source.reason}"
        is LookSource.File -> "overlay-theme.xml over " + when (val under = source.underlying) {
            is LookSource.Default -> "the default look (Linear)"
            else -> describe(under).removePrefix("Follows ES-DE's theme: ")
        }
    }

    /**
     * A button showing [value] that opens a menu of [options]; a [value] not among them still shows.
     * Not ExposedDropdownMenuBox: its read-only field keeps D-pad focus, so the controller can't leave it.
     */
    @Composable
    private fun <T> Dropdown(
        title: String,
        value: T,
        options: List<T>,
        text: (T) -> String,
        description: String? = null,
        onPick: (T) -> Unit,
    ) {
        var open by remember { mutableStateOf(false) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title)
                description?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Box {
                OutlinedButton(onClick = { open = true }) { Text("${text(value)} ▾") }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    for (o in options) DropdownMenuItem(text = { Text(text(o)) }, onClick = { open = false; onPick(o) })
                }
            }
        }
    }

    private fun keyName(code: Int) = when (code) {
        0 -> "Back or Menu (default)"
        KeyEvent.KEYCODE_BUTTON_THUMBL -> "L3"
        KeyEvent.KEYCODE_BUTTON_THUMBR -> "R3"
        KeyEvent.KEYCODE_BUTTON_MODE -> "Guide"
        else -> KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_").removePrefix("BUTTON_").replace('_', ' ')
            .lowercase().replaceFirstChar(Char::uppercase)
    }

    private fun treeLabel(uri: Uri?) = uri?.let { DocumentsContract.getTreeDocumentId(it) } ?: "Not set"

    /**
     * The service reads ES-DE's log to see launches, so a folder without it isn't the ES-DE folder. And ES-DE shows
     * game media from its MediaDirectory when that's set, where captures and guides saved here don't show.
     */
    private fun esdeWarning(esde: Uri): String? {
        if (runCatching { contentResolver.openInputStream(treeChild(esde, "logs/es_log.txt"))!!.close() }.isFailure) {
            return "No ES-DE log here; set the ES-DE folder"
        }
        val media = runCatching {
            contentResolver.openInputStream(treeChild(esde, "settings/es_settings.xml"))!!.use(::mediaDirectory)
        }.getOrDefault("")
        if (media.isEmpty() || media.trimEnd('/') == romRoots(esde).first() + "/downloaded_media") return null
        return "ES-DE shows media from $media, so captures and guides saved in this folder won't show in ES-DE"
    }

    // Null when there's no settings file to read, which the folder's own warning covers.
    private fun esdeReportsScreensaver(esde: Uri) = runCatching {
        contentResolver.openInputStream(treeChild(esde, "settings/es_settings.xml"))!!.use { reportsScreensaver(it.reader().readText()) }
    }.getOrNull()

    // Skipped while the ES-DE folder has no gamelists, which its own warning already covers.
    private fun romsWarning(esde: Uri, roms: Uri): String? {
        fun names(tree: Uri, path: String) = runCatching { children(this, tree, DocumentsContract.getTreeDocumentId(tree) + path).keys }
            .getOrDefault(emptySet())
        val systems = names(esde, "/gamelists")
        return if (systems.isNotEmpty() && (systems intersect names(roms, "")).isEmpty()) "No ES-DE system folders here" else null
    }

    private fun granted(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun usageAccess() = getSystemService(AppOpsManager::class.java)
        .unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, applicationInfo.uid, packageName) == AppOpsManager.MODE_ALLOWED

    // ButtonService itself: an update keeps the entry of the service's old name, which no longer exists.
    private fun menuServiceEnabled() =
        Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty().split(':')
            .any { ComponentName.unflattenFromString(it) == ComponentName(this, ButtonService::class.java) }

    private fun openSettings(action: String, withPackage: Boolean) {
        startActivity(Intent(action).apply { if (withPackage) data = Uri.parse("package:$packageName") })
    }

    private val Boolean.label get() = if (this) "Enabled" else "Disabled"

    private val Boolean.action get() = if (this) "Disable" else "Enable"

    private val Overwrite.label
        get() = when (this) {
            Overwrite.REPLACE -> "Replace"
            Overwrite.ASK -> "Ask"
            Overwrite.SKIP -> "Skip"
        }

    companion object {
        const val EXTRA_GRANT = "grant"
        private const val MENU_SERVICE = "Controller button (accessibility service)"

        // ES-DE's image folders under downloaded_media/<system>/, labelled as its scraper menu names them.
        private val MEDIA_TYPES = linkedMapOf(
            "screenshots" to "Screenshots",
            "titlescreens" to "Title Screens",
            "miximages" to "Miximages",
            "covers" to "Box Covers",
            "backcovers" to "Box Back Covers",
            "3dboxes" to "3D Boxes",
            "fanart" to "Fan Art",
            "marquees" to "Marquees",
            "physicalmedia" to "Physical Media",
        )
    }
}

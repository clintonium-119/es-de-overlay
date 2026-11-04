package com.clintonium.es_de_overlay

import android.Manifest
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
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import com.clintonium.es_de_overlay.core.Overwrite
import com.clintonium.es_de_overlay.core.reportsScreensaver

class SettingsActivity : ComponentActivity() {
    private var refresh by mutableIntStateOf(0)
    private var autoGrant = false

    // Opened just to grant consent (tile, boot notification, re-grant tap): go back to the game afterwards.
    private var grantOnly = false

    // The permission the dialog was last launched for: its result only says whether it was granted.
    private var asked = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        var clip by remember { mutableIntStateOf(prefs.clipSeconds) }
        var overwrite by remember { mutableStateOf(prefs.overwrite) }
        var bootStart by remember { mutableStateOf(prefs.bootStart) }
        var sleepWhenIdle by remember { mutableStateOf(prefs.sleepWhenIdle) }
        var sleepMinutes by remember { mutableIntStateOf(prefs.sleepMinutes) }
        val screensaverReported = remember(tick, esde) { esde?.let(::esdeReportsScreensaver) }

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

            Section("Service")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (HelperService.running) {
                    OutlinedButton(onClick = { stopService(Intent(this@SettingsActivity, HelperService::class.java)); this@SettingsActivity.refresh++ }) {
                        Text("Stop Service")
                    }
                    Text("Running")
                } else {
                    Button(onClick = requestConsent, enabled = prefs.esdeTree != null && prefs.romsTree != null) { Text("Start Service") }
                    Text(if (prefs.esdeTree == null || prefs.romsTree == null) "Set both folders first" else "Stopped")
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Offer to start after boot", Modifier.weight(1f))
                Switch(checked = bootStart, onCheckedChange = { bootStart = it; prefs.bootStart = it })
            }
            Setting(
                "Quick settings tile",
                if (prefs.tileAdded) "Added" else "Not added",
                if (prefs.tileAdded) null else "Add",
                "Turn the recorder on or off from the notification shade",
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

            Section("Capture")
            Dropdown(
                "Screenshot media type",
                mediaType,
                MEDIA_TYPES.keys.toList(),
                { MEDIA_TYPES[it] ?: it },
                "Pick the image type your theme shows in the gamelist, so captures show there. The in-game menu shows the same type.",
            ) { mediaType = it; prefs.mediaType = it }
            Dropdown("Clip length", clip, (5..60 step 5).toList(), { "$it s" }) { clip = it; prefs.clipSeconds = it }
            Text("If the media file already exists")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (o in Overwrite.entries) {
                    FilterChip(selected = overwrite == o, onClick = { overwrite = o; prefs.overwrite = o }, label = { Text(o.label) })
                }
            }
            Text(
                "ES-DE only shows the file named exactly like the ROM: Replace saves over it, Skip keeps it and drops the new capture.",
                style = MaterialTheme.typography.bodySmall,
            )
            Section("Screen")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Turn the screen off when ES-DE is idle")
                    Text(
                        "ES-DE keeps the screen on while it's in front, even behind its screensaver. This locks the screen " +
                            "like the power button once ES-DE's screensaver has run for the time below. Needs the Back " +
                            "button menu and usage access enabled.",
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
            if (esde != null) {
                Section("Guides")
                Setting("Saved guides", "Browse guides by game and delete the ones you no longer need", "Manage Guides") {
                    startActivity(Intent(this@SettingsActivity, GuidesActivity::class.java))
                }
            }
            Section("Permissions")
            for ((title, permission, description) in listOf(
                Triple("Notifications", Manifest.permission.POST_NOTIFICATIONS, "Shows capture results and the offer to start after boot."),
                Triple("Game sound in clips", Manifest.permission.RECORD_AUDIO, "Android calls it audio recording; only game audio is captured."),
                Triple("Read screenshots", Manifest.permission.READ_MEDIA_IMAGES, "Files a system screenshot (Power + Volume down) under the game."),
            )) {
                val on = granted(permission)
                Setting(title, on.label, on.action, description) {
                    if (on) {
                        openSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, withPackage = true)
                    } else {
                        asked = permission
                        askPermission.launch(permission)
                    }
                }
            }
            val menu = menuServiceEnabled()
            Setting(
                "Back button menu",
                menu.label,
                menu.action,
                "An accessibility service. The menu opens on the controller's Back button during a game ES-DE launched; no " +
                    "Retroid setting is needed.",
            ) { openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS, withPackage = false) }
            val overlay = Settings.canDrawOverlays(this@SettingsActivity)
            Setting("Display over other apps", overlay.label, overlay.action, "For the in-game menu and the guide viewer.") {
                openSettings(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, withPackage = true)
            }
            val usage = usageAccess()
            Setting("Usage access", usage.label, usage.action, "Tells which app is in front.") {
                openSettings(Settings.ACTION_USAGE_ACCESS_SETTINGS, withPackage = false)
            }
            val battery = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
            Setting("Ignore battery optimization", battery.label, battery.action, "Keeps the service running in the background.") {
                if (battery) {
                    openSettings(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS, withPackage = false)
                } else {
                    @Suppress("BatteryLife")
                    openSettings(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, withPackage = true)
                }
            }
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

    private fun treeLabel(uri: Uri?) = uri?.let { DocumentsContract.getTreeDocumentId(it) } ?: "Not set"

    // The service reads this log to see launches, so a folder without it isn't the ES-DE folder.
    private fun esdeWarning(esde: Uri) =
        if (runCatching { contentResolver.openInputStream(treeChild(esde, "logs/es_log.txt"))!!.close() }.isFailure) {
            "No ES-DE log here; set the ES-DE folder"
        } else null

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
            Overwrite.SKIP -> "Skip"
        }

    companion object {
        const val EXTRA_GRANT = "grant"

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

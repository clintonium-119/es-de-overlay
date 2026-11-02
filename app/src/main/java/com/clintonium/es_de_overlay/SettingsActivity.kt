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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.clintonium.es_de_overlay.core.Overwrite

class SettingsActivity : ComponentActivity() {
    private var refresh by mutableIntStateOf(0)
    private var autoGrant = false

    // Opened just to grant consent (tile, boot notification, re-grant tap): go back to the game afterwards.
    private var grantOnly = false

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
        @Suppress("UNUSED_VARIABLE") val tick = refresh
        val prefs = remember { Prefs(this) }
        var mediaType by remember { mutableStateOf(prefs.mediaType) }
        var clip by remember { mutableStateOf(prefs.clipSeconds.toString()) }
        var overwrite by remember { mutableStateOf(prefs.overwrite) }
        var bootStart by remember { mutableStateOf(prefs.bootStart) }
        var closeOnLeave by remember { mutableStateOf(prefs.closeOnLeave) }
        // Every app with an icon, by name; not ES-DE itself or this app.
        val apps = remember {
            packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .map { it.activityInfo.applicationInfo }
                .filter { it.packageName != packageName && it.packageName != ESDE_PACKAGE }
                .distinctBy { it.packageName }
                .map { it.packageName to it.loadLabel(packageManager).toString() }
                .sortedBy { it.second.lowercase() }
        }

        val pickEsde = treePicker { prefs.esdeTree = it }
        val pickRoms = treePicker { prefs.romsTree = it }
        val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { this.refresh++ }
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

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (HelperService.running) {
                    OutlinedButton(onClick = { stopService(Intent(this@SettingsActivity, HelperService::class.java)); this@SettingsActivity.refresh++ }) {
                        Text("Stop service")
                    }
                    Text("Running")
                } else {
                    Button(onClick = requestConsent, enabled = prefs.esdeTree != null && prefs.romsTree != null) { Text("Start service") }
                    Text(if (prefs.esdeTree == null || prefs.romsTree == null) "Pick both folders first" else "Stopped")
                }
            }

            Section("Folders")
            Setting("ES-DE data folder", treeLabel(prefs.esdeTree), "Pick") { pickEsde.launch(null) }
            Setting("ROMs folder", treeLabel(prefs.romsTree), "Pick") { pickRoms.launch(null) }

            Section("Capture")
            OutlinedTextField(
                value = mediaType,
                onValueChange = { mediaType = it.trim(); if (mediaType.isNotEmpty()) prefs.mediaType = mediaType },
                label = { Text("Screenshot media type folder") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = clip,
                onValueChange = { v -> clip = v.filter(Char::isDigit); clip.toIntOrNull()?.let { prefs.clipSeconds = it.coerceIn(1, 60) } },
                label = { Text("Clip length (seconds, 1–60)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Text("If the media file already exists")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (o in Overwrite.entries) {
                    FilterChip(selected = overwrite == o, onClick = { overwrite = o; prefs.overwrite = o }, label = { Text(o.label) })
                }
            }
            Text(
                "ES-DE only shows the file named exactly like the ROM; \"Keep both\" saves extras as name-1, name-2… for you to pick from later.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Offer to start after boot", Modifier.weight(1f))
                Switch(checked = bootStart, onCheckedChange = { bootStart = it; prefs.bootStart = it })
            }

            Setting("Quick settings tile", "Turn the recorder on or off from the notification shade", "Add") {
                getSystemService(StatusBarManager::class.java).requestAddTileService(
                    ComponentName(this@SettingsActivity, CaptureTile::class.java),
                    "ES-DE Overlay",
                    Icon.createWithResource(this@SettingsActivity, android.R.drawable.ic_menu_camera),
                    mainExecutor,
                ) {}
            }

            Section("Permissions")
            Setting("Notifications", granted(Manifest.permission.POST_NOTIFICATIONS).label, "Grant") {
                askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            Setting("Game sound in clips (Android calls it audio recording; only game audio is captured)", granted(Manifest.permission.RECORD_AUDIO).label, "Grant") {
                askPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
            Setting("Read screenshots (system screenshot pickup)", granted(Manifest.permission.READ_MEDIA_IMAGES).label, "Grant") {
                askPermission.launch(Manifest.permission.READ_MEDIA_IMAGES)
            }
            Setting("Home menu (accessibility service)", if (homeMenuEnabled()) "Enabled" else "Not enabled", "Open") {
                openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS, withPackage = false)
            }
            Text(
                "The menu opens on the toast from Retroid's \"Prevent pressing Home accidentally\", so keep that setting on.",
                style = MaterialTheme.typography.bodySmall,
            )
            Setting("Display over other apps (menu and viewer)", Settings.canDrawOverlays(this@SettingsActivity).label, "Open") {
                openSettings(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, withPackage = true)
            }
            Setting("Usage access (which app is in front)", usageAccess().label, "Open") {
                openSettings(Settings.ACTION_USAGE_ACCESS_SETTINGS, withPackage = false)
            }
            Setting(
                "Ignore battery optimization",
                getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName).label,
                "Open",
            ) {
                @Suppress("BatteryLife")
                openSettings(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, withPackage = true)
            }

            Section("Close when you leave")
            Text(
                "Switch on an app to close it completely when you go back to ES-DE from a game ES-DE started in it, as Quit Game does, so its next launch starts fresh. It closes without warning: your saves are kept, but progress since your last save is lost, and an emulator's save-on-exit doesn't run. Android's Settings app shows for a moment while it closes.",
                style = MaterialTheme.typography.bodySmall,
            )
            apps.forEach { (pkg, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, Modifier.weight(1f))
                    Switch(checked = pkg in closeOnLeave, onCheckedChange = { on ->
                        closeOnLeave = if (on) closeOnLeave + pkg else closeOnLeave - pkg
                        prefs.closeOnLeave = closeOnLeave
                    })
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

    @Composable
    private fun Setting(title: String, status: String, action: String, onClick: () -> Unit) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title)
                Text(status, style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = onClick) { Text(action) }
        }
    }

    private fun treeLabel(uri: Uri?) = uri?.let { DocumentsContract.getTreeDocumentId(it) } ?: "Not set"

    private fun granted(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun usageAccess() = getSystemService(AppOpsManager::class.java)
        .unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, applicationInfo.uid, packageName) == AppOpsManager.MODE_ALLOWED

    private fun homeMenuEnabled() =
        Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)?.contains("$packageName/") == true

    private fun openSettings(action: String, withPackage: Boolean) {
        startActivity(Intent(action).apply { if (withPackage) data = Uri.parse("package:$packageName") })
    }

    private val Boolean.label get() = if (this) "Granted" else "Not granted"

    private val Overwrite.label
        get() = when (this) {
            Overwrite.REPLACE -> "Replace"
            Overwrite.SKIP -> "Skip"
            Overwrite.SUFFIX -> "Keep both"
        }

    companion object {
        const val EXTRA_GRANT = "grant"
    }
}

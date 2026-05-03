package com.therealaleph.mhrv.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.therealaleph.mhrv.CaInstall
import com.therealaleph.mhrv.ConfigStore
import com.therealaleph.mhrv.DEFAULT_SNI_POOL
import com.therealaleph.mhrv.MhrvConfig
import com.therealaleph.mhrv.Mode
import com.therealaleph.mhrv.Native
import com.therealaleph.mhrv.ConnectionMode
import com.therealaleph.mhrv.NetworkDetect
import com.therealaleph.mhrv.R
import com.therealaleph.mhrv.SplitMode
import com.therealaleph.mhrv.UiLang
import com.therealaleph.mhrv.VpnState
import androidx.compose.ui.res.stringResource
import com.therealaleph.mhrv.ui.theme.ErrRed
import com.therealaleph.mhrv.ui.theme.OkGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

// ... (sealed classes remain unchanged) ...

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onStart: () -> Unit,
    onStop: () -> Unit,
    onInstallCaConfirmed: () -> Unit,
    caOutcome: CaInstallOutcome?,
    onCaOutcomeConsumed: () -> Unit,
    onLangChange: (UiLang) -> Unit = {},
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var cfg by remember { mutableStateOf(ConfigStore.load(ctx)) }
    fun persist(new: MhrvConfig) {
        cfg = new
        ConfigStore.save(ctx, new)
    }

    var showInstallDialog by rememberSaveable { mutableStateOf(false) }

    // Auto update check (kept, but no UI button for it now)
    var autoUpdateChecked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(autoUpdateChecked) {
        if (autoUpdateChecked) return@LaunchedEffect
        autoUpdateChecked = true
        val json = withContext(Dispatchers.IO) {
            runCatching { Native.checkUpdate() }.getOrNull()
        }
        if (json != null) {
            val obj = runCatching { JSONObject(json) }.getOrNull()
            if (obj?.optString("kind") == "updateAvailable") {
                snackbar.showSnackbar(
                    "Update available: v${obj.optString("current")} → " +
                    "v${obj.optString("latest")}  ${obj.optString("url")}",
                    withDismissAction = true,
                )
            }
        }
    }

    var awaitingRunning by remember { mutableStateOf<Boolean?>(null) }
    val transitioning = awaitingRunning != null
    LaunchedEffect(awaitingRunning) {
        val target = awaitingRunning ?: return@LaunchedEffect
        try {
            withTimeoutOrNull(12_000) {
                VpnState.isRunning.first { it == target }
            }
        } finally {
            awaitingRunning = null
        }
    }

    LaunchedEffect(caOutcome) {
        val o = caOutcome ?: return@LaunchedEffect
        val msg = when (o) {
            is CaInstallOutcome.Installed -> "Certificate installed ✓"
            is CaInstallOutcome.NotInstalled -> buildString {
                append("Certificate not yet installed.")
                if (!o.downloadPath.isNullOrBlank()) {
                    append(" Saved to ${o.downloadPath}. ")
                    append("In Settings, search for \"CA certificate\" and install from there — NOT \"VPN & app user certificate\" or \"Wi-Fi\".")
                } else {
                    append(" Tap Install again to retry.")
                }
            }
            is CaInstallOutcome.Failed -> o.message
        }
        snackbar.showSnackbar(msg, withDismissAction = true)
        onCaOutcomeConsumed()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mooz VPN") },   // changed title
                // actions removed (no language toggle, no update check button)
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ========== REPLACED ConfigSharingBar ==========
            // Only Import (from clipboard) and Scan QR remain
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Import button: reads from clipboard and calls onImport
                val clipboardManager = LocalClipboardManager.current
                Button(
                    onClick = {
                        val clip = clipboardManager.getText()?.text
                        if (!clip.isNullOrBlank()) {
                            // Assume the clipboard contains a valid config string
                            // (JSON or the format expected by ConfigStore)
                            persist(MhrvConfig.fromJson(clip) ?: run {
                                snackbar.showSnackbar("Invalid config in clipboard")
                                return@Button
                            })
                            snackbar.showSnackbar("Config imported from clipboard")
                        } else {
                            snackbar.showSnackbar("Clipboard is empty")
                        }
                    }
                ) {
                    Text("Import")
                }

                // Scan button (placeholder – integrate your QR scanner here)
                Button(
                    onClick = {
                        // TODO: Replace with actual QR scanning logic
                        // For now, show a snackbar suggesting to implement
                        snackbar.showSnackbar("QR scanning not yet implemented")
                    }
                ) {
                    Text("Scan QR")
                }
            }

            Spacer(Modifier.height(32.dp))

            val isVpnRunning by VpnState.isRunning.collectAsState()
            val buttonEnabled = (isVpnRunning ||
                cfg.mode == Mode.DIRECT ||
                (cfg.hasDeploymentId && cfg.authKey.isNotBlank())) && !transitioning

            CircularConnectButton(
                isRunning = isVpnRunning,
                transitioning = transitioning,
                enabled = buttonEnabled,
                onClick = {
                    if (isVpnRunning) {
                        awaitingRunning = false
                        onStop()
                    } else {
                        awaitingRunning = true
                        scope.launch {
                            var updated = cfg
                            if (updated.googleIp.isBlank()) {
                                val fresh = withContext(Dispatchers.IO) {
                                    NetworkDetect.resolveGoogleIp()
                                }
                                if (!fresh.isNullOrBlank()) {
                                    updated = updated.copy(googleIp = fresh)
                                }
                            }
                            if (updated.frontDomain.isBlank() ||
                                updated.frontDomain.parseAsIpOrNull() != null
                            ) {
                                updated = updated.copy(frontDomain = "www.google.com")
                            }
                            if (updated !== cfg) persist(updated)
                            onStart()
                        }
                    }
                },
                modifier = Modifier.padding(16.dp),
            )

            Spacer(Modifier.height(24.dp))

            if (transitioning) {
                Text("…", style = MaterialTheme.typography.titleMedium)
            } else {
                Text(
                    if (isVpnRunning) "متصل" else "قطع",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isVpnRunning) OkGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    // CA install dialog unchanged
    if (showInstallDialog) {
        // ... (unchanged, same as original) ...
    }
}

// ========== CircularConnectButton and helper functions remain exactly the same ==========
// (no changes below this line)

@Composable
private fun CircularConnectButton(
    isRunning: Boolean,
    transitioning: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // ... (unchanged) ...
}

private fun summarizeUpdateCheck(json: String?): String {
    // ... (unchanged) ...
}

private fun String.parseAsIpOrNull(): java.net.InetAddress? {
    // ... (unchanged) ...
}

private fun parseProbeResult(json: String?): ProbeState {
    // ... (unchanged) ...
}

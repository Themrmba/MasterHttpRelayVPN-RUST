package com.therealaleph.mhrv.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.therealaleph.mhrv.CaInstall
import com.therealaleph.mhrv.ConfigStore
import com.therealaleph.mhrv.MhrvConfig
import com.therealaleph.mhrv.Mode
import com.therealaleph.mhrv.Native
import com.therealaleph.mhrv.NetworkDetect
import com.therealaleph.mhrv.R
import com.therealaleph.mhrv.UiLang
import com.therealaleph.mhrv.VpnState
import com.therealaleph.mhrv.ui.theme.NeonCyan
import com.therealaleph.mhrv.ui.theme.NeonGreen
import com.therealaleph.mhrv.ui.theme.NeonMagenta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

sealed class CaInstallOutcome {
    object Installed : CaInstallOutcome()
    data class NotInstalled(val downloadPath: String?) : CaInstallOutcome()
    data class Failed(val message: String) : CaInstallOutcome()
}

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

    // دروازه‌ی Start/Stop بر اساس وضعیت واقعی سرویس
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

    // وضعیت BottomSheet
    val sheetState = rememberModalBottomSheetState()
    var showSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("mhrv-rs") })
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showSheet = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
                modifier = Modifier
                    .shadow(
                        elevation = 16.dp,
                        shape = CircleShape,
                        ambientColor = MaterialTheme.colorScheme.primary,
                        spotColor = MaterialTheme.colorScheme.primary
                    )
                    .border(2.dp, Color.White, CircleShape)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Open")
            }
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ۱. ایمپورت / اکسپورت کانفیگ
            ConfigSharingBar(
                cfg = cfg,
                onImport = { persist(it) },
                onSnackbar = { snackbar.showSnackbar(it) }
            )

            // ۲. دکمه‌ی Connect / Disconnect
            val isVpnRunning by VpnState.isRunning.collectAsState()
            Button(
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
                                if (!fresh.isNullOrBlank()) updated = updated.copy(googleIp = fresh)
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
                enabled = (isVpnRunning ||
                        cfg.mode == Mode.DIRECT ||
                        (cfg.hasDeploymentId && cfg.authKey.isNotBlank())) && !transitioning,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isVpnRunning) Color(0xFFFF5252) else NeonGreen,
                    contentColor = Color.Black,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) {
                Text(
                    when {
                        transitioning -> "…"
                        isVpnRunning -> stringResource(R.string.btn_disconnect)
                        else -> stringResource(R.string.btn_connect)
                    },
                    style = MaterialTheme.typography.titleMedium
                )
            }

            // ۳. دکمه‌ی نصب CA
            FilledTonalButton(
                onClick = { showInstallDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.btn_install_mitm))
            }
        }
    }

    // دیالوگ نصب CA (بدون تغییر)
    if (showInstallDialog) {
        val exported = remember { CaInstall.export(ctx) }
        val fp = remember(exported) { if (exported) CaInstall.fingerprint(ctx) else null }
        val cn = remember(exported) { if (exported) CaInstall.subjectCn(ctx) else null }

        AlertDialog(
            onDismissRequest = { showInstallDialog = false },
            title = { Text(stringResource(R.string.dialog_install_mitm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "mhrv-rs creates a local certificate authority so it can decrypt " +
                        "and re-encrypt HTTPS traffic before tunnelling it through the Apps " +
                        "Script relay. Without this CA installed as trusted, apps will show " +
                        "certificate errors."
                    )
                    Text(
                        "On Android 11+ the system removed the inline install path, so " +
                        "tapping Install will: (1) save a PEM copy to Downloads/mhrv-ca.crt, " +
                        "(2) open the Settings app.\n\n" +
                        "Inside Settings, tap the search bar and type \"CA certificate\". " +
                        "Open the result labelled \"CA certificate\" (NOT \"VPN & app user " +
                        "certificate\" or \"Wi-Fi certificate\"). Pick mhrv-ca.crt from " +
                        "Downloads when prompted. If you don't have a screen lock, Android " +
                        "will ask you to add one first — that's an OS requirement for " +
                        "installing any user CA."
                    )
                    if (fp != null) {
                        Text("Subject: ${cn ?: "(unknown)"}", style = MaterialTheme.typography.labelMedium)
                        Text(
                            "SHA-256: ${CaInstall.fingerprintHex(fp)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace
                        )
                    } else {
                        Text(
                            "Could not read the CA cert yet. Tap Start once so the " +
                            "proxy generates it, then come back.",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showInstallDialog = false
                        if (fp != null) onInstallCaConfirmed()
                    },
                    enabled = fp != null
                ) { Text("Install") }
            },
            dismissButton = {
                TextButton(onClick = { showInstallDialog = false }) { Text("Cancel") }
            }
        )
    }

    // برگه‌ی کشویی پایین با گوشه‌های بالای گرد
    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 0.dp, bottomEnd = 0.dp),
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("mhrv-rs", style = MaterialTheme.typography.titleMedium)
                Button(
                    onClick = {
                        val intent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://t.me/BHBadGateWay")
                        )
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { ctx.startActivity(intent) }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,   // NeonMagenta
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(2.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(12.dp))
                        .shadow(8.dp, RoundedCornerShape(12.dp), spotColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text("Follow Channel", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

// ========================================================
//  تابع کمکی برای تشخیص IP (دست‌نخورده از نسخه‌ی اصلی)
// ========================================================
private fun String.parseAsIpOrNull(): java.net.InetAddress? {
    val s = trim()
    if (s.isEmpty() || s.any { it.isLetter() }) return null
    return try {
        java.net.InetAddress.getByName(s).takeIf {
            it.hostAddress?.let { addr -> addr == s || addr.contains(s) } == true
        }
    } catch (_: Throwable) {
        null
    }
}

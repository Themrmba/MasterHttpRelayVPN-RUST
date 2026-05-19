package com.therealaleph.mhrv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.therealaleph.mhrv.ui.theme.NeonGreen
import com.therealaleph.mhrv.ui.theme.NeonMagenta
import com.therealaleph.mhrv.ui.theme.NeonCyan
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

    val sheetState = rememberModalBottomSheetState()
    var showSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Nice Relay") })
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ۱. دکمه‌ی نصب CA
            FilledTonalButton(
                onClick = { showInstallDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                Text(stringResource(R.string.btn_install_mitm))
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ۲. نوار ایمپورت/اکسپورت کانفیگ
            ConfigSharingBar(
                cfg = cfg,
                onImport = { persist(it) },
                onSnackbar = { snackbar.showSnackbar(it) }
            )

            // فضای خالی برای هل دادن دکمه‌ی اتصال به مرکز عمودی
            Spacer(modifier = Modifier.weight(1f))

            // ۳. دکمه‌ی Connect بزرگ و مرکز
            val isVpnRunning by VpnState.isRunning.collectAsState()
            val connectEnabled = (isVpnRunning ||
                    cfg.mode == Mode.DIRECT ||
                    (cfg.hasDeploymentId && cfg.authKey.isNotBlank())) && !transitioning

            val connectModifier = if (connectEnabled) {
                Modifier.clickable {
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
                }
            } else {
                Modifier
            }

            Box(
                modifier = Modifier
                    .size(110.dp)
                    .shadow(16.dp, CircleShape, spotColor = NeonGreen, ambientColor = NeonGreen)
                    .clip(CircleShape)
                    .background(if (isVpnRunning) Color(0xFFFF5252) else NeonGreen)
                    .then(connectModifier),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PowerSettingsNew,
                    contentDescription = if (isVpnRunning) "Disconnect" else "Connect",
                    tint = Color.Black,
                    modifier = Modifier.size(52.dp)
                )
            }

            // فضای خالی پایین دکمه تا دکمه‌ی Follow
            Spacer(modifier = Modifier.weight(1f))

            // ۴. دکمه‌ی باز کردن برگه‌ی Follow
            Button(
                onClick = { showSheet = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonCyan,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .border(2.dp, Color.White, RoundedCornerShape(12.dp))
                    .shadow(8.dp, RoundedCornerShape(12.dp), spotColor = NeonCyan)
            ) {
                Text("Follow", style = MaterialTheme.typography.labelLarge)
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    // ---------- برگه‌ی کشویی Follow ----------
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
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Follow", style = MaterialTheme.typography.titleMedium)

                // دکمه‌ی Follow Us (تلگرام)
                Button(
                    onClick = {
                        val intent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://t.me/TheNiceGateWay")
                        )
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { ctx.startActivity(intent) }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonCyan,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .border(2.dp, Color.White, RoundedCornerShape(12.dp))
                        .shadow(6.dp, RoundedCornerShape(12.dp), spotColor = NeonCyan)
                ) {
                    Text("Follow Us", style = MaterialTheme.typography.labelLarge)
                }

                // دکمه‌ی Follow on Super App (روبیکا)
                Button(
                    onClick = {
                        val intent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(
                                "https://rubika.ir/joing/+BAHDFHDGD0JFFNTDEECCGZOADAANTXCW"
                            )
                        )
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { ctx.startActivity(intent) }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonMagenta,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .border(2.dp, Color.White, RoundedCornerShape(12.dp))
                        .shadow(6.dp, RoundedCornerShape(12.dp), spotColor = NeonMagenta)
                ) {
                    Text("Follow on Super App", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }

    // ---------- دیالوگ نصب CA ----------
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
}

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

package com.echosixhiya.webspeak.android.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echosixhiya.webspeak.android.R
import com.echosixhiya.webspeak.android.data.ConnectionProfileStore
import com.echosixhiya.webspeak.android.data.FavoriteServerProfile
import com.echosixhiya.webspeak.android.data.FavoriteServerProfiles
import com.echosixhiya.webspeak.android.model.JoinRequest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionScreen(
    connecting: Boolean,
    errorCode: String,
    errorMessage: String,
    snackbarHost: @Composable () -> Unit,
    onConnect: (JoinRequest) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val savedProfile = remember(context) { ConnectionProfileStore(context) }
    var favorites by remember(savedProfile) { mutableStateOf(savedProfile.favorites()) }
    var gateway by rememberSaveable { mutableStateOf(savedProfile.gatewayUrl()) }
    var nickname by rememberSaveable { mutableStateOf(savedProfile.nickname()) }
    var teamSpeakTarget by rememberSaveable { mutableStateOf(savedProfile.target()) }
    var channel by rememberSaveable { mutableStateOf("") }
    var serverPassword by rememberSaveable { mutableStateOf("") }
    var inviteToken by rememberSaveable { mutableStateOf("") }
    var rememberIdentity by rememberSaveable { mutableStateOf(true) }
    var advancedExpanded by rememberSaveable { mutableStateOf(false) }
    val currentProfile = FavoriteServerProfile(gateway.trim(), nickname.trim(), teamSpeakTarget.trim())
    val currentProfileIsFavorite = favorites.any { FavoriteServerProfiles.sameDestination(it, currentProfile) }

    val scrollState = rememberScrollState()
    val contentTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val contentBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Scaffold(
        snackbarHost = snackbarHost,
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
                .padding(top = (contentTop - scaffoldPadding.calculateTopPadding()).coerceAtLeast(8.dp) + 10.dp)
                .padding(bottom = (contentBottom - scaffoldPadding.calculateBottomPadding()).coerceAtLeast(8.dp) + 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BrandHero()
                Spacer(Modifier.height(22.dp))
                AppLanguageSelector()
                Spacer(Modifier.height(12.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(30.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(15.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                 Text(stringResource(R.string.connection_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                     stringResource(R.string.connection_description),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(Icons.Filled.Public, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }

                         if (errorMessage.isNotBlank()) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                shape = RoundedCornerShape(18.dp),
                            ) {
                                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                     Text(stringResource(R.string.connection_error_heading), color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.SemiBold)
                                     Text(
                                         text = localizedConnectionError(errorCode),
                                         color = MaterialTheme.colorScheme.onErrorContainer,
                                         style = MaterialTheme.typography.bodyMedium,
                                     )
                                    if (errorCode.isNotBlank()) {
                                        Text(errorCode, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = gateway,
                            onValueChange = {
                                if (!it.trim().equals(gateway.trim(), ignoreCase = true)) teamSpeakTarget = ""
                                gateway = it
                            },
                            modifier = Modifier.fillMaxWidth(),
                             label = { Text(stringResource(R.string.connection_gateway_label)) },
                            placeholder = { Text("https://voice.example.com") },
                            leadingIcon = { Icon(Icons.Filled.CloudDone, contentDescription = null) },
                            singleLine = true,
                            enabled = !connecting,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                        )
                        OutlinedTextField(
                            value = nickname,
                            onValueChange = { nickname = it.take(30) },
                            modifier = Modifier.fillMaxWidth(),
                             label = { Text(stringResource(R.string.connection_nickname_label)) },
                             placeholder = { Text(stringResource(R.string.connection_nickname_hint)) },
                            leadingIcon = { Icon(Icons.Filled.Mic, contentDescription = null) },
                            singleLine = true,
                            enabled = !connecting,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        )
                        TextButton(
                            onClick = { favorites = savedProfile.addFavorite(currentProfile) },
                            enabled = !connecting && FavoriteServerProfiles.isValid(currentProfile),
                            modifier = Modifier.align(Alignment.Start),
                        ) {
                            Icon(Icons.Filled.Star, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(if (currentProfileIsFavorite) R.string.favorite_update else R.string.favorite_add))
                        }
                        if (gateway.isNotBlank() && !FavoriteServerProfiles.isValid(currentProfile)) {
                            Text(
                                stringResource(R.string.favorite_gateway_invalid),
                                modifier = Modifier.align(Alignment.Start),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }

                        if (favorites.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(R.string.favorites_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(
                                    stringResource(R.string.favorites_privacy),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                favorites.forEach { favorite ->
                                    Card(
                                        onClick = {
                                            gateway = favorite.gatewayUrl
                                            nickname = favorite.nickname
                                            teamSpeakTarget = favorite.target
                                            channel = ""
                                            serverPassword = ""
                                            inviteToken = ""
                                            advancedExpanded = false
                                        },
                                        enabled = !connecting,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                                                Text(favorite.nickname, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                                                Text(
                                                    favorite.target.ifBlank { favorite.gatewayUrl },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                            IconButton(
                                                onClick = { favorites = savedProfile.removeFavorite(favorite) },
                                                enabled = !connecting,
                                            ) {
                                                Icon(Icons.Filled.DeleteOutline, contentDescription = stringResource(R.string.favorite_delete))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        TextButton(
                            onClick = { advancedExpanded = !advancedExpanded },
                            enabled = !connecting,
                            modifier = Modifier.align(Alignment.Start),
                        ) {
                            Icon(Icons.Filled.Tune, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                             Text(stringResource(if (advancedExpanded) R.string.connection_options_collapse else R.string.connection_options_expand))
                        }
                        if (advancedExpanded) {
                            OutlinedTextField(
                                value = channel,
                                onValueChange = { channel = it.take(100) },
                                modifier = Modifier.fillMaxWidth(),
                                 label = { Text(stringResource(R.string.connection_channel_optional)) },
                                singleLine = true,
                                enabled = !connecting,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            )
                            OutlinedTextField(
                                value = serverPassword,
                                onValueChange = { serverPassword = it.take(512) },
                                modifier = Modifier.fillMaxWidth(),
                                 label = { Text(stringResource(R.string.connection_password_optional)) },
                                leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                                singleLine = true,
                                enabled = !connecting,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            )
                            OutlinedTextField(
                                value = inviteToken,
                                onValueChange = { inviteToken = it.take(128) },
                                modifier = Modifier.fillMaxWidth(),
                                 label = { Text(stringResource(R.string.connection_invite_optional)) },
                                singleLine = true,
                                enabled = !connecting,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = rememberIdentity, onCheckedChange = { rememberIdentity = it }, enabled = !connecting)
                            Column(Modifier.padding(start = 4.dp)) {
                                 Text(stringResource(R.string.connection_remember_identity), style = MaterialTheme.typography.bodyMedium)
                                 Text(stringResource(R.string.connection_identity_secure), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Button(
                            onClick = {
                                onConnect(
                                    JoinRequest(
                                        gatewayUrl = gateway.trim(),
                                        nickname = nickname.trim(),
                                        teamSpeakTarget = teamSpeakTarget.trim(),
                                        channel = channel.trim(),
                                        serverPassword = serverPassword,
                                        inviteToken = inviteToken.trim(),
                                        rememberIdentity = rememberIdentity,
                                    ),
                                )
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            enabled = !connecting && gateway.isNotBlank() && nickname.isNotBlank(),
                            shape = RoundedCornerShape(18.dp),
                            contentPadding = ButtonDefaults.ContentPadding,
                        ) {
                            if (connecting) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                Spacer(Modifier.width(12.dp))
                                 Text(stringResource(R.string.connection_connecting))
                            } else {
                                 Text(stringResource(R.string.connection_join), fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.width(10.dp))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                            }
                        }
                        if (connecting) {
                            TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                                 Text(stringResource(R.string.connection_cancel))
                            }
                        }
                    }
                }

            }
        }
    }
}

@Composable
private fun BrandHero() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(92.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.webspeak_browser_icon),
                contentDescription = "WebSpeak",
                modifier = Modifier.size(86.dp).clip(RoundedCornerShape(26.dp)),
            )
        }
        Spacer(Modifier.height(13.dp))
        Text("WebSpeak", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp)
        Text(stringResource(R.string.connection_tagline), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun localizedConnectionError(code: String): String {
    val resource = when (code.uppercase()) {
        "MICROPHONE_PERMISSION_REQUIRED" -> R.string.error_microphone_permission
        "GATEWAY_CONFIG_FAILED" -> R.string.error_gateway_config
        "ORIGIN_REJECTED" -> R.string.error_origin_rejected
        "NOT_INITIALIZED" -> R.string.error_gateway_uninitialized
        "RATE_LIMITED" -> R.string.error_rate_limited
        "TARGET_NOT_ALLOWED" -> R.string.error_target_not_allowed
        "ACCELERATION_UNAVAILABLE" -> R.string.error_acceleration_unavailable
        "INVALID_NICKNAME" -> R.string.error_invalid_nickname
        "INVITE_INVALID" -> R.string.error_invite_invalid
        "JOIN_TICKET_FAILED" -> R.string.error_join_ticket
        "INVALID_GATEWAY_URL" -> R.string.error_invalid_gateway
        "INVALID_GATEWAY_SUBPATH" -> R.string.error_gateway_subpath
        "INVALID_GATEWAY_CREDENTIALS" -> R.string.error_gateway_credentials
        "INSECURE_GATEWAY_URL" -> R.string.error_https_required
        "INVALID_TARGET" -> R.string.error_invalid_target
        "GATEWAY_HANDSHAKE_TIMEOUT" -> R.string.error_handshake_timeout
        "GATEWAY_CONNECTION_FAILED", "GATEWAY_CONNECTION_CLOSED", "GATEWAY_NETWORK_LOST", "GATEWAY_SESSION_ENDED" -> R.string.error_gateway_connection
        "SERVER_PASSWORD_REQUIRED" -> R.string.error_server_password_required
        "INVALID_SERVER_PASSWORD" -> R.string.error_server_password_invalid
        "IDENTITY_IN_USE" -> R.string.error_identity_in_use
        "IDENTITY_REJECTED", "IDENTITY_INVALID" -> R.string.error_identity_invalid
        "SERVER_REJECTED", "CHANNEL_FULL" -> R.string.error_server_rejected
        "JOIN_TICKET_REQUIRED" -> R.string.error_ticket_invalid
        "TEAM_SPEAK_CLIENT_UNAVAILABLE" -> R.string.error_teamspeak_unavailable
        "WEBRTC_NEGOTIATION_FAILED" -> R.string.error_webrtc_negotiation
        else -> null
    }
    return resource?.let { stringResource(it) } ?: stringResource(R.string.error_connection_generic)
}

package com.echosixhiya.webspeak.android.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.echosixhiya.webspeak.android.R
import com.echosixhiya.webspeak.android.locale.AppLocaleContext
import com.echosixhiya.webspeak.android.service.VoiceSessionService

private data class LanguageOption(val tag: String, val label: String)

private val languageOptions = listOf(
    LanguageOption("", "system"),
    LanguageOption("zh-CN", "中文（简体）"),
    LanguageOption("en", "English"),
    LanguageOption("fr", "Français"),
    LanguageOption("de", "Deutsch"),
    LanguageOption("ja", "日本語"),
    LanguageOption("es", "Español"),
)

@Composable
fun AppLanguageSelector(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val currentTag = AppLocaleContext.applicationLanguageTag(context)
        .substringBefore(',')
        .lowercase()
    val selected = languageOptions.firstOrNull {
        it.tag.equals(currentTag, ignoreCase = true) || currentTag.startsWith("${it.tag.lowercase()}-")
    }
        ?: languageOptions.first()
    val selectedLabel = if (selected.tag.isEmpty()) stringResource(R.string.language_system_default) else selected.label

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(stringResource(R.string.language_setting_title), style = MaterialTheme.typography.titleSmall)
            Box {
                TextButton(onClick = { expanded = true }) {
                    Text("$selectedLabel ▾", color = MaterialTheme.colorScheme.primary)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    languageOptions.forEach { option ->
                        val label = if (option.tag.isEmpty()) stringResource(R.string.language_system_default) else option.label
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                expanded = false
                                AppLocaleContext.setApplicationLanguage(context, option.tag)
                                VoiceSessionService.refreshLocalizedNotification(context)
                            },
                        )
                    }
                }
            }
        }
    }
}

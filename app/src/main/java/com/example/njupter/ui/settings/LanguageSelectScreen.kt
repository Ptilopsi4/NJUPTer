package com.example.njupter.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.njupter.R
import com.example.njupter.ui.settings.component.SettingsSectionCard
import com.example.njupter.ui.settings.model.SettingsItem
import com.example.njupter.ui.settings.model.SettingsIcon
import com.example.njupter.ui.settings.model.SettingsSection
import androidx.compose.ui.tooling.preview.Preview
import com.example.njupter.ui.theme.NJUPTerTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSelectScreen(
    currentLanguageTag: String,
    onBack: () -> Unit,
    onSelectLanguage: (String) -> Unit
) {
    val normalizedLanguageTag = if (currentLanguageTag.startsWith("zh")) "zh" else "en"
    val isSystemSelected = currentLanguageTag.isEmpty() || currentLanguageTag == "system"
    val isZhSelected = normalizedLanguageTag == "zh" && !isSystemSelected
    val isEnSelected = normalizedLanguageTag == "en" && !isSystemSelected

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.language)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
            }
        )
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SettingsSectionCard(
                    section = SettingsSection(
                        title = stringResource(R.string.language),
                        items = listOf(
                            SettingsItem.Navigation(
                                icon = SettingsIcon.Drawable(R.drawable.ic_language_follow),
                                title = stringResource(R.string.language_system),
                                selected = isSystemSelected,
                                onClick = { onSelectLanguage("system") }
                            ),
                            SettingsItem.Navigation(
                                icon = SettingsIcon.Drawable(R.drawable.ic_language_cn),
                                title = stringResource(R.string.language_zh),
                                selected = isZhSelected,
                                onClick = { onSelectLanguage("zh") }
                            ),
                            SettingsItem.Navigation(
                                icon = SettingsIcon.Drawable(R.drawable.ic_language_us),
                                title = stringResource(R.string.language_en),
                                selected = isEnSelected,
                                onClick = { onSelectLanguage("en") }
                            )
                        )
                    )
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Language - System")
@Composable
fun LanguageSelectScreenPreviewSystem() {
    NJUPTerTheme {
        LanguageSelectScreen(
            currentLanguageTag = "system",
            onBack = {},
            onSelectLanguage = {}
        )
    }
}

@Preview(showBackground = true, name = "Language - Chinese")
@Composable
fun LanguageSelectScreenPreviewZh() {
    NJUPTerTheme {
        LanguageSelectScreen(
            currentLanguageTag = "zh",
            onBack = {},
            onSelectLanguage = {}
        )
    }
}

@Preview(showBackground = true, name = "Language - English")
@Composable
fun LanguageSelectScreenPreviewEn() {
    NJUPTerTheme {
        LanguageSelectScreen(
            currentLanguageTag = "en",
            onBack = {},
            onSelectLanguage = {}
        )
    }
}

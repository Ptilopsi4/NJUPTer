/*
    数据层：定义条目长什么样
 */
package com.example.njupter.ui.settings.model

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.vector.ImageVector

data class SettingsSection(
    val title: String,
    val items: List<SettingsItem>
)

sealed interface SettingsIcon {
    data class Vector(val imageVector: ImageVector) : SettingsIcon

    data class Drawable(@param:DrawableRes val resId: Int) : SettingsIcon
}

sealed interface SettingsItem {
    val icon: SettingsIcon
    val title: String
    val description: String?

    data class Toggle(
        override val icon: SettingsIcon,
        override val title: String,
        override val description: String? = null,
        val checked: Boolean,
        val onToggle: () -> Unit
    ) : SettingsItem

    data class Navigation(
        override val icon: SettingsIcon,
        override val title: String,
        override val description: String? = null,
        val value: String? = null,
        val emphasized: Boolean = false,
        val onClick: () -> Unit
    ) : SettingsItem
}


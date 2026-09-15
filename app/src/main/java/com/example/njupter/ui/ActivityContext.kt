package com.example.njupter.ui

import android.content.Context
import androidx.compose.runtime.compositionLocalOf

/**
 * 语言切换会把 LocalContext 覆写为 createConfigurationContext 的结果（非 Activity，
 * 也无法沿 Context 链找回 Activity），而 startActivity / ActivityResult 注册需要真 Activity。
 * 在组合根（覆写发生前）捕获 Activity 并通过此 local 下发。
 */
val LocalActivityContext = compositionLocalOf<Context?> { null }

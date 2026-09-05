package com.example.njupter.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.example.njupter.R

/**
 * requestPinAppWidget 的 successCallback 落点：
 * 用户在系统弹窗里确认添加后由 launcher 回调，给出已添加反馈。
 */
class WidgetPinResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Toast.makeText(context, R.string.widget_pinned, Toast.LENGTH_SHORT).show()
    }
}

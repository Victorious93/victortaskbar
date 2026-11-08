package com.victorious.taskbar.service

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import com.victorious.taskbar.MainActivity
import com.victorious.taskbar.Permissions
import com.victorious.taskbar.R

/**
 * 3x1 home-screen widget: shows whether the taskbar runs and toggles it. A widget tap is one of
 * Android's allowed ways to start a foreground service from the background.
 */
class TaskbarWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = refresh(ctx)

    companion object {
        fun refresh(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, TaskbarWidget::class.java))
            if (ids.isEmpty()) return
            val running = TaskbarService.running.value
            val canRun = Permissions.canDrawOverlays(ctx)
            val v = RemoteViews(ctx.packageName, R.layout.widget_toggle)
            v.setTextViewText(R.id.widget_status, when {
                !canRun -> "Setup needed — tap to open"
                running -> "Running"
                else -> "Off"
            })
            v.setTextViewText(R.id.widget_button, if (running) "Stop" else "Start")
            v.setInt(R.id.widget_button, "setBackgroundResource", if (running) R.drawable.widget_btn_off else R.drawable.widget_btn_on)
            v.setTextColor(R.id.widget_button, if (running) 0xFFF5F7FF.toInt() else 0xFF000000.toInt())

            val toggle = Intent(ctx, TaskbarService::class.java).setAction(TaskbarService.ACTION_TOGGLE_RUNNING)
            val buttonPi = if (!canRun) {
                PendingIntent.getActivity(ctx, 10, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            } else if (Build.VERSION.SDK_INT >= 26) {
                PendingIntent.getForegroundService(ctx, 11, toggle, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            } else {
                PendingIntent.getService(ctx, 11, toggle, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            }
            v.setOnClickPendingIntent(R.id.widget_button, buttonPi)
            v.setOnClickPendingIntent(R.id.widget_root,
                PendingIntent.getActivity(ctx, 12, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            mgr.updateAppWidget(ids, v)
        }
    }
}

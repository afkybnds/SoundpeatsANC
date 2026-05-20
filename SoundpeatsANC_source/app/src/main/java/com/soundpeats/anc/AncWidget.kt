package com.soundpeats.anc

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetManager.ACTION_APPWIDGET_RESTORED
import android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews

class AncWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_SET_MODE = "com.soundpeats.anc.SET_MODE"
        const val EXTRA_MODE = "mode"

        fun updateAllWidgets(context: Context) {
            val awm = AppWidgetManager.getInstance(context)
            val ids = awm.getAppWidgetIds(ComponentName(context, AncWidget::class.java))
            for (id in ids) updateWidget(context, awm, id)
        }

        fun updateWidget(context: Context, awm: AppWidgetManager, id: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_anc)

            listOf(
                R.id.pos_anc    to BleCommandService.MODE_ANC,
                R.id.pos_normal to BleCommandService.MODE_NORMAL,
                R.id.pos_pass   to BleCommandService.MODE_TRANSPARENCY
            ).forEach { (viewId, tapMode) ->
                val intent = Intent(context, AncWidget::class.java).apply {
                    action = ACTION_SET_MODE
                    putExtra(EXTRA_MODE, tapMode)
                }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                views.setOnClickPendingIntent(viewId,
                    PendingIntent.getBroadcast(context, tapMode, intent, flags))
            }

            awm.updateAppWidget(id, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        // Redraw after NothingOS restores/wakes the widget from hibernation
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_RESTORED ||
            intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            updateAllWidgets(context)
            return
        }

        if (intent.action != ACTION_SET_MODE) return
        val mode = intent.getIntExtra(EXTRA_MODE, -1).takeIf { it >= 0 } ?: return

        val svc = Intent(context, BleCommandService::class.java)
            .putExtra(BleCommandService.EXTRA_MODE, mode)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(svc)
        else context.startService(svc)
    }

    override fun onUpdate(context: Context, awm: AppWidgetManager, ids: IntArray) {
        for (id in ids) updateWidget(context, awm, id)
    }
}

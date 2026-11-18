/*
 * Copyright (C) 2026 KuroDeck contributors
 * Licensed under the Apache License, Version 2.0.
 */
package com.kurodeck.hud

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Notification access for the HUD panel. Separate from Launcher3's notification-dot listener,
 * which unbinds itself whenever dots are turned off. The system delivers the callbacks on the
 * main thread.
 */
class HudNotificationService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        notifyChanged()
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
        notifyChanged()
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = notifyChanged()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = notifyChanged()
    override fun onNotificationRankingUpdate(rankingMap: RankingMap?) = notifyChanged()

    companion object {
        private const val TAG = "HudNotificationService"
        @Volatile private var instance: HudNotificationService? = null
        private val listeners = CopyOnWriteArrayList<() -> Unit>()

        fun addListener(l: () -> Unit) = listeners.add(l)
        fun removeListener(l: () -> Unit) = listeners.remove(l)
        private fun notifyChanged() = listeners.forEach { it() }

        fun component(ctx: Context) = ComponentName(ctx, HudNotificationService::class.java)

        fun isGranted(ctx: Context): Boolean =
            ctx.getSystemService(NotificationManager::class.java)
                ?.isNotificationListenerAccessGranted(component(ctx)) == true

        fun isConnected() = instance != null

        /** Opens the system screen where the user grants KuroDeck notification access. */
        fun requestAccess(ctx: Context) {
            val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    component(ctx).flattenToString())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                ctx.startActivity(detail)
            } catch (e: Exception) {
                ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }

        /**
         * Current notifications, newest first. Group summaries are dropped when their children
         * are present, so each message shows once.
         */
        fun active(): List<StatusBarNotification> {
            val svc = instance ?: return emptyList()
            val all = try {
                svc.activeNotifications?.toList().orEmpty()
            } catch (e: Exception) {
                Log.w(TAG, "activeNotifications failed", e)
                return emptyList()
            }
            val groupsWithChildren = all
                .filter { it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 }
                .mapNotNull { it.groupKey }
                .toSet()
            return all
                .filter {
                    val summary = it.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
                    !(summary && it.groupKey in groupsWithChildren)
                }
                .sortedByDescending { it.postTime }
        }

        fun dismiss(sbn: StatusBarNotification) {
            try { instance?.cancelNotification(sbn.key) } catch (e: Exception) {
                Log.w(TAG, "dismiss failed", e)
            }
        }

        fun dismissAll() {
            try { instance?.cancelAllNotifications() } catch (e: Exception) {
                Log.w(TAG, "dismissAll failed", e)
            }
        }
    }
}

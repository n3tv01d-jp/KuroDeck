/*
 * Copyright (C) 2026 KuroDeck contributors
 * Licensed under the Apache License, Version 2.0.
 */
package com.kurodeck.hud

import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.util.Log

/** Helpers for the provider-info objects Launcher3 fabricates for in-process widgets. */
object HudWidgetInfo {
    private const val TAG = "KuroDeckWidgets"

    /** Points [info]'s backing ActivityInfo at this app so resource lookups use our package. */
    @JvmStatic
    fun retarget(context: Context, info: AppWidgetProviderInfo) {
        val ai: ActivityInfo = info.activityInfo ?: return
        ai.applicationInfo = context.applicationInfo
        ai.packageName = context.packageName
    }

    /** A blank provider info, used when the device has no third-party widget providers. */
    @JvmStatic
    fun emptyTemplate(context: Context, cn: ComponentName): AppWidgetProviderInfo? = try {
        AppWidgetProviderInfo().also { info ->
            info.provider = cn
            val ai = ActivityInfo().apply {
                applicationInfo = context.applicationInfo
                packageName = context.packageName
                name = cn.className
            }
            AppWidgetProviderInfo::class.java.getField("providerInfo").set(info, ai)
        }
    } catch (t: Throwable) {
        Log.w(TAG, "Cannot build widget template", t)
        null
    }
}

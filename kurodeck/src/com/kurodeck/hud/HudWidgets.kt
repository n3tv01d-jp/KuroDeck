/*
 * Copyright (C) 2026 KuroDeck contributors
 * Licensed under the Apache License, Version 2.0.
 */
package com.kurodeck.hud

import android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import com.android.launcher3.dagger.LauncherComponentProvider.appComponent
import com.android.launcher3.widget.LauncherAppWidgetHostView
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo
import com.android.launcher3.widget.custom.CustomWidget
import com.android.launcher3.widget.custom.CustomWidgetManager.NAMED_CUSTOM_WIDGETS
import dagger.Module
import dagger.Provides
import dagger.multibindings.ElementsIntoSet
import javax.inject.Named

/**
 * HUD panels exposed to Launcher3 as built-in "custom widgets": they appear in the widget picker
 * under KuroDeck, can be placed, moved and resized like any widget, and are drawn in-process.
 */
abstract class HudWidget(
    final override val id: String,
    private val label: String,
    private val spanY: Int,
    private val minSpanX: Int,
    private val minSpanY: Int,
    private val previewRes: Int,
) : CustomWidget {

    abstract fun createPanel(context: Context): HudPanelView

    override fun updateWidgetInfo(context: Context, info: LauncherAppWidgetProviderInfo) {
        val idp = context.appComponent.idp
        info.label = label
        info.resizeMode = AppWidgetProviderInfo.RESIZE_BOTH
        info.spanX = idp.numColumns
        info.spanY = spanY
        info.minSpanX = minSpanX.coerceAtMost(idp.numColumns)
        info.minSpanY = minSpanY
        info.maxSpanX = idp.numColumns
        info.maxSpanY = idp.numRows
        info.previewImage = previewRes
        info.icon = com.android.launcher3.R.drawable.ic_kurodeck_hud
    }

    override fun createView(context: Context, info: LauncherAppWidgetProviderInfo) =
        LauncherAppWidgetHostView(context).apply {
            setAppWidget(INVALID_APPWIDGET_ID, info)
            addView(createPanel(context), FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        }

    companion object {
        /** Component package used by Launcher3 for in-process widgets. */
        const val PKG = LauncherAppWidgetProviderInfo.CUSTOM_WIDGET_PACKAGE
    }
}

object CoreWidget : HudWidget("kurodeck-core", "CORE // 炉心", 2, 3, 2,
    com.android.launcher3.R.drawable.kd_preview_core) {
    override fun createPanel(context: Context) = CorePanelView(context)
}

object NavigatorWidget : HudWidget("kurodeck-navigator", "NAVIGATOR // 案内役", 1, 3, 1,
    com.android.launcher3.R.drawable.kd_preview_navigator) {
    override fun createPanel(context: Context) = NavigatorPanelView(context)
}

object NetworkWidget : HudWidget("kurodeck-network", "NETWORK // 通信", 1, 3, 1,
    com.android.launcher3.R.drawable.kd_preview_network) {
    override fun createPanel(context: Context) = NetPanelView(context)
}

object FleetWidget : HudWidget("kurodeck-fleet", "FLEET // 艦隊", 2, 3, 1,
    com.android.launcher3.R.drawable.kd_preview_fleet) {
    override fun createPanel(context: Context) = FleetPanelView(context)
}

/** Dagger bindings that add the HUD widgets to Launcher3's custom-widget set. */
@Module
object KuroDeckWidgetModule {
    @Provides
    @ElementsIntoSet
    @Named(NAMED_CUSTOM_WIDGETS)
    @JvmStatic
    fun hudWidgets(): Set<CustomWidget> = setOf(CoreWidget, NavigatorWidget, NetworkWidget, FleetWidget)
}

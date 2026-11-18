/*
 * Copyright (C) 2026 KuroDeck contributors
 * Licensed under the Apache License, Version 2.0.
 */
package com.kurodeck.hud

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.kurodeck.theme.HudPalette
import com.kurodeck.theme.HudTheme

/**
 * Base for the canvas-drawn HUD panels. Handles the palette, telemetry subscription (only while
 * the panel is actually visible on screen) and Hearth's `panelIn` entrance animation.
 */
abstract class HudPanelView(context: Context) : View(context) {
    protected val hud = HudDraw(context)
    protected var pal: HudPalette = HudTheme.current(context)
    protected var sample: TelemetrySample? = Telemetry.latest
    private var subscribed = false

    /** 0→1 entrance progress (`@keyframes panelIn`: fade, rise and brighten). */
    protected var enter = 0f

    private val onTheme: (HudPalette) -> Unit = { p -> post { pal = p; invalidate() } }
    private val onSample: (TelemetrySample) -> Unit = { s -> sample = s; onTelemetry(s) }

    /** Whether this panel needs telemetry ticks. */
    protected open val wantsTelemetry = true

    protected open fun onTelemetry(s: TelemetrySample) = invalidate()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        HudTheme.addListener(onTheme)
        pal = HudTheme.current(context)
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 700
            interpolator = DecelerateInterpolator(2f)
            addUpdateListener { enter = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        HudTheme.removeListener(onTheme)
        setSubscribed(false)
        super.onDetachedFromWindow()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        setSubscribed(isVisible && wantsTelemetry)
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        setSubscribed(visibility == VISIBLE && isShown && wantsTelemetry)
    }

    private fun setSubscribed(on: Boolean) {
        if (on == subscribed) return
        subscribed = on
        if (on) Telemetry.subscribe(context, onSample) else Telemetry.unsubscribe(onSample)
    }

    final override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return
        val pad = context.dp(2f)
        canvas.save()
        canvas.translate(0f, (1f - enter) * context.dp(10f))
        val layer = if (enter < 1f) canvas.saveLayerAlpha(0f, 0f, w, h, (enter * 255).toInt())
            else -1
        hud.panel(canvas, pal, pad, pad, w - pad, h - pad)
        drawContent(canvas, pad + context.dp(12f), pad + context.dp(10f),
            w - pad - context.dp(12f), h - pad - context.dp(12f))
        if (layer >= 0) canvas.restoreToCount(layer)
        canvas.restore()
    }

    /** Draws the panel body inside the given content box. */
    protected abstract fun drawContent(c: Canvas, l: Float, t: Float, r: Float, b: Float)
}

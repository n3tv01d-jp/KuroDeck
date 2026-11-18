/*
 * Copyright (C) 2026 KuroDeck contributors
 * Licensed under the Apache License, Version 2.0.
 */
package com.kurodeck.hud

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import com.kurodeck.theme.HudPalette
import com.kurodeck.theme.HudPalette.Companion.withAlpha
import com.kurodeck.theme.HudTheme
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Hearth's background layers over the wallpaper: two accent glows (`body` radial gradients),
 * the hex lattice (`.bg-hex`, masked to fade at the edges), CRT scanlines (`.bg-scan`) and the
 * slow scan sweep. Sits behind the workspace inside Launcher3's DragLayer.
 */
class HudBackdropView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    View(context, attrs) {

    private var pal: HudPalette = HudTheme.current(context)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hex = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mask = Paint(Paint.ANTI_ALIAS_FLAG)
    private val scan = Paint()
    private val sweep = Paint()
    private var sweepY = 0f
    private var sweepAnim: ValueAnimator? = null
    private var enabled = HudTheme.isEnabled(context, HudTheme.KEY_BACKDROP)

    private val onTheme: (HudPalette) -> Unit = { p -> post { pal = p; rebuild() } }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        isClickable = false
        isFocusable = false
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        HudTheme.addListener(onTheme)
        all.add(WeakReference(this))
    }

    override fun onDetachedFromWindow() {
        HudTheme.removeListener(onTheme)
        all.removeAll { it.get() == null || it.get() === this }
        stopSweep()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible && enabled) startSweep() else stopSweep()
    }

    private fun startSweep() {
        if (sweepAnim != null || height == 0) return
        sweepAnim = ValueAnimator.ofFloat(-context.dp(200f), height + context.dp(200f)).apply {
            duration = 9000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { sweepY = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    private fun stopSweep() {
        sweepAnim?.cancel()
        sweepAnim = null
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        rebuild()
        if (isShown && enabled) { stopSweep(); startSweep() }
    }

    private fun rebuild() {
        enabled = HudTheme.isEnabled(context, HudTheme.KEY_BACKDROP)
        if (width == 0 || height == 0) return
        // Hex tile: the same 56×97 path as Hearth's SVG mask, scaled to dp.
        val s = context.dp(1f) * .9f
        val tw = (56 * s).toInt().coerceAtLeast(1)
        val th = (97 * s).toInt().coerceAtLeast(1)
        val tile = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
        val tc = Canvas(tile)
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = s; color = withAlpha(pal.a1, .10f)
        }
        val path = Path().apply {
            moveTo(28 * s, 0f); lineTo(56 * s, 16 * s); lineTo(56 * s, 48 * s)
            lineTo(28 * s, 64 * s); lineTo(0f, 48 * s); lineTo(0f, 16 * s); close()
            moveTo(28 * s, 64 * s); lineTo(28 * s, 97 * s)
            moveTo(0f, 48 * s); lineTo(0f, 97 * s)
            moveTo(56 * s, 48 * s); lineTo(56 * s, 97 * s)
        }
        tc.drawPath(path, tp)
        hex.shader = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        val r = maxOf(width, height) * .62f
        mask.shader = RadialGradient(width / 2f, height / 2f, r,
            intArrayOf(withAlpha(pal.bg, 0f), withAlpha(pal.bg, 0f), withAlpha(pal.bg, .9f)),
            floatArrayOf(0f, .35f, 1f), Shader.TileMode.CLAMP)

        val line = Bitmap.createBitmap(1, 3, Bitmap.Config.ARGB_8888)
        line.setPixel(0, 0, withAlpha(0xFFFFFFFF.toInt(), .025f))
        scan.shader = BitmapShader(line, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        sweep.shader = LinearGradient(0f, 0f, 0f, context.dp(180f),
            intArrayOf(0, withAlpha(pal.a1, .06f), 0), null, Shader.TileMode.CLAMP)
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        // Top-right secondary glow, bottom-left primary glow; a light veil to calm wallpapers.
        c.drawColor(withAlpha(pal.bg, .28f))
        glow.shader = RadialGradient(w * .85f, -h * .08f, w * 1.1f,
            withAlpha(pal.a2, .16f), 0, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, glow)
        glow.shader = RadialGradient(-w * .1f, h * 1.08f, w * 1.05f,
            withAlpha(pal.a1, .14f), 0, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, glow)
        if (!enabled) return
        c.drawRect(0f, 0f, w, h, hex)
        c.drawRect(0f, 0f, w, h, mask)
        c.drawRect(0f, 0f, w, h, scan)
        c.save()
        c.translate(0f, sweepY)
        c.drawRect(0f, 0f, w, context.dp(180f), sweep)
        c.restore()
    }

    companion object {
        private val all = CopyOnWriteArrayList<WeakReference<HudBackdropView>>()

        /** Re-reads the backdrop switch on every attached instance. */
        fun refreshAll() = all.forEach { ref ->
            ref.get()?.let { v -> v.post { v.rebuild(); if (v.enabled && v.isShown) v.startSweep() else v.stopSweep() } }
        }
    }
}

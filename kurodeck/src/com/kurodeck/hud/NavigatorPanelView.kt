/*
 * Copyright (C) 2026 KuroDeck contributors
 * Licensed under the Apache License, Version 2.0.
 */
package com.kurodeck.hud

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.kurodeck.theme.HudPalette
import com.kurodeck.theme.HudPalette.Companion.withAlpha
import com.kurodeck.theme.HudTheme
import kotlin.math.min
import kotlin.math.sin

/**
 * `07 NAVIGATOR 案内役`: KAGARI's screen (a small chamfered monitor showing her kaomoji) and her
 * speech line, typed out character by character like Hearth's `typeTo()`.
 */
class NavigatorPanelView(context: Context) : HudPanelView(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private var line: NavLine? = null
    private var typedAt = 0L
    private var layout: StaticLayout? = null
    private var layoutKey = ""
    override val wantsHub = true
    override fun onHub() = refresh()

    init {
        isClickable = true
        setOnClickListener { Kagari.bump(); refresh() }
        contentDescription = "KAGARI navigator"
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Kagari.attach(context)
        refresh()
    }

    override fun onDetachedFromWindow() {
        Kagari.detach()
        super.onDetachedFromWindow()
    }

    override fun onTelemetry(s: TelemetrySample) = refresh()

    private fun refresh() {
        val n = Kagari.state(sample, HudTheme.isEnabled(context, HudTheme.KEY_KAGARI))
        if (n.key != line?.key) {
            line = n
            typedAt = SystemClock.uptimeMillis()
            contentDescription = "KAGARI: ${n.en}"
        }
        invalidate()
    }

    private fun moodColor(m: String) = when (m) {
        "crit" -> HudPalette.RED
        "warn" -> HudPalette.AMBER
        "happy" -> pal.a2
        else -> HudPalette.GREEN
    }

    override fun drawContent(c: Canvas, l: Float, t: Float, r: Float, b: Float) {
        val n = line ?: return
        val mood = moodColor(n.mood)
        val compact = b - t < context.dp(110f)
        val top = if (compact) t else hud.header(c, pal, "07", "NAVIGATOR", "案内役", null, l, t, r)

        // Face monitor.
        val fh = b - top
        val fw = min(fh * 1.25f, (r - l) * .36f)
        hud.chamfer(path, l, top, l + fw, b, context.dp(8f))
        p.reset(); p.isAntiAlias = true
        p.color = withAlpha(pal.bg, .7f); p.style = Paint.Style.FILL
        c.drawPath(path, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = context.dp(1f)
        p.color = if (n.mood == "crit") HudPalette.RED else pal.line2
        c.drawPath(path, p)
        // Scanlines inside the monitor.
        p.color = withAlpha(0xFFFFFFFF.toInt(), .035f)
        var y = top + context.dp(2f)
        c.save(); c.clipPath(path)
        while (y < b) { c.drawLine(l, y, l + fw, y, p); y += context.dp(3f) }
        c.restore()

        val now = SystemClock.uptimeMillis()
        val shake = if (n.mood == "crit") sin(now / 25.0).toFloat() * context.dp(1.5f) else 0f
        val kaoColor = when (n.mood) { "ok" -> pal.a1; else -> mood }
        var ks = if (compact) 17f else 21f
        hud.font(p, HudDraw.Font.UI_BOLD, ks, kaoColor)
        while (p.measureText(n.kao) > fw - context.dp(10f) && ks > 9f) {
            ks -= 1f; p.textSize = context.sp(ks)
        }
        val kx = l + (fw - p.measureText(n.kao)) / 2f + shake
        val ky = top + fh / 2f + p.textSize * .35f - if (compact) 0f else context.dp(6f)
        hud.glowText(c, n.kao, kx, ky, p, kaoColor, 8f)
        if (!compact) {
            hud.font(p, HudDraw.Font.DISPLAY, 8f, pal.faint, 700, .3f)
            val nm = "KAGARI-AI"
            c.drawText(nm, l + (fw - p.measureText(nm)) / 2f, b - context.dp(8f), p)
        }

        // Speech.
        val x = l + fw + context.dp(12f)
        val w = (r - x).toInt().coerceAtLeast(1)
        hud.font(p, HudDraw.Font.DISPLAY, 9f, mood, 700, .2f)
        val tag = "● ${n.label}"
        val tagY = top + context.dp(11f)
        c.drawText(tag, x, tagY, p)
        hud.font(p, HudDraw.Font.JP, 9.5f, pal.dim, spacing = .1f)
        c.drawText(n.jp, r - p.measureText(n.jp), tagY, p)

        val chars = n.en.codePointCount(0, n.en.length)
        val shown = min(chars, ((now - typedAt) / 20L).toInt())
        val end = n.en.offsetByCodePoints(0, shown)
        val text = n.en.substring(0, end)
        tp.typeface = HudTheme.ui(context)
        tp.textSize = context.sp(if (compact) 13.5f else 15f)
        tp.color = pal.text
        val maxLines = ((b - tagY - context.dp(6f)) / (tp.textSize * 1.15f)).toInt().coerceAtLeast(1)
        val key = "$text|$w|$maxLines|${pal.id}"
        if (key != layoutKey) {
            layout = StaticLayout.Builder.obtain(text, 0, text.length, tp, w)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.0f)
                .setMaxLines(maxLines)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build()
            layoutKey = key
        }
        c.save()
        c.translate(x, tagY + context.dp(6f))
        layout?.draw(c)
        c.restore()

        // Blinking caret while typing; keep animating until the line is complete.
        if (shown < chars || n.mood == "crit") {
            postInvalidateOnAnimation()
        }
    }
}

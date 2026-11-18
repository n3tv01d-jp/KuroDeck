/*
 * Copyright (C) 2026 KuroDeck contributors
 * Licensed under the Apache License, Version 2.0.
 */
package com.kurodeck.hud

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import com.kurodeck.theme.HudPalette.Companion.withAlpha
import kotlin.math.max

/** `02 NETWORK 通信`: live RX/TX readouts and Hearth's 120 s throughput chart. */
class NetPanelView(context: Context) : HudPanelView(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ln = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    override fun drawContent(c: Canvas, l: Float, t: Float, r: Float, b: Float) {
        val s = sample
        val top = hud.header(c, pal, "02", "NETWORK", "通信", s?.link ?: "—", l, t, r)
        val readW = (r - l) * .34f

        // `.big` readouts with a left accent bar and a fading tint.
        val h = (b - top - context.dp(6f)) / 2f
        big(c, "RX ↓", s?.let { Telemetry.rate(it.rxRate) } ?: "—",
            "Σ " + (s?.let { Telemetry.bytes(it.rxTotal) } ?: "—"), l, top, l + readW, top + h, pal.a1)
        big(c, "TX ↑", s?.let { Telemetry.rate(it.txRate) } ?: "—",
            "Σ " + (s?.let { Telemetry.bytes(it.txTotal) } ?: "—"), l, top + h + context.dp(6f),
            l + readW, b, pal.a2)

        // Chart.
        val cl = l + readW + context.dp(10f)
        ln.strokeWidth = context.dp(1f); ln.color = pal.line; ln.shader = null; ln.pathEffect = null
        c.drawRect(cl, top, r, b, ln)
        for (i in 1..3) {
            val y = top + (b - top) * i / 4f
            ln.color = withAlpha(pal.a1, .06f)
            c.drawLine(cl, y, r, y, ln)
        }
        if (s == null) return
        val peak = max(1f, max(s.rxHistory.max(), s.txHistory.max()))
        series(c, s.txHistory, peak, cl, top, r, b, pal.a2)
        series(c, s.rxHistory, peak, cl, top, r, b, pal.a1)
        hud.font(p, HudDraw.Font.MONO, 9f, pal.dim, spacing = .1f)
        val lg = "PEAK ${Telemetry.rate(peak.toLong())} · 120s"
        c.drawText(lg, r - p.measureText(lg) - context.dp(6f), top + context.dp(12f), p)
    }

    private fun big(c: Canvas, label: String, value: String, sub: String,
            l: Float, t: Float, r: Float, b: Float, color: Int) {
        fill.shader = LinearGradient(l, 0f, r, 0f, withAlpha(color, .10f), 0, Shader.TileMode.CLAMP)
        c.drawRect(l, t, r, b, fill)
        fill.shader = null; fill.color = color
        c.drawRect(l, t, l + context.dp(3f), b, fill)
        val x = l + context.dp(10f)
        hud.font(p, HudDraw.Font.DISPLAY, 8f, pal.faint, 600, .2f)
        c.drawText(label, x, t + context.dp(12f), p)
        hud.font(p, HudDraw.Font.DISPLAY, 17f, color, 700)
        hud.glowText(c, value, x, t + context.dp(31f), p, withAlpha(color, .55f), 8f)
        if (b - t > context.dp(46f)) {
            hud.font(p, HudDraw.Font.MONO, 10f, pal.dim)
            c.drawText(sub, x, t + context.dp(45f), p)
        }
    }

    private fun series(c: Canvas, d: FloatArray, peak: Float, l: Float, t: Float, r: Float,
            b: Float, color: Int) {
        val n = d.size
        val inset = context.dp(4f)
        val top = t + context.dp(16f)
        path.reset()
        for (i in 0 until n) {
            val x = l + (r - l) * i / (n - 1)
            val y = b - inset - (b - inset - top) * (d[i] / peak)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        ln.color = color
        ln.strokeWidth = context.dp(1.5f)
        ln.setShadowLayer(context.dp(4f), 0f, 0f, color)
        c.drawPath(path, ln)
        ln.clearShadowLayer()
        path.lineTo(r, b); path.lineTo(l, b); path.close()
        fill.shader = LinearGradient(0f, top, 0f, b, withAlpha(color, .22f), 0, Shader.TileMode.CLAMP)
        c.drawPath(path, fill)
        fill.shader = null
    }
}

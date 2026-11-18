/*
 * Copyright (C) 2026 KuroDeck contributors
 * Licensed under the Apache License, Version 2.0.
 */
package com.kurodeck.hud

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Build
import com.kurodeck.theme.HudPalette
import com.kurodeck.theme.HudPalette.Companion.withAlpha
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

/**
 * `01 CORE 炉心`: Hearth's reactor ring re-purposed for a phone. The outer ring is battery charge,
 * the inner ring memory pressure; next to it sit the clock and a key/value grid (`.kv`).
 */
class CorePanelView(context: Context) : HudPanelView(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.BUTT
    }
    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val secFmt = SimpleDateFormat(":ss", Locale.ROOT)
    private val dateFmt = SimpleDateFormat("yyyy.MM.dd  EEE", Locale.ENGLISH)
    private val model = (Build.MODEL ?: "DEVICE").uppercase(Locale.ROOT)

    override fun drawContent(c: Canvas, l: Float, t: Float, r: Float, b: Float) {
        val top = hud.header(c, pal, "01", "CORE", "炉心", model, l, t, r)
        val s = sample
        val h = b - top
        val d = min(h, (r - l) * .42f)
        val cx = l + d / 2f
        val cy = top + h / 2f
        reactor(c, cx, cy, d / 2f, s)

        // Clock + date on the right.
        val x = l + d + context.dp(14f)
        val now = Date()
        hud.font(p, HudDraw.Font.DISPLAY, if (h > context.dp(120f)) 30f else 24f, pal.a1, 700, .06f)
        val time = timeFmt.format(now)
        val ty = top + p.textSize
        hud.glowText(c, time, x, ty, p, pal.a1d, 10f)
        val tw = p.measureText(time)
        hud.font(p, HudDraw.Font.DISPLAY, 13f, pal.a1, 500)
        c.drawText(secFmt.format(now), x + tw + context.dp(2f), ty, p)
        hud.font(p, HudDraw.Font.MONO, 11f, pal.dim, spacing = .2f)
        val dy = ty + context.dp(16f)
        c.drawText(dateFmt.format(now).uppercase(Locale.ROOT), x, dy, p)

        if (s == null) return
        // `.kv` grid: two columns, left border accent, label over value.
        val rows = listOf(
            "MEM" to "${Telemetry.bytes(s.memUsedBytes)} / ${Telemetry.bytes(s.memTotalBytes)}",
            "STORAGE" to "${s.storageUsedPct}% · ${Telemetry.bytes(s.storageFreeBytes)} FREE",
            "NET ↓" to Telemetry.rate(s.rxRate),
            "NET ↑" to Telemetry.rate(s.txRate),
            "BATT" to String.format(Locale.ROOT, "%.1f°C %.2fV", s.batteryTempC, s.batteryVolts),
            "THERMAL" to Telemetry.thermalLabel(s.thermalStatus),
            "LINK" to s.link,
            "UPTIME" to Telemetry.uptime(s.uptimeMs),
        )
        val gridTop = dy + context.dp(10f)
        val rowH = context.dp(28f)
        val maxRows = ((b - gridTop) / rowH).toInt().coerceAtLeast(0)
        val colW = (r - x) / 2f
        val shown = rows.take(maxRows * 2)
        shown.forEachIndexed { i, (k, v) ->
            val col = i % 2
            val row = i / 2
            val kx = x + col * colW
            val ky = gridTop + row * rowH
            arc.strokeWidth = context.dp(2f); arc.color = pal.line2; arc.pathEffect = null
            c.drawLine(kx, ky + context.dp(2f), kx, ky + rowH - context.dp(4f), arc)
            hud.font(p, HudDraw.Font.DISPLAY, 8f, pal.faint, 600, .2f)
            c.drawText(k, kx + context.dp(8f), ky + context.dp(10f), p)
            val warn = (k == "THERMAL" && s.thermalStatus >= 3) || (k == "STORAGE" && s.storageUsedPct >= 90)
            hud.font(p, HudDraw.Font.MONO, 12.5f, if (warn) HudPalette.AMBER else pal.text)
            c.drawText(fit(v, colW - context.dp(12f)), kx + context.dp(8f), ky + context.dp(24f), p)
        }
    }

    private fun fit(s: String, w: Float): String {
        if (p.measureText(s) <= w) return s
        var e = s
        while (e.length > 1 && p.measureText("$e…") > w) e = e.dropLast(1)
        return "$e…"
    }

    private fun reactor(c: Canvas, cx: Float, cy: Float, rad: Float, s: TelemetrySample?) {
        val batt = s?.batteryPct ?: 0
        val mem = s?.memUsedPct ?: 0
        val battColor = when {
            s?.charging == true -> HudPalette.GREEN
            batt <= 15 -> HudPalette.RED
            batt <= 30 -> HudPalette.AMBER
            else -> pal.a1
        }

        // Radial core glow.
        p.reset(); p.isAntiAlias = true
        p.shader = RadialGradient(cx, cy, rad, withAlpha(pal.a2, .30f), 0, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, rad * .72f, p)
        p.shader = null

        val outer = rad - context.dp(6f)
        val inner = outer - context.dp(13f)
        val box = hud.rect(0f, 0f, 0f, 0f)

        // Rotating decorative rings (advance with the 1 Hz telemetry tick).
        val sec = (System.currentTimeMillis() / 1000 % 60).toFloat()
        arc.pathEffect = DashPathEffect(floatArrayOf(context.dp(2f), context.dp(6f)), 0f)
        arc.strokeWidth = context.dp(1.5f); arc.color = pal.a1d
        c.save(); c.rotate(sec * 6f, cx, cy)
        c.drawCircle(cx, cy, rad - context.dp(1f), arc)
        c.restore()
        arc.pathEffect = DashPathEffect(floatArrayOf(context.dp(18f), context.dp(6f),
            context.dp(2f), context.dp(6f)), 0f)
        arc.strokeWidth = context.dp(1f); arc.color = withAlpha(pal.a2, .45f)
        c.save(); c.rotate(-sec * 3f, cx, cy)
        c.drawCircle(cx, cy, inner - context.dp(10f), arc)
        c.restore()
        arc.pathEffect = null

        // Battery arc (outer) and memory arc (inner) over faint tracks.
        arc.strokeWidth = context.dp(8f)
        arc.color = withAlpha(pal.a1, .10f)
        c.drawCircle(cx, cy, outer, arc)
        arc.color = battColor
        arc.setShadowLayer(context.dp(6f), 0f, 0f, battColor)
        box.set(cx - outer, cy - outer, cx + outer, cy + outer)
        c.drawArc(box, -90f, 360f * batt / 100f, false, arc)
        arc.clearShadowLayer()

        arc.strokeWidth = context.dp(5f)
        arc.color = withAlpha(pal.a2, .10f)
        c.drawCircle(cx, cy, inner, arc)
        arc.color = if (mem >= 90) HudPalette.RED else pal.a2
        arc.setShadowLayer(context.dp(5f), 0f, 0f, arc.color)
        box.set(cx - inner, cy - inner, cx + inner, cy + inner)
        c.drawArc(box, -90f, 360f * mem / 100f, false, arc)
        arc.clearShadowLayer()

        // Center readout.
        hud.font(p, HudDraw.Font.DISPLAY, if (rad > context.dp(60f)) 34f else 26f, 0xFFFFFFFF.toInt(), 700)
        val v = batt.toString()
        val vw = p.measureText(v)
        hud.font(p, HudDraw.Font.DISPLAY, 13f, battColor, 700)
        val uw = p.measureText("%")
        hud.font(p, HudDraw.Font.DISPLAY, if (rad > context.dp(60f)) 34f else 26f, 0xFFFFFFFF.toInt(), 700)
        val vx = cx - (vw + uw) / 2f
        val vy = cy + p.textSize * .3f
        hud.glowText(c, v, vx, vy, p, pal.a1d, 10f)
        hud.font(p, HudDraw.Font.DISPLAY, 13f, battColor, 700)
        c.drawText("%", vx + vw + context.dp(1f), vy, p)

        hud.font(p, HudDraw.Font.JP, 9f, pal.dim, spacing = .3f)
        val lbl = if (s?.charging == true) "充電中" else "出力"
        c.drawText(lbl, cx - p.measureText(lbl) / 2f, vy + context.dp(14f), p)
        hud.font(p, HudDraw.Font.MONO, 10f, pal.a2)
        val ml = "MEM $mem%"
        c.drawText(ml, cx - p.measureText(ml) / 2f, vy + context.dp(27f), p)
    }
}

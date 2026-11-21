/*
 * Copyright (C) 2026 KuroDeck contributors
 * Licensed under the Apache License, Version 2.0.
 */
package com.kurodeck.hud

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.TextUtils
import android.text.TextPaint
import com.kurodeck.theme.HudPalette
import com.kurodeck.theme.HudPalette.Companion.withAlpha
import java.util.Locale

/**
 * `04 FLEET 艦隊`: Hearth's fleet strip on the home screen. One row per machine with its link
 * light, CPU, memory, hottest sensor and containers; the newest hub alert underneath. Tap to
 * open the Hearth dashboard.
 */
class FleetPanelView(context: Context) : HudPanelView(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
    override val wantsTelemetry = false
    override val wantsHub = true

    init {
        isClickable = true
        setOnClickListener {
            if (HearthLink.url(context) != null) HearthLink.open(context, "hearth.html")
        }
        contentDescription = "HEARTH fleet"
    }

    override fun drawContent(c: Canvas, l: Float, t: Float, r: Float, b: Float) {
        val url = HearthLink.url(context)
        val st = HearthLink.status
        val err = HearthLink.error
        val meta = when {
            url == null -> "NO LINK"
            st == null && err != null -> "OFFLINE"
            st == null -> "LINKING"
            else -> (if (err != null) "STALE · " else "") + "${st.fleet.count { it.online }}/${st.fleet.size} ONLINE"
        }
        val top = hud.header(c, pal, "04", "FLEET", "艦隊", meta, l, t, r)
        contentDescription = "HEARTH fleet, $meta"

        if (url == null) {
            note(c, "Link a HEARTH hub: long-press the home screen → Command deck → HEARTH LINK.", l, top, r)
            return
        }
        if (st == null) {
            note(c, if (err != null) "Can't reach $url ($err)." else "Linking to $url…", l, top, r)
            return
        }

        val rowH = context.dp(21f)
        val alert = st.events.firstOrNull { !it.rec && (it.lvl == "crit" || it.lvl == "warn") }
        val alertH = if (alert != null) context.dp(20f) else 0f
        val fit = ((b - top - alertH) / rowH).toInt().coerceAtLeast(1)
        // offline and alerting machines first when they don't all fit
        val nodes = if (st.fleet.size <= fit) st.fleet
            else st.fleet.sortedWith(compareBy({ it.online }, { it.alerts == 0 }, { !it.local })).take(fit)
        var y = top
        for (n in nodes) {
            row(c, n, l, y, r, rowH)
            y += rowH
        }
        if (st.fleet.size > nodes.size) {
            hud.font(p, HudDraw.Font.MONO, 9f, pal.faint)
            val more = "+${st.fleet.size - nodes.size} more"
            c.drawText(more, r - p.measureText(more), y - context.dp(6f), p)
        }
        if (alert != null) {
            val ay = b - context.dp(5f)
            val col = if (alert.lvl == "crit") HudPalette.RED else HudPalette.AMBER
            hud.font(p, HudDraw.Font.DISPLAY, 8f, col, 700, .15f)
            val tag = "▲ ${alert.node.uppercase(Locale.ROOT)} · ${HearthLink.ago(alert.t)}"
            c.drawText(tag, l, ay, p)
            val x = l + p.measureText(tag) + context.dp(8f)
            hud.font(p, HudDraw.Font.UI, 12f, pal.text)
            tp.set(p)
            val msg = TextUtils.ellipsize(alert.msg, tp, (r - x).coerceAtLeast(0f), TextUtils.TruncateAt.END).toString()
            c.drawText(msg, x, ay, tp)
        }
    }

    private fun row(c: Canvas, n: FleetNode, l: Float, t: Float, r: Float, h: Float) {
        val mid = t + h * .62f
        val dot = when {
            !n.online -> HudPalette.RED
            n.alerts > 0 -> HudPalette.AMBER
            else -> HudPalette.GREEN
        }
        p.reset(); p.isAntiAlias = true; p.color = dot
        p.setShadowLayer(context.dp(4f), 0f, 0f, dot)
        c.drawCircle(l + context.dp(4f), mid - context.dp(4f), context.dp(3f), p)
        p.clearShadowLayer()

        // name
        hud.font(p, HudDraw.Font.UI_BOLD, 14f, if (n.online) pal.text else pal.dim)
        tp.set(p)
        val nameW = (r - l) * .34f
        val name = TextUtils.ellipsize(n.name.uppercase(Locale.ROOT) + if (n.local) " ◆" else "", tp,
            nameW, TextUtils.TruncateAt.END).toString()
        c.drawText(name, l + context.dp(14f), mid, tp)

        // readouts, right-aligned
        hud.font(p, HudDraw.Font.MONO, 11f, pal.dim)
        val parts = if (!n.online) listOf("SIGNAL LOST") else listOfNotNull(
            n.cpu?.let { String.format(Locale.ROOT, "CPU %3.0f%%", it) },
            n.mem?.let { String.format(Locale.ROOT, "MEM %3.0f%%", it) },
            n.hot?.let { String.format(Locale.ROOT, "%.0f°", it) },
            n.total?.takeIf { it > 0 }?.let { "▣ ${n.running ?: 0}/$it" },
        )
        var x = r
        for (s in parts.reversed()) {
            val w = p.measureText(s)
            if (x - w < l + context.dp(14f) + nameW + context.dp(6f)) break
            p.color = when {
                !n.online -> HudPalette.RED
                s.startsWith("CPU") && (n.cpu ?: 0.0) >= 90 -> HudPalette.AMBER
                s.startsWith("MEM") && (n.mem ?: 0.0) >= 90 -> HudPalette.AMBER
                s.endsWith("°") && (n.hot ?: 0.0) >= 80 -> HudPalette.AMBER
                else -> pal.dim
            }
            c.drawText(s, x - w, mid, p)
            x -= w + context.dp(10f)
        }
        p.color = withAlpha(pal.a1, .06f)
        c.drawLine(l, t + h - context.dp(1f), r, t + h - context.dp(1f), p)
    }

    private fun note(c: Canvas, msg: String, l: Float, top: Float, r: Float) {
        hud.font(p, HudDraw.Font.UI, 13f, pal.dim)
        tp.set(p)
        val lay = android.text.StaticLayout.Builder.obtain(msg, 0, msg.length, tp, (r - l).toInt().coerceAtLeast(1))
            .setMaxLines(3).setEllipsize(TextUtils.TruncateAt.END).build()
        c.save(); c.translate(l, top + context.dp(4f)); lay.draw(c); c.restore()
    }
}

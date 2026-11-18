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
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.TypedValue
import com.kurodeck.theme.HudPalette
import com.kurodeck.theme.HudPalette.Companion.withAlpha
import com.kurodeck.theme.HudTheme

fun Context.dp(v: Float): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

fun Context.sp(v: Float): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

/**
 * Canvas versions of Hearth's CSS building blocks: the chamfered `.panel` (cut top-right and
 * bottom-left corners, gradient hairline border, hatched corner), the `.ph` panel header with its
 * numbered tag, and glowing text.
 */
class HudDraw(private val ctx: Context) {
    val cut = ctx.dp(14f)
    private val path = Path()
    private val inner = Path()
    private val border = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tmp = RectF()

    /** `--chamfer` clip-path polygon. */
    fun chamfer(p: Path, l: Float, t: Float, r: Float, b: Float, c: Float = cut) {
        p.reset()
        p.moveTo(l, t)
        p.lineTo(r - c, t)
        p.lineTo(r, t + c)
        p.lineTo(r, b)
        p.lineTo(l + c, b)
        p.lineTo(l, b - c)
        p.close()
    }

    /** Draws a `.panel` filling the given bounds. */
    fun panel(canvas: Canvas, pal: HudPalette, l: Float, t: Float, r: Float, b: Float) {
        chamfer(path, l, t, r, b)
        border.shader = LinearGradient(l, t, r, b,
            intArrayOf(pal.a1d, withAlpha(pal.a1, .12f), withAlpha(pal.a2, .12f), pal.a2d),
            floatArrayOf(0f, .3f, .7f, 1f), Shader.TileMode.CLAMP)
        canvas.drawPath(path, border)

        val i = ctx.dp(1f)
        chamfer(inner, l + i, t + i, r - i, b - i, cut - i * .4f)
        fill.shader = LinearGradient(0f, t, 0f, b,
            withAlpha(pal.surface, .90f), pal.panel, Shader.TileMode.CLAMP)
        canvas.drawPath(inner, fill)

        // Hatched corner: an accent edge plus diagonal hairlines fading inward.
        val s = ctx.dp(40f)
        canvas.save()
        canvas.clipPath(inner)
        line.shader = null
        line.strokeWidth = ctx.dp(1f)
        val step = ctx.dp(6f)
        var k = 0f
        while (k < s) {
            val a = (1f - k / s) * .28f
            line.color = withAlpha(pal.a1, a)
            canvas.drawLine(r - cut - k, t, r, t + cut + k, line)
            k += step
        }
        line.color = pal.a1
        line.strokeWidth = ctx.dp(2f)
        canvas.drawLine(r - cut, t + i, r - i, t + cut, line)
        canvas.restore()
    }

    /**
     * Draws a `.ph` header: `[01] TITLE 日本語 ............ meta` with a hairline and a glowing
     * secondary-accent underline segment. Returns the y coordinate below the header.
     */
    fun header(canvas: Canvas, pal: HudPalette, no: String, title: String, jp: String,
            meta: String?, l: Float, t: Float, r: Float): Float {
        val h = ctx.dp(22f)
        val base = t + h * .72f

        // Numbered tag with a slanted right edge.
        text.reset(); text.isAntiAlias = true
        text.typeface = HudTheme.display(ctx)
        text.fontVariationSettings = "'wght' 700"
        text.textSize = ctx.sp(10f)
        val nw = text.measureText(no) + ctx.dp(14f)
        path.reset()
        path.moveTo(l, t + ctx.dp(3f)); path.lineTo(l + nw, t + ctx.dp(3f))
        path.lineTo(l + nw - ctx.dp(6f), t + h - ctx.dp(3f)); path.lineTo(l, t + h - ctx.dp(3f))
        path.close()
        fill.shader = null; fill.color = pal.a1
        canvas.drawPath(path, fill)
        text.color = pal.bg
        canvas.drawText(no, l + ctx.dp(5f), base, text)

        var x = l + nw + ctx.dp(8f)
        text.color = 0xFFFFFFFF.toInt()
        text.textSize = ctx.sp(13f)
        text.letterSpacing = .22f
        canvas.drawText(title, x, base, text)
        x += text.measureText(title) + ctx.dp(8f)

        text.typeface = HudTheme.jp
        text.fontVariationSettings = null
        text.letterSpacing = .2f
        text.textSize = ctx.sp(11f)
        text.color = pal.a2
        canvas.drawText(jp, x, base, text)
        x += text.measureText(jp) + ctx.dp(10f)

        if (!meta.isNullOrEmpty()) {
            text.typeface = HudTheme.mono(ctx)
            text.letterSpacing = 0f
            text.textSize = ctx.sp(11f)
            text.color = pal.dim
            val m = ellipsize(meta, r - x)
            canvas.drawText(m, r - text.measureText(m), base, text)
        }

        val y = t + h + ctx.dp(5f)
        line.shader = null
        line.strokeWidth = ctx.dp(1f)
        line.color = pal.line
        canvas.drawLine(l, y, r, y, line)
        line.strokeWidth = ctx.dp(2f)
        line.color = pal.a2
        line.setShadowLayer(ctx.dp(6f), 0f, 0f, pal.a2)
        canvas.drawLine(l, y, l + ctx.dp(56f), y, line)
        line.clearShadowLayer()
        return y + ctx.dp(8f)
    }

    private fun ellipsize(s: String, w: Float): String {
        if (w <= 0) return ""
        if (text.measureText(s) <= w) return s
        var e = s
        while (e.isNotEmpty() && text.measureText("$e…") > w) e = e.dropLast(1)
        return "$e…"
    }

    /** Configures [p] for a font role and size. */
    fun font(p: Paint, role: Font, sizeSp: Float, color: Int, weight: Int = 700,
            spacing: Float = 0f) {
        p.isAntiAlias = true
        p.style = Paint.Style.FILL
        p.shader = null
        p.typeface = when (role) {
            Font.DISPLAY -> HudTheme.display(ctx)
            Font.UI -> HudTheme.ui(ctx)
            Font.UI_BOLD -> HudTheme.uiBold(ctx)
            Font.MONO -> HudTheme.mono(ctx)
            Font.JP -> HudTheme.jp
        }
        p.fontVariationSettings = if (role == Font.DISPLAY) "'wght' $weight" else null
        p.textSize = ctx.sp(sizeSp)
        p.color = color
        p.letterSpacing = spacing
    }

    /** Draws text with a soft glow (`text-shadow: 0 0 Npx color`). */
    fun glowText(c: Canvas, s: String, x: Float, y: Float, p: Paint, glow: Int, radiusDp: Float) {
        p.setShadowLayer(ctx.dp(radiusDp), 0f, 0f, glow)
        c.drawText(s, x, y, p)
        p.clearShadowLayer()
    }

    fun rect(l: Float, t: Float, r: Float, b: Float): RectF = tmp.apply { set(l, t, r, b) }

    enum class Font { DISPLAY, UI, UI_BOLD, MONO, JP }

    companion object {
        /** Typeface helper for non-canvas views. */
        fun mono(ctx: Context): Typeface = HudTheme.mono(ctx)
    }
}

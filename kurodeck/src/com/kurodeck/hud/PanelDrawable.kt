/*
 * Copyright (C) 2026 KuroDeck contributors
 * Licensed under the Apache License, Version 2.0.
 */
package com.kurodeck.hud

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import com.kurodeck.theme.HudPalette

/** A Hearth `.panel` as a Drawable, for view backgrounds (dialogs, rows, chips). */
class PanelDrawable(
    private val ctx: Context,
    private val pal: HudPalette,
    private val style: Style = Style.PANEL,
) : Drawable() {
    enum class Style { PANEL, CHIP, CHIP_ON }

    private val hud = HudDraw(ctx)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    override fun draw(c: Canvas) {
        val b = bounds
        when (style) {
            Style.PANEL -> hud.panel(c, pal, b.left.toFloat(), b.top.toFloat(),
                b.right.toFloat(), b.bottom.toFloat())
            Style.CHIP, Style.CHIP_ON -> {
                val cut = ctx.dp(8f)
                hud.chamfer(path, b.left + 1f, b.top + 1f, b.right - 1f, b.bottom - 1f, cut)
                p.style = Paint.Style.FILL
                p.color = if (style == Style.CHIP_ON) pal.a1
                    else HudPalette.withAlpha(pal.a1, .05f)
                if (style == Style.CHIP_ON) p.setShadowLayer(ctx.dp(8f), 0f, 0f, pal.a1d)
                c.drawPath(path, p)
                p.clearShadowLayer()
                p.style = Paint.Style.STROKE
                p.strokeWidth = ctx.dp(1f)
                p.color = if (style == Style.CHIP_ON) pal.a1 else pal.line2
                c.drawPath(path, p)
            }
        }
    }

    override fun getOutline(outline: Outline) {
        val b = bounds
        hud.chamfer(path, b.left.toFloat(), b.top.toFloat(), b.right.toFloat(),
            b.bottom.toFloat(), if (style == Style.PANEL) hud.cut else ctx.dp(8f))
        outline.setPath(path)
    }

    override fun setAlpha(alpha: Int) { p.alpha = alpha }
    override fun setColorFilter(cf: ColorFilter?) { p.colorFilter = cf }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

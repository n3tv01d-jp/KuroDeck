/*
 * Copyright (C) 2026 KuroDeck contributors
 * Licensed under the Apache License, Version 2.0.
 */
package com.kurodeck.hud

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import com.android.launcher3.R
import com.android.launcher3.shortcuts.DeepShortcutView
import com.kurodeck.theme.HudPalette
import com.kurodeck.theme.HudPalette.Companion.withAlpha
import com.kurodeck.theme.HudTheme

/**
 * HUD styling for Launcher3's long-press popups (home-screen options and app shortcuts).
 * Each shortcut container becomes a widget-style `.panel` (chamfer, gradient hairline,
 * hatched corner). Rows inside it are flat, with a hairline divider and an accent bar that
 * lights up on press. Called from [com.android.launcher3.popup.ArrowPopup].
 */
object HudPopup {

    /** Turns a shortcut container (`popup_container_iterate_children`) into a HUD panel. */
    @JvmStatic
    fun styleContainer(container: View) {
        val ctx = container.context
        val pal = HudTheme.current(ctx)
        if (container.background !is PanelDrawable) {
            container.background = PanelDrawable(ctx, pal)
        }
        container.elevation = 0f
        val h = ctx.dp(2f).toInt()
        // The icon-only row has a fixed 52dp height for 48dp touch targets: keep 2dp there.
        val horizontal = (container as? LinearLayout)?.orientation == LinearLayout.HORIZONTAL
        val v = ctx.dp(if (horizontal) 2f else 4f).toInt()
        container.setPadding(h, v, h, v)
        if (container is ViewGroup) {
            for (i in 0 until container.childCount) {
                val child = container.getChildAt(i)
                // Icon-only system shortcuts (the compact row above many deep shortcuts).
                if (child is ImageView) child.imageTintList = ColorStateList.valueOf(pal.a1)
            }
        }
    }

    /**
     * Styles one shortcut row.
     *
     * @param last whether this is the last visible row of its group (no divider below it)
     * @param standalone the row is not inside a panel container, so it draws its own chip
     */
    @JvmStatic
    fun styleRow(row: View, last: Boolean, standalone: Boolean) {
        val ctx = row.context
        val pal = HudTheme.current(ctx)
        row.background = RowDrawable(ctx, pal, divider = !last && !standalone, framed = standalone)
        row.elevation = 0f
        if (row !is DeepShortcutView) return
        row.bubbleText?.apply {
            background = null // the row drawable shows the press state instead of a ripple
            typeface = HudTheme.uiBold(ctx)
            isAllCaps = true
            letterSpacing = .09f
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(pal.text)
        }
        // System shortcuts use a tinted vector icon; deep shortcuts show the app's own icon.
        if (row.id == R.id.system_shortcut) {
            row.iconView?.backgroundTintList = ColorStateList.valueOf(pal.a1)
        }
    }

    /** Adds the `[00] OPTIONS 操作` header row to the home-screen options menu. */
    @JvmStatic
    fun addHeader(container: ViewGroup) {
        val ctx = container.context
        container.addView(HeaderView(ctx, HudTheme.current(ctx)), 0,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ctx.dp(44f).toInt()))
    }

    /** The popup arrow as a HUD wedge. */
    @JvmStatic
    fun arrow(ctx: Context, pointingUp: Boolean): Drawable =
        ArrowDrawable(ctx, HudTheme.current(ctx), pointingUp)

    private class HeaderView(c: Context, private val pal: HudPalette) : View(c) {
        private val hud = HudDraw(c)
        init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
        override fun onDraw(canvas: Canvas) {
            val pad = context.dp(10f)
            hud.header(canvas, pal, "00", "OPTIONS", "操作", null, pad, context.dp(8f),
                width - pad)
        }
    }

    /** Flat row: dim tick at rest, accent wash and glowing bar when pressed or focused. */
    private class RowDrawable(
        private val ctx: Context,
        private val pal: HudPalette,
        private val divider: Boolean,
        private val framed: Boolean,
    ) : Drawable() {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val path = Path()
        private val hud = HudDraw(ctx)
        private val chip = if (framed) PanelDrawable(ctx, pal, PanelDrawable.Style.CHIP) else null
        private var active = false

        override fun draw(c: Canvas) {
            val b = bounds
            val l = b.left.toFloat()
            val t = b.top.toFloat()
            val r = b.right.toFloat()
            val bt = b.bottom.toFloat()
            chip?.let { it.bounds = b; it.draw(c) }
            val inset = ctx.dp(6f)
            if (active) {
                p.style = Paint.Style.FILL
                p.color = withAlpha(pal.a1, .13f)
                if (framed) {
                    hud.chamfer(path, l + 1, t + 1, r - 1, bt - 1, ctx.dp(8f))
                    c.drawPath(path, p)
                } else {
                    c.drawRect(l + ctx.dp(1f), t, r - ctx.dp(1f), bt, p)
                }
                p.color = pal.a1
                p.setShadowLayer(ctx.dp(6f), 0f, 0f, pal.a1d)
                c.drawRect(l + ctx.dp(1f), t + inset, l + ctx.dp(3.5f), bt - inset, p)
                p.clearShadowLayer()
            } else {
                p.style = Paint.Style.FILL
                p.color = pal.line2
                val mid = (t + bt) / 2
                c.drawRect(l + ctx.dp(1f), mid - ctx.dp(5f), l + ctx.dp(2.5f), mid + ctx.dp(5f), p)
            }
            if (divider) {
                p.style = Paint.Style.FILL
                p.color = pal.line
                c.drawRect(l + ctx.dp(14f), bt - ctx.dp(1f), r - ctx.dp(10f), bt, p)
            }
        }

        override fun isStateful() = true

        override fun onStateChange(state: IntArray): Boolean {
            val on = state.any {
                it == android.R.attr.state_pressed || it == android.R.attr.state_focused ||
                    it == android.R.attr.state_hovered
            }
            if (on == active) return false
            active = on
            invalidateSelf()
            return true
        }

        override fun getOutline(outline: Outline) {
            if (chip != null) chip.apply { bounds = this@RowDrawable.bounds }.getOutline(outline)
            else outline.setRect(bounds)
        }

        override fun setAlpha(alpha: Int) { p.alpha = alpha }
        override fun setColorFilter(cf: ColorFilter?) { p.colorFilter = cf }
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }

    /** Wedge filled with the panel colour and edged with the primary accent. */
    private class ArrowDrawable(
        private val ctx: Context,
        private val pal: HudPalette,
        private val up: Boolean,
    ) : Drawable() {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val path = Path()

        override fun draw(c: Canvas) {
            val b = bounds
            val l = b.left.toFloat()
            val r = b.right.toFloat()
            val tip = if (up) b.top.toFloat() else b.bottom.toFloat()
            val base = if (up) b.bottom.toFloat() else b.top.toFloat()
            path.reset()
            path.moveTo(l, base)
            path.lineTo((l + r) / 2, tip)
            path.lineTo(r, base)
            path.close()
            p.style = Paint.Style.FILL
            // Match the panel gradient where the wedge meets it (top: surface, bottom: panel).
            p.color = if (up) withAlpha(pal.surface, .90f) else pal.panel
            c.drawPath(path, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = ctx.dp(1f)
            p.color = pal.a1d
            c.drawLine(l, base, (l + r) / 2, tip, p)
            c.drawLine((l + r) / 2, tip, r, base, p)
        }

        override fun setAlpha(alpha: Int) { p.alpha = alpha }
        override fun setColorFilter(cf: ColorFilter?) { p.colorFilter = cf }
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
}

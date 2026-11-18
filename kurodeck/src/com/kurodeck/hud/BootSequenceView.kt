/*
 * Copyright (C) 2026 KuroDeck contributors
 * Licensed under the Apache License, Version 2.0.
 */
package com.kurodeck.hud

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import com.kurodeck.theme.HudPalette
import com.kurodeck.theme.HudPalette.Companion.withAlpha
import com.kurodeck.theme.HudTheme

/**
 * Hearth's `#boot` screen: logo, a typed start-up log with `OK` markers and a gradient progress
 * bar, then a fade-out. Shown once per process start (i.e. after reboot or a launcher restart),
 * skippable with a tap, and disabled under "remove animations" or the COMMAND DECK switch.
 */
class BootSequenceView(context: Context) : View(context) {
    private val pal: HudPalette = HudTheme.current(context)
    private val hud = HudDraw(context)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var progress = 0f
    private var anim: ValueAnimator? = null

    private val lines = listOf(
        "mounting launcher3 core (grapheneos) ....",
        "binding workspace grid ..................",
        "calibrating reactor ring ................",
        "linking telemetry bus ...................",
        "loading palette ${pal.name} ${pal.jp} ......".take(41).padEnd(41, '.'),
        "waking navigator KAGARI-AI ..............",
    )

    init {
        isClickable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun play(onDone: () -> Unit) {
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1900
            interpolator = LinearInterpolator()
            addUpdateListener { progress = it.animatedValue as Float; invalidate() }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    animate().alpha(0f).setDuration(500).withEndAction(onDone).start()
                }
            })
            start()
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action == MotionEvent.ACTION_UP) anim?.end()
        return true
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        c.drawColor(pal.bg)
        val boxW = minOf(w - context.dp(32f), context.dp(420f))
        val l = (w - boxW) / 2f
        var y = h * .34f

        hud.font(p, HudDraw.Font.DISPLAY, 38f, pal.a1, 900, .18f)
        hud.glowText(c, "KURODECK", l, y, p, pal.a1d, 18f)
        y += context.dp(26f)
        hud.font(p, HudDraw.Font.JP, 13f, pal.a2, spacing = .5f)
        hud.glowText(c, "黒甲板 · 起動シーケンス", l, y, p, pal.a2d, 12f)

        y += context.dp(34f)
        hud.font(p, HudDraw.Font.MONO, 12.5f, pal.dim)
        val okW = p.measureText(" OK")
        val n = lines.size
        val shown = (progress * (n + 1)).toInt().coerceAtMost(n)
        for (i in 0 until shown) {
            var s = lines[i]
            while (s.length > 4 && p.measureText(s) + okW > boxW) s = s.dropLast(1)
            p.color = pal.dim
            c.drawText(s, l, y, p)
            p.color = HudPalette.GREEN
            c.drawText(" OK", l + p.measureText(s), y, p)
            y += context.dp(20f)
        }
        if (shown < n && (System.currentTimeMillis() / 300) % 2 == 0L) {
            p.color = pal.a1
            c.drawText("▌", l, y, p)
        }

        val by = h * .34f + context.dp(60f) + n * context.dp(20f) + context.dp(18f)
        p.shader = null
        p.color = withAlpha(pal.a1, .12f)
        c.drawRect(l, by, l + boxW, by + context.dp(4f), p)
        p.shader = LinearGradient(l, 0f, l + boxW, 0f, pal.a1, pal.a2, Shader.TileMode.CLAMP)
        p.setShadowLayer(context.dp(10f), 0f, 0f, pal.a1)
        c.drawRect(l, by, l + boxW * progress, by + context.dp(4f), p)
        p.clearShadowLayer()
        p.shader = null
    }

    companion object {
        private var shownThisProcess = false

        /** Adds the boot overlay on top of [activity]'s content the first time per process. */
        @JvmStatic
        fun maybeShow(activity: Activity) {
            if (shownThisProcess) return
            shownThisProcess = true
            if (!HudTheme.isEnabled(activity, HudTheme.KEY_BOOT)) return
            val scale = Settings.Global.getFloat(activity.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
            if (scale == 0f) return
            val root = activity.window.decorView as? ViewGroup ?: return
            val v = BootSequenceView(activity)
            root.addView(v, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT))
            v.play { root.removeView(v) }
        }
    }
}

/*
 * Copyright (C) 2026 KuroDeck contributors
 * Licensed under the Apache License, Version 2.0.
 */
package com.kurodeck.hud

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.Window
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.kurodeck.theme.HudPalette
import com.kurodeck.theme.HudTheme

/**
 * The COMMAND DECK (司令甲板): Hearth's theme menu and sidebar switches in one HUD dialog.
 * Opened from the workspace long-press menu. Picking a palette re-themes the HUD immediately
 * and recreates the launcher activity so Launcher3's own surfaces follow.
 */
class CommandDeckDialog(private val activity: Activity) : Dialog(activity) {
    private val ctx: Context = activity
    private val startTheme = HudTheme.current(ctx).id
    private val startPaged = HudTheme.isEnabled(ctx, HudTheme.KEY_PAGED_DRAWER)
    private lateinit var root: LinearLayout

    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window?.setDimAmount(.72f)
        build()
        setOnDismissListener {
            // The palette and the drawer layout are read when the launcher's views are built.
            if (HudTheme.current(ctx).id != startTheme ||
                HudTheme.isEnabled(ctx, HudTheme.KEY_PAGED_DRAWER) != startPaged) {
                activity.recreate()
            }
        }
    }

    private fun build() {
        val pal = HudTheme.current(ctx)
        val pad = ctx.dp(18f).toInt()
        root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = PanelDrawable(ctx, pal)
            setPadding(pad, ctx.dp(14f).toInt(), pad, pad)
        }
        root.addView(HeaderView(ctx, pal, "00", "COMMAND DECK", "司令甲板"),
            LinearLayout.LayoutParams(MATCH_PARENT, ctx.dp(40f).toInt()))

        root.addView(section(pal, "PALETTE // 配色"))
        HudTheme.palettes(ctx).forEach { root.addView(themeRow(pal, it)) }

        root.addView(section(pal, "HEARTH LINK // 炉心連結"))
        root.addView(linkRow(pal))
        if (HearthLink.url(ctx) != null) {
            root.addView(toggleRow(pal, "FLEET THEME", "艦隊配色", HearthLink.KEY_FOLLOW))
        }

        root.addView(section(pal, "SYSTEMS // 機能"))
        root.addView(toggleRow(pal, "BOOT SEQUENCE", "起動演出", HudTheme.KEY_BOOT))
        root.addView(toggleRow(pal, "KAGARI CHATTER", "雑談", HudTheme.KEY_KAGARI))
        root.addView(toggleRow(pal, "HEX BACKDROP", "背景格子", HudTheme.KEY_BACKDROP))
        root.addView(toggleRow(pal, "PAGED DRAWER", "頁式一覧", HudTheme.KEY_PAGED_DRAWER))
        root.addView(toggleRow(pal, "HUD PANEL", "操作盤", HudTheme.KEY_SHADE))

        root.addView(TextView(ctx).apply {
            text = "KURODECK // based on GrapheneOS Launcher3 · HUD after HEARTH"
            typeface = HudTheme.mono(ctx)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setTextColor(pal.faint)
            setPadding(0, ctx.dp(14f).toInt(), 0, 0)
        })

        setContentView(ScrollView(ctx).apply {
            isFillViewport = false
            addView(root, MATCH_PARENT, WRAP_CONTENT)
        })
        window?.setLayout((ctx.resources.displayMetrics.widthPixels * .9f).toInt(), WRAP_CONTENT)
    }

    private fun rebuild() = build()

    private fun section(pal: HudPalette, title: String) = TextView(ctx).apply {
        text = title
        typeface = HudTheme.display(ctx)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
        letterSpacing = .2f
        setTextColor(pal.a3)
        setPadding(0, ctx.dp(14f).toInt(), 0, ctx.dp(6f).toInt())
    }

    private fun themeRow(pal: HudPalette, p: HudPalette): View {
        val on = p.id == pal.id
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = PanelDrawable(ctx, pal,
                if (on) PanelDrawable.Style.CHIP_ON else PanelDrawable.Style.CHIP)
            val h = ctx.dp(10f).toInt()
            setPadding(h, ctx.dp(8f).toInt(), h, ctx.dp(8f).toInt())
            isClickable = true
            isFocusable = true
            contentDescription = "Theme ${p.name}" + if (on) ", selected" else ""
            setOnClickListener {
                HudTheme.set(ctx, p, push = true)
                rebuild()
            }
            addView(Swatch(ctx, p), LinearLayout.LayoutParams(ctx.dp(42f).toInt(), ctx.dp(14f).toInt()))
            addView(TextView(ctx).apply {
                text = p.name
                typeface = HudTheme.display(ctx)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                letterSpacing = .16f
                setTextColor(if (on) pal.bg else pal.text)
                setPadding(ctx.dp(12f).toInt(), 0, 0, 0)
            }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(TextView(ctx).apply {
                text = if (on) "${p.jp}  ◆" else p.jp
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(if (on) pal.bg else pal.a2)
            })
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                topMargin = ctx.dp(4f).toInt()
            }
        }
    }

    private fun toggleRow(pal: HudPalette, label: String, jp: String, key: String): View {
        val enabled = HudTheme.isEnabled(ctx, key)
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, ctx.dp(6f).toInt(), 0, ctx.dp(6f).toInt())
            isClickable = true
            isFocusable = true
            contentDescription = "$label, " + if (enabled) "on" else "off"
            setOnClickListener {
                HudTheme.setEnabled(ctx, key, !enabled)
                if (key == HudTheme.KEY_BACKDROP) HudBackdropView.refreshAll()
                if (key == HearthLink.KEY_FOLLOW) {
                    HudTheme.hubChanged(ctx)
                    if (!enabled) HearthLink.refresh(ctx, theme = true)
                }
                rebuild()
            }
            addView(TextView(ctx).apply {
                text = label
                typeface = HudTheme.uiBold(ctx)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                letterSpacing = .08f
                setTextColor(pal.text)
            })
            addView(TextView(ctx).apply {
                text = jp
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(pal.dim)
                setPadding(ctx.dp(8f).toInt(), 0, 0, 0)
            }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(TextView(ctx).apply {
                text = if (enabled) "ON" else "OFF"
                typeface = HudTheme.display(ctx)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                letterSpacing = .16f
                gravity = Gravity.CENTER
                setTextColor(if (enabled) pal.bg else pal.dim)
                background = PanelDrawable(ctx, pal,
                    if (enabled) PanelDrawable.Style.CHIP_ON else PanelDrawable.Style.CHIP)
                val h = ctx.dp(12f).toInt()
                setPadding(h, ctx.dp(5f).toInt(), h, ctx.dp(5f).toInt())
                minWidth = ctx.dp(56f).toInt()
            })
        }
    }

    /** The hub URL and link state; tap to link, change or unlink a HEARTH hub. */
    private fun linkRow(pal: HudPalette): View {
        val url = HearthLink.url(ctx)
        val st = HearthLink.status
        val state = when {
            url == null -> "NOT LINKED · tap to link a HEARTH hub"
            HearthLink.error != null -> "UNREACHABLE · ${HearthLink.error}"
            st == null -> "LINKING…"
            else -> "${st.fleet.count { it.online }}/${st.fleet.size} ONLINE · hub ${st.hub}"
        }
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = PanelDrawable(ctx, pal, PanelDrawable.Style.CHIP)
            val h = ctx.dp(10f).toInt()
            setPadding(h, ctx.dp(8f).toInt(), h, ctx.dp(8f).toInt())
            isClickable = true
            isFocusable = true
            contentDescription = "HEARTH link: $state"
            setOnClickListener { editLink(pal) }
            addView(TextView(ctx).apply {
                text = url ?: "—"
                typeface = HudTheme.mono(ctx)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(if (url != null) pal.a1 else pal.dim)
            })
            addView(TextView(ctx).apply {
                text = state
                typeface = HudTheme.ui(ctx)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(if (HearthLink.error != null) HudPalette.AMBER else pal.dim)
            })
        }
    }

    private fun editLink(pal: HudPalette) {
        val input = EditText(ctx).apply {
            setText(HearthLink.url(ctx) ?: "http://")
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            hint = "http://192.168.1.10:8088"
            setSingleLine()
            setSelection(text.length)
        }
        val pad = ctx.dp(20f).toInt()
        val box = LinearLayout(ctx).apply { setPadding(pad, ctx.dp(8f).toInt(), pad, 0); addView(input, MATCH_PARENT, WRAP_CONTENT) }
        val d = AlertDialog.Builder(ctx)
            .setTitle("HEARTH LINK // 炉心連結")
            .setMessage("Address of your HEARTH hub. KuroDeck shows its fleet, follows its theme and " +
                "lets KAGARI report its alerts.")
            .setView(box)
            .setPositiveButton("LINK", null)
            .setNegativeButton("CANCEL", null)
            .apply { if (HearthLink.url(ctx) != null) setNeutralButton("UNLINK") { _, _ ->
                HearthLink.setUrl(ctx, null); rebuild() } }
            .create()
        d.setOnShowListener {
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val u = HearthLink.normalise(input.text.toString())
                if (u == null) {
                    input.error = "Use http://host:port"
                } else {
                    HearthLink.setUrl(ctx, u)
                    d.dismiss()
                    rebuild()
                    // the status arrives in a moment; show it when it does
                    root.postDelayed({ if (isShowing) rebuild() }, 2500)
                }
            }
        }
        d.show()
    }

    /** `.sw`: three accent squares. */
    private class Swatch(c: Context, private val p: HudPalette) : View(c) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            val s = height.toFloat()
            val gap = (width - 3 * s) / 2f
            listOf(p.a1, p.a2, p.a3).forEachIndexed { i, col ->
                paint.color = col
                paint.setShadowLayer(s / 2, 0f, 0f, col)
                val x = i * (s + gap)
                canvas.drawRect(x, 0f, x + s, s, paint)
            }
        }
    }

    /** Canvas-drawn `.ph` header row. */
    private class HeaderView(c: Context, private val pal: HudPalette, private val no: String,
            private val title: String, private val jp: String) : View(c) {
        private val hud = HudDraw(c)
        init { contentDescription = "$title $jp" }
        override fun onDraw(canvas: Canvas) {
            hud.header(canvas, pal, no, title, jp, "v0.1", 0f, 0f, width.toFloat())
        }
    }
}

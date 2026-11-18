/*
 * Copyright (C) 2026 KuroDeck contributors
 * Licensed under the Apache License, Version 2.0.
 */
package com.kurodeck.theme

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import androidx.annotation.ColorInt
import androidx.annotation.StyleRes
import androidx.core.content.res.ResourcesCompat
import com.android.launcher3.R
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A HUD palette. Mirrors the CSS custom properties of Hearth's `style.css`: `a1` primary,
 * `a2` secondary, `a3` tertiary accent, `surface` (`--s-rgb`), `bg`, `bg2`, `panel`, `text`,
 * `dim`, `faint`. Status colours (green / amber / red) are shared by every palette.
 */
data class HudPalette(
    val id: String,
    val name: String,
    val jp: String,
    @ColorInt val a1: Int,
    @ColorInt val a2: Int,
    @ColorInt val a3: Int,
    @ColorInt val surface: Int,
    @ColorInt val bg: Int,
    @ColorInt val bg2: Int,
    @ColorInt val panel: Int,
    @ColorInt val text: Int,
    @ColorInt val dim: Int,
    @ColorInt val faint: Int,
    @StyleRes val launcherTheme: Int,
) {
    /** `--line`: hairlines. */
    val line: Int get() = withAlpha(a1, 0.16f)
    /** `--line-2`: stronger hairlines. */
    val line2: Int get() = withAlpha(a1, 0.32f)
    /** `--a1-d`: glow tint of the primary accent. */
    val a1d: Int get() = withAlpha(a1, 0.55f)
    /** `--a2-d`: glow tint of the secondary accent. */
    val a2d: Int get() = withAlpha(a2, 0.55f)

    companion object {
        @ColorInt val GREEN = 0xFF5DFF9E.toInt()
        @ColorInt val AMBER = 0xFFFFB63E.toInt()
        @ColorInt val RED = 0xFFFF3B5C.toInt()

        @ColorInt
        fun withAlpha(@ColorInt c: Int, a: Float): Int =
            (c and 0x00FFFFFF) or ((a.coerceIn(0f, 1f) * 255).toInt() shl 24)
    }
}

/** Current palette, fonts and KuroDeck switches, persisted in the `kurodeck` preferences. */
object HudTheme {
    private const val PREFS = "kurodeck"
    const val KEY_THEME = "hud_theme"
    const val KEY_BOOT = "hud_boot_sequence"
    const val KEY_KAGARI = "hud_kagari_chatter"
    const val KEY_BACKDROP = "hud_backdrop"
    /** Paged (swipe left/right) app drawer instead of Launcher3's vertical A–Z list. */
    const val KEY_PAGED_DRAWER = "hud_paged_drawer"
    /** Swipe down on the home screen opens the HUD panel (notifications + quick controls). */
    const val KEY_SHADE = "hud_shade_panel"

    private fun c(hex: String) = Color.parseColor(hex)

    /** Palettes in Hearth's theme menu order. */
    val palettes: List<HudPalette> = listOf(
        HudPalette("hearth", "HEARTH", "炉心", c("#3EF3FF"), c("#FF3EA5"), c("#9B7BFF"),
            c("#0E142A"), c("#05060D"), c("#0A0D1C"), c("#E6090D1C"), c("#D8F6FF"),
            c("#7D93B5"), c("#45567A"), R.style.KuroDeck_Hearth),
        HudPalette("redline", "REDLINE", "赤警報", c("#FF6A3D"), c("#FFD23F"), c("#FF9A76"),
            c("#260E0C"), c("#0B0405"), c("#160808"), c("#E6180909"), c("#FFE9E0"),
            c("#B58A7D"), c("#6E4A42"), R.style.KuroDeck_Redline),
        HudPalette("mecha", "MECHA", "機甲", c("#8DFF5C"), c("#FFE14D"), c("#4DFFD2"),
            c("#101E10"), c("#040805"), c("#08110A"), c("#E609140B"), c("#E4FFE0"),
            c("#86A982"), c("#47613F"), R.style.KuroDeck_Mecha),
        HudPalette("sakura", "SAKURA", "桜", c("#FF9AD5"), c("#8FDCFF"), c("#D4B3FF"),
            c("#241022"), c("#0B0510"), c("#150A18"), c("#E6180A1A"), c("#FFEAF6"),
            c("#B48AA6"), c("#6B4A63"), R.style.KuroDeck_Sakura),
        HudPalette("eclipse", "ECLIPSE", "月蝕", c("#C3A6FF"), c("#FFCB5C"), c("#7FB4FF"),
            c("#16102A"), c("#06050E"), c("#0D0A1C"), c("#E60E0A1E"), c("#ECE6FF"),
            c("#8D86B5"), c("#4C4675"), R.style.KuroDeck_Eclipse),
        HudPalette("kurenai", "KURENAI", "紅", c("#FF2E4D"), c("#FF8A8A"), c("#C21A3A"),
            c("#22060A"), c("#0A0203"), c("#160406"), c("#E61A0508"), c("#FFE3E6"),
            c("#B57A82"), c("#6E3A42"), R.style.KuroDeck_Kurenai),
        HudPalette("abyss", "ABYSS", "深海", c("#2F9BFF"), c("#3EFFC1"), c("#5A6BFF"),
            c("#061226"), c("#02060D"), c("#051022"), c("#E6050E1E"), c("#DEF0FF"),
            c("#7A97B8"), c("#3C5475"), R.style.KuroDeck_Abyss),
        HudPalette("aurum", "AURUM", "黄金", c("#FFC94A"), c("#FFF1B8"), c("#D98B2B"),
            c("#1E1608"), c("#090702"), c("#141006"), c("#E6161006"), c("#FFF5DC"),
            c("#B5A27A"), c("#6E5F3E"), R.style.KuroDeck_Aurum),
        HudPalette("specter", "SPECTER", "幽霊", c("#E6EEF7"), c("#9FB4C9"), c("#6E8296"),
            c("#14171C"), c("#060708"), c("#0D0F12"), c("#E6101216"), c("#F2F5F8"),
            c("#8C96A2"), c("#4C5560"), R.style.KuroDeck_Specter),
    )

    private val listeners = CopyOnWriteArrayList<(HudPalette) -> Unit>()
    @Volatile private var cached: HudPalette? = null

    fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun current(context: Context): HudPalette {
        cached?.let { return it }
        val id = prefs(context).getString(KEY_THEME, palettes[0].id)
        return (palettes.firstOrNull { it.id == id } ?: palettes[0]).also { cached = it }
    }

    fun set(context: Context, palette: HudPalette) {
        prefs(context).edit().putString(KEY_THEME, palette.id).apply()
        cached = palette
        listeners.forEach { it(palette) }
    }

    fun addListener(l: (HudPalette) -> Unit) = listeners.add(l)
    fun removeListener(l: (HudPalette) -> Unit) = listeners.remove(l)

    fun isEnabled(context: Context, key: String, default: Boolean = true): Boolean =
        prefs(context).getBoolean(key, default)

    fun setEnabled(context: Context, key: String, value: Boolean) =
        prefs(context).edit().putBoolean(key, value).apply()

    /** Activity theme for Launcher3; replaces wallpaper-driven light/dark selection. */
    @JvmStatic
    @StyleRes
    fun launcherThemeRes(context: Context): Int = current(context).launcherTheme

    // ── fonts ──────────────────────────────────────────────────────────────────────────────
    private var fDisplay: Typeface? = null
    private var fUi: Typeface? = null
    private var fUiBold: Typeface? = null
    private var fMono: Typeface? = null

    /** Orbitron (variable; set `'wght'` on the Paint for 500..900). */
    fun display(context: Context): Typeface =
        fDisplay ?: load(context, R.font.orbitron).also { fDisplay = it }

    /** Rajdhani SemiBold: labels and body text. */
    fun ui(context: Context): Typeface =
        fUi ?: load(context, R.font.rajdhani_semibold).also { fUi = it }

    /** Rajdhani Bold. */
    fun uiBold(context: Context): Typeface =
        fUiBold ?: load(context, R.font.rajdhani_bold).also { fUiBold = it }

    /** Share Tech Mono: numbers and logs. */
    fun mono(context: Context): Typeface =
        fMono ?: load(context, R.font.share_tech_mono).also { fMono = it }

    /** Japanese subtitles use the system CJK font in bold. */
    val jp: Typeface get() = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

    private fun load(context: Context, res: Int): Typeface =
        try {
            ResourcesCompat.getFont(context, res) ?: Typeface.DEFAULT
        } catch (e: Exception) {
            Typeface.DEFAULT
        }
}

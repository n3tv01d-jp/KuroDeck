/*
 * Copyright (C) 2026 KuroDeck contributors
 * Licensed under the Apache License, Version 2.0.
 */
package com.kurodeck.hud

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.PowerManager
import android.os.SystemClock
import java.util.Calendar
import java.util.Locale

/** What KAGARI is saying right now. [mood] is ok / happy / warn / crit. */
data class NavLine(
    val key: String,
    val mood: String,
    val label: String,
    val kao: String,
    val en: String,
    val jp: String,
)

/**
 * KAGARI (篝), Hearth's kaomoji navigator, adapted to a phone. Like Hearth's `navState()`, urgent
 * conditions win; otherwise an idle line rotates every 14 s. Device events (charger, unlocks,
 * app installs) are remembered for a short while so she can comment on them.
 */
object Kagari {
    private const val ROTATE_MS = 14_000L
    private const val EVENT_MS = 25_000L

    private var rot = 0
    private var rotAt = 0L
    private var manualBump = 0
    private var event: NavLine? = null
    private var eventAt = 0L
    private var unlocksToday = 0
    private var unlockDay = -1
    private var registered = 0
    private var app: Context? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val now = SystemClock.elapsedRealtime()
            when (i.action) {
                Intent.ACTION_POWER_CONNECTED -> post("plug", "happy", "POWER LINK", "٩(◕‿◕｡)۶",
                    "Charger connected! Topping up the core, commander~", "充電開始！", now)
                Intent.ACTION_POWER_DISCONNECTED -> post("unplug", "ok", "ON BATTERY", "(・ω・)ゞ",
                    "Unplugged. Running on internal power now.", "内部電源に切替。", now)
                Intent.ACTION_USER_PRESENT -> {
                    val day = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
                    if (day != unlockDay) { unlockDay = day; unlocksToday = 0 }
                    unlocksToday++
                    val (k, e, j) = greeting()
                    post("unlock$unlocksToday", "happy", "WELCOME BACK", k,
                        "Welcome back, commander! That's unlock #$unlocksToday today. $e", j, now)
                }
                Intent.ACTION_PACKAGE_ADDED -> if (!i.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                    val pkg = i.data?.schemeSpecificPart ?: return
                    post("add-$pkg", "happy", "NEW UNIT", "(ﾉ◕ヮ◕)ﾉ*:･ﾟ✧",
                        "${label(c, pkg)} just joined the deck. Welcome aboard!", "新規ユニット着任！", now)
                }
                Intent.ACTION_PACKAGE_FULLY_REMOVED -> {
                    val pkg = i.data?.schemeSpecificPart ?: return
                    post("rm-$pkg", "warn", "UNIT REMOVED", "(╥﹏╥)",
                        "$pkg has left the deck. I'll miss it a little.", "ユニット離脱。", now)
                }
            }
        }
    }

    private fun post(key: String, mood: String, label: String, kao: String, en: String,
            jp: String, now: Long) {
        event = NavLine("ev-$key", mood, label, kao, en, jp)
        eventAt = now
    }

    private fun label(c: Context, pkg: String): String = try {
        val pm = c.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0)))
            .toString()
    } catch (e: Exception) {
        pkg
    }

    fun attach(context: Context) {
        if (registered++ > 0) return
        val c = context.applicationContext.also { app = it }
        c.registerReceiver(receiver, IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_USER_PRESENT)
        }, Context.RECEIVER_EXPORTED)
        c.registerReceiver(receiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
            addDataScheme("package")
        }, Context.RECEIVER_EXPORTED)
    }

    fun detach() {
        if (--registered > 0) return
        registered = 0
        try { app?.unregisterReceiver(receiver) } catch (_: IllegalArgumentException) {}
    }

    /** Tapping the panel skips to the next idle line. */
    fun bump() {
        manualBump++
        rotAt = SystemClock.elapsedRealtime()
        event = null
    }

    private fun greeting(): Triple<String, String, String> {
        val cal = Calendar.getInstance()
        val h = cal.get(Calendar.HOUR_OF_DAY)
        // KAGARI's shared lines from a linked HEARTH hub, when there are any
        HearthLink.pack?.greetings?.takeIf { it.isNotEmpty() }?.let { g ->
            val line = (g.firstOrNull { h < it.first } ?: g.last()).second
            val hm = String.format(Locale.ROOT, "%02d:%02d", h, cal.get(Calendar.MINUTE))
            return Triple(line.kao, line.en.replace("{time}", hm).replace("{where}", "deck"), line.jp)
        }
        return when {
            h < 5 -> Triple("(￣o￣) zzz", "Still up this late? I'll keep the hearth warm.", "夜更かしですね…")
            h < 11 -> Triple("(｡•̀ᴗ-)✧", "Good morning! Systems warmed up and ready.", "おはようございます！")
            h < 18 -> Triple("(・ω・)ノ", "Good afternoon! Everything is humming along nicely.", "こんにちは！")
            else -> Triple("(´｡• ᵕ •｡`)", "Good evening. Quiet night on the deck so far.", "こんばんは。")
        }
    }

    private const val HUB_ALERT_MS = 120_000L
    private const val HUB_REC_MS = 45_000L

    /** Fleet emergencies from the linked hub: machines gone silent, fresh critical events. */
    private fun hubUrgent(): NavLine? {
        val st = HearthLink.status ?: return null
        if (HearthLink.error != null) return null
        val lost = st.fleet.filter { !it.online }
        if (lost.isNotEmpty()) return NavLine("hub-lost-" + lost.joinToString(",") { it.id }, "crit",
            "NODE SILENT", "(⊙_⊙;)", "${lost.joinToString(" & ") { it.name }} went silent at home! " +
                "I can't hear ${if (lost.size > 1) "them" else "it"} on the LAN anymore…", "ノード応答なし！")
        val crit = st.events.firstOrNull { !it.rec && it.lvl == "crit" &&
            System.currentTimeMillis() - it.t < HUB_ALERT_MS } ?: return null
        return NavLine("hub-ev-${crit.id}", "crit", "FLEET ALERT", "(×﹏×)",
            "${crit.node}: ${crit.msg}!", "艦隊に異常発生！")
    }

    /** Fleet warnings and recoveries from the linked hub. */
    private fun hubNotice(): NavLine? {
        val st = HearthLink.status ?: return null
        val now = System.currentTimeMillis()
        val rec = st.events.firstOrNull { it.rec && now - it.t < HUB_REC_MS }
        if (rec != null) return NavLine("hub-rec-${rec.id}", "happy", "RECOVERED", "٩(◕‿◕｡)۶",
            "Yay! ${rec.node}: ${rec.msg}. Everything's okay at home again~", "復旧しました！")
        val warn = st.events.firstOrNull { !it.rec && it.lvl == "warn" && now - it.t < HUB_ALERT_MS }
            ?: return null
        return NavLine("hub-ev-${warn.id}", "warn", "FLEET WARNING", "(°ロ°;)",
            "Heads up from home: ${warn.node}: ${warn.msg}.", "艦隊から警告。")
    }

    /** Picks the line to show for [s], following Hearth's priority order. */
    fun state(s: TelemetrySample?, chatter: Boolean): NavLine {
        val now = SystemClock.elapsedRealtime()
        if (s != null) {
            if (s.thermalStatus >= PowerManager.THERMAL_STATUS_SEVERE) return NavLine("therm-crit",
                "crit", "MELTDOWN RISK", "(×﹏×)",
                "Thermal status is ${Telemetry.thermalLabel(s.thermalStatus)}! Give the core a rest, please!",
                "熱暴走の危険！")
            if (!s.charging && s.batteryPct in 0..5) return NavLine("batt-crit", "crit",
                "POWER CRITICAL", "(×﹏×)",
                "Only ${s.batteryPct}% left! Plug me in before the hearth goes dark!", "電力残量わずか！")
        }
        hubUrgent()?.let { return it }
        event?.let { if (now - eventAt < EVENT_MS) return it else event = null }
        hubNotice()?.let { return it }
        if (s != null) {
            if (s.charging && s.batteryPct >= 100) return NavLine("full", "happy", "FULL CHARGE",
                "(ﾉ◕ヮ◕)ﾉ*:･ﾟ✧", "Core at 100%! You can unplug whenever you like.", "充電完了！")
            if (!s.charging && s.batteryPct <= 15) return NavLine("batt-low", "warn", "LOW POWER",
                "(｡•́︿•̀｡)", "Battery is down to ${s.batteryPct}%. Maybe find a charger soon?",
                "電力低下中…")
            if (s.thermalStatus >= PowerManager.THERMAL_STATUS_MODERATE) return NavLine("therm",
                "warn", "RUNNING HOT", "(°ロ°;)",
                String.format(Locale.ROOT, "Running warm: battery at %.0f°C. Easy on the heavy apps~",
                    s.batteryTempC), "高温注意！")
            if (s.memUsedPct >= 90) return NavLine("mem", "warn", "MEMORY TIGHT", "(・_・;)",
                "Memory is ${s.memUsedPct}% full. Things might start getting evicted.", "メモリ逼迫。")
            if (s.storageUsedPct >= 92) return NavLine("disk", "warn", "DISK FULL", "(・・;)ゞ",
                "Storage is ${s.storageUsedPct}% used, only ${Telemetry.bytes(s.storageFreeBytes)} left!",
                "記憶域がいっぱい！")
            if (s.link == "NO LINK") return NavLine("nolink", "warn", "SIGNAL LOST", "(⊙_⊙;)",
                "No network link. I'll keep watch offline.", "通信途絶…")
            if (s.rxRate > 4L * 1024 * 1024) return NavLine("net", "happy", "HEAVY TRAFFIC",
                "ヽ(°〇°)ﾉ", "Whoa, ${Telemetry.rate(s.rxRate)} pouring in over ${s.link}! Big download?",
                "大量のデータを受信中！")
        }

        val (gk, ge, gj) = greeting()
        val idle = buildList {
            add(Triple(gk, ge, gj))
            if (s != null) {
                add(Triple("(｀・ω・´)ゞ",
                    "All systems nominal. Core at ${s.batteryPct}%, link ${s.link}.", "全システム正常です。"))
                add(Triple("(￣▽￣)ノ", "Uptime ${Telemetry.uptime(s.uptimeMs)}. The hearth burns steady.",
                    "炉心、安定稼働中。"))
                add(Triple("( ´ ▽ ` )", String.format(Locale.ROOT,
                    "Battery sits at a comfy %.0f°C on %s power.", s.batteryTempC,
                    if (s.charging) s.plug else "internal"), "温度は快適です。"))
                add(Triple("(・ω・)", "RAM ${s.memUsedPct}% · storage ${s.storageUsedPct}%. " +
                    "Plenty of headroom left.", "余裕あり、問題なし。"))
                add(Triple("(｀・ω・´)📋", "Since boot we've moved ▼${Telemetry.bytes(s.rxTotal)} " +
                    "▲${Telemetry.bytes(s.txTotal)}.", "通信量の報告です。"))
            }
            HearthLink.status?.let { st ->
                val on = st.fleet.count { it.online }
                if (st.fleet.isNotEmpty() && on == st.fleet.size) add(Triple("(ﾉ◕ヮ◕)ﾉ*:･ﾟ✧",
                    "All ${st.fleet.size} machine${if (st.fleet.size == 1) "" else "s"} in the fleet are green. " +
                        "The hearth burns steady at home.", "艦隊、全機正常。"))
            }
            HearthLink.pack?.idle?.forEach { add(Triple(it.kao, it.en, it.jp)) }
            if (unlocksToday > 0) add(Triple("(˘ω˘)",
                "You've checked in $unlocksToday time${if (unlocksToday == 1) "" else "s"} today.",
                "本日の記録。"))
        }
        if (chatter && now - rotAt > ROTATE_MS) { rot++; rotAt = now }
        val i = Math.floorMod(rot + manualBump, idle.size)
        val (k, e, j) = idle[i]
        return NavLine("idle-$i-${rot + manualBump}", "ok", "NOMINAL", k, e, j)
    }
}

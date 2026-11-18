/*
 * Copyright (C) 2026 KuroDeck contributors
 * Licensed under the Apache License, Version 2.0.
 */
package com.kurodeck.hud

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import java.util.concurrent.CopyOnWriteArrayList

/** One telemetry frame. Rates are bytes per second; [rxHistory]/[txHistory] are oldest first. */
data class TelemetrySample(
    val batteryPct: Int,
    val charging: Boolean,
    val plug: String,
    val batteryTempC: Float,
    val batteryVolts: Float,
    val memUsedPct: Int,
    val memUsedBytes: Long,
    val memTotalBytes: Long,
    val storageUsedPct: Int,
    val storageFreeBytes: Long,
    val storageTotalBytes: Long,
    val rxRate: Long,
    val txRate: Long,
    val rxTotal: Long,
    val txTotal: Long,
    val rxHistory: FloatArray,
    val txHistory: FloatArray,
    val link: String,
    val thermalStatus: Int,
    val thermalHeadroom: Float,
    val uptimeMs: Long,
    val cores: Int,
)

/**
 * Samples the device once a second while at least one HUD panel is visible: battery (sticky
 * broadcast), memory, storage, network throughput (TrafficStats), link type and thermal status.
 * Everything here is available to regular apps; CPU load (/proc/stat) is not on modern Android.
 */
object Telemetry {
    const val HISTORY = 120

    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<(TelemetrySample) -> Unit>()
    private var app: Context? = null
    private var running = false

    private var battery: Intent? = null
    private var lastRx = -1L
    private var lastTx = -1L
    private var lastT = 0L
    private val rxHist = FloatArray(HISTORY)
    private val txHist = FloatArray(HISTORY)
    @Volatile var latest: TelemetrySample? = null
        private set

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { battery = i }
    }

    private val tick = object : Runnable {
        override fun run() {
            val c = app ?: return
            val s = sample(c)
            latest = s
            listeners.forEach { it(s) }
            if (running) main.postDelayed(this, 1000L - SystemClock.uptimeMillis() % 1000L)
        }
    }

    fun subscribe(context: Context, l: (TelemetrySample) -> Unit) {
        listeners.addIfAbsent(l)
        latest?.let(l)
        if (!running) start(context.applicationContext)
    }

    fun unsubscribe(l: (TelemetrySample) -> Unit) {
        listeners.remove(l)
        if (listeners.isEmpty()) stop()
    }

    private fun start(c: Context) {
        app = c
        running = true
        battery = c.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        main.removeCallbacks(tick)
        main.post(tick)
    }

    private fun stop() {
        running = false
        main.removeCallbacks(tick)
        try { app?.unregisterReceiver(batteryReceiver) } catch (_: IllegalArgumentException) {}
        lastRx = -1L
    }

    private fun sample(c: Context): TelemetrySample {
        val b = battery
        val level = b?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = b?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else 0
        val status = b?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val plug = when (b?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "WIRELESS"
            BatteryManager.BATTERY_PLUGGED_DOCK -> "DOCK"
            else -> "CELL"
        }
        val temp = (b?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val volts = (b?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0) / 1000f

        val am = c.getSystemService(ActivityManager::class.java)
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val used = mi.totalMem - mi.availMem
        val memPct = if (mi.totalMem > 0) (used * 100 / mi.totalMem).toInt() else 0

        val st = StatFs(Environment.getDataDirectory().path)
        val total = st.totalBytes
        val free = st.availableBytes
        val stPct = if (total > 0) ((total - free) * 100 / total).toInt() else 0

        val now = SystemClock.elapsedRealtime()
        val rx = TrafficStats.getTotalRxBytes().coerceAtLeast(0)
        val tx = TrafficStats.getTotalTxBytes().coerceAtLeast(0)
        var rxRate = 0L
        var txRate = 0L
        if (lastRx >= 0 && now > lastT) {
            val dt = (now - lastT) / 1000f
            rxRate = ((rx - lastRx).coerceAtLeast(0) / dt).toLong()
            txRate = ((tx - lastTx).coerceAtLeast(0) / dt).toLong()
        }
        lastRx = rx; lastTx = tx; lastT = now
        System.arraycopy(rxHist, 1, rxHist, 0, HISTORY - 1)
        System.arraycopy(txHist, 1, txHist, 0, HISTORY - 1)
        rxHist[HISTORY - 1] = rxRate.toFloat()
        txHist[HISTORY - 1] = txRate.toFloat()

        val pm = c.getSystemService(PowerManager::class.java)
        val thermal = pm.currentThermalStatus
        val headroom = if (Build.VERSION.SDK_INT >= 30) pm.getThermalHeadroom(10) else Float.NaN

        return TelemetrySample(
            batteryPct = pct, charging = charging, plug = plug, batteryTempC = temp,
            batteryVolts = volts, memUsedPct = memPct, memUsedBytes = used,
            memTotalBytes = mi.totalMem, storageUsedPct = stPct, storageFreeBytes = free,
            storageTotalBytes = total, rxRate = rxRate, txRate = txRate, rxTotal = rx,
            txTotal = tx, rxHistory = rxHist.copyOf(), txHistory = txHist.copyOf(),
            link = link(c), thermalStatus = thermal, thermalHeadroom = headroom,
            uptimeMs = now, cores = Runtime.getRuntime().availableProcessors(),
        )
    }

    private fun link(c: Context): String {
        val cm = c.getSystemService(ConnectivityManager::class.java) ?: return "NO LINK"
        val caps = try {
            cm.getNetworkCapabilities(cm.activeNetwork)
        } catch (e: SecurityException) {
            null
        } ?: return "NO LINK"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELL"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETH"
            else -> "LINK"
        }
    }

    // ── formatting (matches Hearth's app.js helpers) ───────────────────────────────────────
    fun bytes(v: Long): String {
        val u = arrayOf("B", "K", "M", "G", "T")
        var x = v.toDouble(); var i = 0
        while (x >= 1024 && i < u.size - 1) { x /= 1024; i++ }
        return if (i == 0) "${v}B" else String.format(java.util.Locale.ROOT,
            if (x >= 100) "%.0f%s" else "%.1f%s", x, u[i])
    }

    fun rate(v: Long): String = bytes(v) + "/s"

    fun uptime(ms: Long): String {
        val s = ms / 1000
        val d = s / 86400; val h = s % 86400 / 3600; val m = s % 3600 / 60
        return if (d > 0) "${d}d ${h}h" else if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    fun thermalLabel(s: Int): String = when (s) {
        PowerManager.THERMAL_STATUS_NONE -> "NOMINAL"
        PowerManager.THERMAL_STATUS_LIGHT -> "LIGHT"
        PowerManager.THERMAL_STATUS_MODERATE -> "MODERATE"
        PowerManager.THERMAL_STATUS_SEVERE -> "SEVERE"
        PowerManager.THERMAL_STATUS_CRITICAL -> "CRITICAL"
        PowerManager.THERMAL_STATUS_EMERGENCY -> "EMERGENCY"
        PowerManager.THERMAL_STATUS_SHUTDOWN -> "SHUTDOWN"
        else -> "—"
    }
}

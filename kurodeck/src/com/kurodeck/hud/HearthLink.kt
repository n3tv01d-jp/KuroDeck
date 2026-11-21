/*
 * Copyright (C) 2026 KuroDeck contributors
 * Licensed under the Apache License, Version 2.0.
 */
package com.kurodeck.hud

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.kurodeck.theme.HudTheme
import org.json.JSONArray
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList

/** One machine of the HEARTH fleet, as `/api/status` reports it. */
data class FleetNode(
    val id: String,
    val name: String,
    val local: Boolean,
    val online: Boolean,
    val cpu: Double?,
    val mem: Double?,
    val hot: Double?,
    val running: Int?,
    val total: Int?,
    val alerts: Int,
)

/** A warning, critical or recovery from the hub's event log. [t] is epoch milliseconds. */
data class HubEvent(val id: Long, val t: Long, val lvl: String, val src: String, val msg: String,
    val node: String, val rec: Boolean)

data class HubStatus(
    val hub: String,
    val fleet: List<FleetNode>,
    val events: List<HubEvent>,
    val maint: Int,
    val themeRev: String,
    /** `elapsedRealtime()` of the fetch. */
    val at: Long,
)

data class HubLine(val kao: String, val en: String, val jp: String)

/** The hub's shared look: fleet theme, palettes and KAGARI's shared lines. */
data class HubPack(
    val theme: String,
    val rev: String,
    val palettes: List<JSONObject>,
    val greetings: List<Pair<Int, HubLine>>,
    val idle: List<HubLine>,
)

/**
 * KuroDeck's link to a HEARTH hub (炉心連結): polls `/api/status` while a FLEET panel, KAGARI or
 * the console is on screen, keeps the hub's look (`/api/theme`) and follows its fleet theme.
 * Nothing runs until a hub URL is set in the Command deck.
 */
object HearthLink {
    private const val TAG = "KuroDeckHearth"
    const val KEY_URL = "hearth_url"
    const val KEY_FOLLOW = "hearth_follow_theme"
    private const val KEY_PACK = "hearth_pack"
    private const val KEY_RYU = "hearth_ryucore"
    private const val POLL_MS = 15_000L
    private const val MAX_BODY = 512 * 1024

    private val worker: Handler by lazy {
        Handler(HandlerThread("kurodeck-hearth").apply { start() }.looper)
    }
    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private var app: Context? = null
    @Volatile private var polling = false
    private var launcher: WeakReference<Activity>? = null

    @Volatile var status: HubStatus? = null
        private set
    @Volatile var error: String? = null
        private set
    @Volatile var pack: HubPack? = null
        private set
    @Volatile var ryucore: String? = null
        private set

    // ── settings ──────────────────────────────────────────────────────────────────────────

    fun url(c: Context): String? =
        HudTheme.prefs(c).getString(KEY_URL, null)?.takeIf { it.isNotBlank() }

    fun follows(c: Context) = url(c) != null && HudTheme.isEnabled(c, KEY_FOLLOW)

    /** Normalises [raw] (adds http://, drops a trailing slash); null if it can't be a hub URL. */
    fun normalise(raw: String): String? {
        var u = raw.trim().trimEnd('/')
        if (u.isEmpty()) return null
        if (!u.contains("://")) u = "http://$u"
        val ok = Regex("^https?://[A-Za-z0-9.\\-\\[\\]:]+(/[A-Za-z0-9._~\\-/]*)?$").matches(u)
        return if (ok) u else null
    }

    /** Links (or with null, unlinks) a hub. */
    fun setUrl(c: Context, url: String?) {
        val p = HudTheme.prefs(c).edit()
        if (url == null) p.remove(KEY_URL).remove(KEY_PACK).remove(KEY_RYU) else p.putString(KEY_URL, url)
        p.apply()
        status = null; error = null; ryucore = null
        if (url == null) {
            pack = null
            HudTheme.hubChanged(c)
        }
        notifyAll()
        if (url != null) {
            refresh(c, theme = true)
            if (listeners.isNotEmpty() && !polling) {
                polling = true
                worker.postDelayed(poll, POLL_MS)
            }
        }
    }

    /** Launcher.onCreate: lets a fleet theme change recreate the activity so Launcher3 follows. */
    @JvmStatic
    fun attach(activity: Activity) {
        launcher = WeakReference(activity)
        val c = activity.applicationContext
        app = c
        if (pack == null) pack = HudTheme.prefs(c).getString(KEY_PACK, null)?.let { parsePack(it) }
        ryucore = HudTheme.prefs(c).getString(KEY_RYU, null)
        if (url(c) != null) refresh(c, theme = true)
    }

    // ── subscriptions ─────────────────────────────────────────────────────────────────────

    fun subscribe(c: Context, l: () -> Unit) {
        app = c.applicationContext
        if (pack == null) pack = HudTheme.prefs(c).getString(KEY_PACK, null)?.let { parsePack(it) }
        listeners.addIfAbsent(l)
        if (!polling && url(c) != null) {
            polling = true
            worker.post(poll)
        }
    }

    fun unsubscribe(l: () -> Unit) {
        listeners.remove(l)
        if (listeners.isEmpty()) {
            polling = false
            worker.removeCallbacks(poll)
        }
    }

    private fun notifyAll() = main.post { listeners.forEach { it() } }

    private val poll = object : Runnable {
        override fun run() {
            val c = app ?: return
            fetchStatus(c)
            if (polling && url(c) != null) worker.postDelayed(this, POLL_MS) else polling = false
        }
    }

    /** One-off refresh on the worker thread. */
    fun refresh(c: Context, theme: Boolean = false) {
        app = c.applicationContext
        worker.post {
            if (theme) fetchTheme(c.applicationContext)
            fetchStatus(c.applicationContext)
        }
    }

    // ── HTTP ──────────────────────────────────────────────────────────────────────────────

    private fun request(url: String, body: String? = null): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 4000
            conn.readTimeout = 6000
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("Accept", "application/json")
            if (body != null) {
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("X-Hearth", "1")
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = conn.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            val bytes = conn.inputStream.use { s ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(8192)
                while (true) {
                    val n = s.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > MAX_BODY) throw IllegalStateException("response too large")
                }
                out.toByteArray()
            }
            return String(bytes, Charsets.UTF_8)
        } finally {
            conn.disconnect()
        }
    }

    private fun fetchStatus(c: Context) {
        val base = url(c) ?: return
        try {
            val st = parseStatus(JSONObject(request("$base/api/status")))
            status = st
            error = null
            if (st.themeRev != pack?.rev) fetchTheme(c)
        } catch (e: Exception) {
            error = e.message ?: e.javaClass.simpleName
            Log.i(TAG, "status from $base failed: $error")
        }
        notifyAll()
    }

    private fun fetchTheme(c: Context) {
        val base = url(c) ?: return
        try {
            val raw = request("$base/api/theme")
            val p = parsePack(raw) ?: return
            val oldTheme = HudTheme.current(c).id
            val oldPalettes = pack?.palettes?.toString()
            pack = p
            HudTheme.prefs(c).edit().putString(KEY_PACK, raw).apply()
            ryucore = try {
                JSONObject(request("$base/api/ryucore")).optString("url").takeIf { it.isNotBlank() }
            } catch (e: Exception) { null }
            HudTheme.prefs(c).edit().putString(KEY_RYU, ryucore).apply()
            main.post {
                HudTheme.hubChanged(c)
                val now = HudTheme.current(c).id
                if (follows(c) && (now != oldTheme || p.palettes.toString() != oldPalettes)) {
                    launcher?.get()?.let { a -> if (!a.isFinishing && !a.isDestroyed) a.recreate() }
                }
            }
        } catch (e: Exception) {
            Log.i(TAG, "theme from $base failed: ${e.message}")
        }
    }

    /** The Command deck picked [id]: make it the fleet theme too (fire and forget). */
    fun pushTheme(c: Context, id: String) {
        if (!follows(c)) return
        val base = url(c) ?: return
        worker.post {
            try {
                request("$base/api/theme", JSONObject().put("theme", id).toString())
                fetchTheme(c.applicationContext)
            } catch (e: Exception) {
                Log.i(TAG, "theme push failed: ${e.message}")
            }
        }
    }

    // ── parsing ───────────────────────────────────────────────────────────────────────────

    private fun JSONObject.dbl(k: String): Double? = if (isNull(k) || !has(k)) null else optDouble(k)
    private fun JSONObject.int(k: String): Int? = if (isNull(k) || !has(k)) null else optInt(k)

    private fun parseStatus(j: JSONObject): HubStatus {
        val fleet = ArrayList<FleetNode>()
        val fa = j.optJSONArray("fleet") ?: JSONArray()
        for (i in 0 until fa.length()) {
            val n = fa.optJSONObject(i) ?: continue
            fleet += FleetNode(n.optString("id"), n.optString("name", n.optString("id")),
                n.optBoolean("local"), n.optBoolean("online"), n.dbl("cpu"), n.dbl("mem"), n.dbl("hot"),
                n.int("running"), n.int("total"), n.optInt("alerts"))
        }
        val events = ArrayList<HubEvent>()
        val ea = j.optJSONArray("events") ?: JSONArray()
        for (i in 0 until ea.length()) {
            val e = ea.optJSONObject(i) ?: continue
            events += HubEvent(e.optLong("id"), e.optLong("t"), e.optString("lvl"), e.optString("src"),
                e.optString("msg"), e.optString("node"), e.optBoolean("rec"))
        }
        return HubStatus(j.optString("hub"), fleet, events.sortedByDescending { it.id },
            j.optJSONArray("maint")?.length() ?: 0,
            j.optJSONObject("theme")?.optString("rev") ?: "", SystemClock.elapsedRealtime())
    }

    private fun line(o: JSONObject?): HubLine? {
        o ?: return null
        val k = o.optString("kao"); val e = o.optString("en"); val j = o.optString("jp")
        return if (k.isBlank() || e.isBlank()) null else HubLine(k.take(40), e.take(300), j.take(80))
    }

    private fun parsePack(raw: String): HubPack? = try {
        val j = JSONObject(raw)
        val pals = ArrayList<JSONObject>()
        j.optJSONArray("palettes")?.let { a -> for (i in 0 until a.length()) a.optJSONObject(i)?.let(pals::add) }
        val k = j.optJSONObject("kagari")
        val greet = ArrayList<Pair<Int, HubLine>>()
        k?.optJSONArray("greetings")?.let { a ->
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                line(o)?.let { greet += o.optInt("until", 24) to it }
            }
        }
        val idle = ArrayList<HubLine>()
        k?.optJSONArray("idle")?.let { a -> for (i in 0 until a.length()) line(a.optJSONObject(i))?.let(idle::add) }
        HubPack(j.optString("theme", "hearth"), j.optString("rev"), pals, greet.sortedBy { it.first }, idle)
    } catch (e: Exception) {
        Log.w(TAG, "bad theme pack", e)
        null
    }

    // ── shortcuts ─────────────────────────────────────────────────────────────────────────

    /** Opens a hub page (or RyuCore) in the browser. */
    fun open(c: Context, page: String) {
        val base = url(c) ?: return
        val target = when (page) {
            "ryucore" -> ryucore ?: return
            else -> "$base/$page"
        }
        try {
            c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Log.w(TAG, "can't open $target", e)
        }
    }

    /** Short human age of an epoch-ms time. */
    fun ago(t: Long): String {
        val s = ((System.currentTimeMillis() - t) / 1000).coerceAtLeast(0)
        return when {
            s < 60 -> "${s}s"
            s < 3600 -> "${s / 60}m"
            s < 86400 -> "${s / 3600}h"
            else -> "${s / 86400}d"
        }
    }
}

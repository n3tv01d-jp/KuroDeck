/*
 * Copyright (C) 2026 KuroDeck contributors
 * Licensed under the Apache License, Version 2.0.
 */
package com.kurodeck.hud

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.os.Build
import android.provider.Settings
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import android.text.format.DateFormat
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.Launcher
import com.android.launcher3.views.BaseDragLayer
import com.kurodeck.theme.HudPalette
import com.kurodeck.theme.HudPalette.Companion.withAlpha
import com.kurodeck.theme.HudTheme
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * KuroDeck's HUD pull-down panel (`[06] CONSOLE 操作盤`), opened by swiping down on the home
 * screen. Swipes that start on the status bar still open the system shade.
 *
 * It holds a clock and status readout, quick tiles, a brightness slider and the notification
 * list. Android only lets an app toggle some settings directly (torch, Do Not Disturb, rotation,
 * brightness). The other tiles open the system's own small settings panel or settings page.
 */
@SuppressLint("ViewConstructor")
class HudShadePanel private constructor(private val launcher: Launcher) :
    AbstractFloatingView(launcher, null) {

    private val ctx: Context = launcher
    private val pal: HudPalette = HudTheme.current(ctx)
    private val sheet = LinearLayout(ctx)
    private val listScroll = ScrollView(ctx)
    private val list = LinearLayout(ctx)
    private val header = HeaderView(ctx, pal)
    private val readout = TextView(ctx)
    private val clearAll = chip("CLEAR ALL")
    private val tiles = ArrayList<TileView>()
    private lateinit var slider: SliderView
    private lateinit var autoChip: TextView

    private var progress = 0f
    private var animator: ValueAnimator? = null
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private val flingVelocity = ViewConfiguration.get(ctx).scaledMinimumFlingVelocity * 4f

    // Drag-to-close state (touches inside the panel).
    private var velocity: VelocityTracker? = null
    private var downX = 0f
    private var downY = 0f
    private var downInList = false
    private var closing = false
    private var closeStart = 1f

    private val onNotifications: () -> Unit = { post(::rebuildNotifications) }
    private val onSample: (TelemetrySample) -> Unit = { updateReadout(it) }
    private val fleetText = TextView(ctx)
    private val onHub: () -> Unit = { post(::refreshFleet) }
    private val torch = Torch(ctx) { post { tiles.forEach { it.refresh() } } }

    init {
        orientation = VERTICAL
        setWillNotDraw(false)
        clipChildren = false
        isFocusable = true
        contentDescription = "KuroDeck panel"
        build()
    }

    // ── layout ────────────────────────────────────────────────────────────────────────────

    private fun build() {
        val pad = ctx.dp(14f).toInt()
        sheet.orientation = LinearLayout.VERTICAL
        sheet.background = PanelDrawable(ctx, pal)
        sheet.setPadding(pad, ctx.dp(10f).toInt(), pad, ctx.dp(4f).toInt())
        sheet.isClickable = true // touches on blank areas stay in the sheet
        addView(sheet, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        sheet.addView(header, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, ctx.dp(38f).toInt()))
        sheet.addView(clockRow())

        sheet.addView(section("QUICK // 即時"))
        val specs = tileSpecs()
        specs.chunked(4).forEach { rowSpecs ->
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            rowSpecs.forEachIndexed { i, spec ->
                val t = TileView(ctx, pal, spec)
                tiles += t
                row.addView(t, LinearLayout.LayoutParams(0, ctx.dp(60f).toInt(), 1f).apply {
                    if (i > 0) marginStart = ctx.dp(6f).toInt()
                })
            }
            sheet.addView(row, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT).apply { topMargin = ctx.dp(6f).toInt() })
        }

        if (HearthLink.url(ctx) != null) {
            sheet.addView(section("FLEET // 艦隊"))
            fleetText.typeface = HudTheme.mono(ctx)
            fleetText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            fleetText.setTextColor(pal.dim)
            fleetText.setLineSpacing(0f, 1.15f)
            sheet.addView(fleetText, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            fleetSpecs().forEachIndexed { i, spec ->
                val t = TileView(ctx, pal, spec)
                tiles += t
                row.addView(t, LinearLayout.LayoutParams(0, ctx.dp(60f).toInt(), 1f).apply {
                    if (i > 0) marginStart = ctx.dp(6f).toInt()
                })
            }
            sheet.addView(row, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT).apply { topMargin = ctx.dp(6f).toInt() })
        }

        sheet.addView(brightnessRow())

        val sigRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        sigRow.addView(section("SIGNALS // 通知"), LinearLayout.LayoutParams(0,
            LayoutParams.WRAP_CONTENT, 1f))
        clearAll.setOnClickListener { HudNotificationService.dismissAll() }
        sigRow.addView(clearAll)
        sheet.addView(sigRow, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT).apply { topMargin = ctx.dp(4f).toInt() })

        list.orientation = LinearLayout.VERTICAL
        listScroll.isVerticalScrollBarEnabled = false
        listScroll.overScrollMode = OVER_SCROLL_NEVER
        listScroll.addView(list, LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        // Weight 1 with height 0: the list takes what is left of the screen and scrolls.
        sheet.addView(listScroll, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
            .apply { topMargin = ctx.dp(6f).toInt() })

        sheet.addView(Grip(ctx, pal).apply {
            contentDescription = "Close panel"
            setOnClickListener { close(true) }
        }, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, ctx.dp(26f).toInt()))
    }

    private fun clockRow(): View {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, ctx.dp(4f).toInt(), 0, ctx.dp(2f).toInt())
        }
        val left = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        left.addView(TextClock(ctx).apply {
            format12Hour = "hh:mm"
            format24Hour = "HH:mm"
            typeface = HudTheme.display(ctx)
            fontVariationSettings = "'wght' 800"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 34f)
            letterSpacing = .06f
            setTextColor(0xFFFFFFFF.toInt())
            setShadowLayer(ctx.dp(10f), 0f, 0f, pal.a1d)
            includeFontPadding = false
        })
        left.addView(TextClock(ctx).apply {
            format12Hour = "EEE dd MMM yyyy"
            format24Hour = "EEE dd MMM yyyy"
            isAllCaps = true
            typeface = HudTheme.mono(ctx)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            letterSpacing = .1f
            setTextColor(pal.a2)
        })
        row.addView(left, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        readout.apply {
            typeface = HudTheme.mono(ctx)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(pal.dim)
            gravity = Gravity.END
            setLineSpacing(ctx.dp(2f), 1f)
        }
        row.addView(readout)
        return row
    }

    private fun brightnessRow(): View {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, ctx.dp(10f).toInt(), 0, ctx.dp(2f).toInt())
        }
        row.addView(TextView(ctx).apply {
            text = "LUMEN\n輝度"
            typeface = HudTheme.display(ctx)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            letterSpacing = .14f
            setTextColor(pal.a3)
        })
        slider = SliderView(ctx, pal,
            canChange = {
                if (Settings.System.canWrite(ctx)) true
                else { requestWriteSettings(); false }
            },
            onChange = { setBrightness(it) })
        row.addView(slider, LinearLayout.LayoutParams(0, ctx.dp(32f).toInt(), 1f).apply {
            marginStart = ctx.dp(10f).toInt()
            marginEnd = ctx.dp(8f).toInt()
        })
        autoChip = chip("AUTO")
        autoChip.setOnClickListener {
            if (!Settings.System.canWrite(ctx)) { requestWriteSettings(); return@setOnClickListener }
            val auto = isAutoBrightness()
            Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                if (auto) Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                else Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
            refreshBrightness()
        }
        row.addView(autoChip)
        return row
    }

    private fun section(title: String) = TextView(ctx).apply {
        text = title
        typeface = HudTheme.display(ctx)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
        letterSpacing = .2f
        setTextColor(pal.a3)
        setPadding(0, ctx.dp(10f).toInt(), 0, ctx.dp(2f).toInt())
    }

    private fun chip(label: String) = TextView(ctx).apply {
        text = label
        typeface = HudTheme.display(ctx)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
        letterSpacing = .14f
        gravity = Gravity.CENTER
        setTextColor(pal.text)
        background = PanelDrawable(ctx, pal, PanelDrawable.Style.CHIP)
        val h = ctx.dp(10f).toInt()
        setPadding(h, ctx.dp(6f).toInt(), h, ctx.dp(6f).toInt())
        isClickable = true
        isFocusable = true
    }

    // ── open / close ──────────────────────────────────────────────────────────────────────

    /** Opening drag from [HudShadeSwipeController]: [dy] pixels pulled down so far. */
    fun onOpenDrag(dy: Float) {
        animator?.cancel()
        setProgress(dy / sheetTravel())
    }

    /** Opening drag released with [velocityY] (px/s, positive = down). */
    fun onOpenRelease(velocityY: Float) = settle(velocityY)

    private fun settle(velocityY: Float) {
        val open = when {
            velocityY > flingVelocity -> true
            velocityY < -flingVelocity -> false
            else -> progress > .35f
        }
        if (open) animateTo(1f) else close(true)
    }

    private fun animateTo(target: Float, end: (() -> Unit)? = null) {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(progress, target).apply {
            duration = (260 * abs(target - progress)).toLong().coerceAtLeast(90)
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener { setProgress(it.animatedValue as Float) }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(a: Animator) { cancelled = true }
                override fun onAnimationEnd(a: Animator) {
                    animator = null
                    if (!cancelled) end?.invoke()
                }
            })
            start()
        }
    }

    private fun sheetTravel(): Float {
        val b = sheet.bottom.toFloat()
        return if (b > 0) b else resources.displayMetrics.heightPixels * .7f
    }

    private fun setProgress(p: Float) {
        progress = p.coerceIn(0f, 1f)
        sheet.translationY = -sheetTravel() * (1f - progress)
        sheet.alpha = .4f + .6f * progress
        invalidate()
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        setProgress(progress) // the travel distance is known now
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(withAlpha(pal.bg, .78f * progress))
    }

    override fun handleClose(animate: Boolean) {
        if (!mIsOpen) return
        mIsOpen = false
        if (animate && progress > 0f) {
            animateTo(0f) { removeSelf() }
        } else {
            animator?.cancel()
            removeSelf()
        }
    }

    private fun removeSelf() {
        (parent as? ViewGroup)?.removeView(this)
    }

    override fun isOfType(type: Int) = type and TYPE_KURODECK_PANEL != 0

    override fun onControllerInterceptTouchEvent(ev: MotionEvent): Boolean {
        // A tap below the sheet closes the panel (and is consumed).
        if (ev.action == MotionEvent.ACTION_DOWN &&
            !launcher.dragLayer.isEventOverView(sheet, ev)) {
            close(true)
            return true
        }
        return false
    }

    // Drag up on the sheet to close it. Inside the list, only once the list is at its end.
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = trackClose(ev, true)

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        trackClose(ev, false)
        return true
    }

    @Suppress("UNUSED_PARAMETER")
    private fun trackClose(ev: MotionEvent, intercepting: Boolean): Boolean {
        if (velocity == null) velocity = VelocityTracker.obtain()
        velocity?.addMovement(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x; downY = ev.y
                closing = false
                val r = android.graphics.Rect()
                listScroll.getGlobalVisibleRect(r)
                downInList = r.contains(ev.rawX.toInt(), ev.rawY.toInt())
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = ev.y - downY
                val dx = ev.x - downX
                if (!closing && mIsOpen && dy < -slop && abs(dy) > abs(dx) &&
                    (!downInList || !listScroll.canScrollVertically(1))) {
                    closing = true
                    closeStart = progress
                    animator?.cancel()
                }
                if (closing) setProgress(closeStart + (dy + slop) / sheetTravel())
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (closing) {
                    velocity?.computeCurrentVelocity(1000)
                    settle(velocity?.yVelocity ?: 0f)
                }
                closing = false
                velocity?.recycle(); velocity = null
            }
        }
        return closing
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        HudNotificationService.addListener(onNotifications)
        Telemetry.subscribe(ctx, onSample)
        HearthLink.subscribe(ctx, onHub)
        torch.start()
        refreshAll()
    }

    override fun onDetachedFromWindow() {
        HudNotificationService.removeListener(onNotifications)
        Telemetry.unsubscribe(onSample)
        HearthLink.unsubscribe(onHub)
        torch.stop()
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        // Back from a settings panel or permission screen: show the new states.
        if (hasWindowFocus) refreshAll()
    }

    private fun refreshAll() {
        tiles.forEach { it.refresh() }
        refreshBrightness()
        rebuildNotifications()
        Telemetry.latest?.let(::updateReadout)
        refreshFleet()
    }

    // ── fleet (HEARTH hub) ────────────────────────────────────────────────────────────────

    private fun refreshFleet() {
        if (HearthLink.url(ctx) == null) return
        val st = HearthLink.status
        val err = HearthLink.error
        val sb = StringBuilder()
        if (st == null) {
            sb.append(if (err != null) "HUB UNREACHABLE // $err" else "LINKING…")
        } else {
            val on = st.fleet.count { it.online }
            sb.append("HUB ${st.hub.uppercase()} · $on/${st.fleet.size} ONLINE")
            if (st.maint > 0) sb.append(" · MAINT")
            if (err != null) sb.append(" · STALE")
            st.fleet.filter { !it.online }.forEach { sb.append("\n✖ ${it.name.uppercase()} SIGNAL LOST") }
            st.events.filter { !it.rec && (it.lvl == "crit" || it.lvl == "warn") }.take(3).forEach {
                sb.append("\n${if (it.lvl == "crit") "▲" else "△"} ${HearthLink.ago(it.t)} ${it.node}: ${it.msg}")
            }
        }
        fleetText.text = sb.toString()
        tiles.forEach { it.refresh() }
    }

    private fun fleetSpecs(): List<TileSpec> = listOf(
        TileSpec("HEARTH", "炉心", {
            val st = HearthLink.status
            if (st == null) "--" to false
            else "${st.fleet.count { it.online }}/${st.fleet.size}" to
                st.fleet.all { it.online && it.alerts == 0 }
        }, { close(false); HearthLink.open(ctx, "hearth.html") },
            { close(false); HearthLink.open(ctx, "arsenal.html") }),
        TileSpec("GATE", "転送門", { "OPEN" to false }, { close(false); HearthLink.open(ctx, "gate.html") }),
        TileSpec("RYUCORE", "竜核", { (if (HearthLink.ryucore != null) "OPEN" else "N/A") to false }, {
            if (HearthLink.ryucore != null) { close(false); HearthLink.open(ctx, "ryucore") }
            else toast("Link RyuCore on HEARTH's start page first")
        }),
    )

    private fun updateReadout(s: TelemetrySample) {
        val bolt = if (s.charging) " ⚡" else ""
        readout.text = "BAT %03d%%%s\nLINK %s\nMEM %02d%%".format(s.batteryPct, bolt, s.link,
            s.memUsedPct)
    }

    // ── brightness ────────────────────────────────────────────────────────────────────────

    private fun isAutoBrightness() = Settings.System.getInt(ctx.contentResolver,
        Settings.System.SCREEN_BRIGHTNESS_MODE, 0) ==
        Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC

    private fun refreshBrightness() {
        val raw = Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
        // The setting is roughly linear in light; a gamma curve makes the slider feel even.
        slider.setValue((raw / 255f).coerceIn(0f, 1f).pow(1f / 2.2f))
        val auto = isAutoBrightness()
        autoChip.background = PanelDrawable(ctx, pal,
            if (auto) PanelDrawable.Style.CHIP_ON else PanelDrawable.Style.CHIP)
        autoChip.setTextColor(if (auto) pal.bg else pal.text)
    }

    private fun setBrightness(v: Float) {
        val cr = ctx.contentResolver
        if (isAutoBrightness()) {
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            refreshBrightness()
        }
        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS,
            (255 * v.pow(2.2f)).roundToInt().coerceIn(1, 255))
    }

    private fun requestWriteSettings() {
        toast("Allow \"Modify system settings\" for KuroDeck")
        start(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
            Uri.parse("package:" + ctx.packageName)))
    }

    // ── tiles ─────────────────────────────────────────────────────────────────────────────

    /** A quick tile. [state] returns its label (e.g. ON / OFF) and whether it is lit. */
    class TileSpec(
        val label: String,
        val jp: String,
        val state: () -> Pair<String, Boolean>,
        val onTap: () -> Unit,
        val onLong: (() -> Unit)? = null,
    )

    private fun onOff(on: Boolean?) = when (on) {
        true -> "ON" to true
        false -> "OFF" to false
        null -> "--" to false
    }

    private fun tileSpecs(): List<TileSpec> {
        val wifi = ctx.getSystemService(WifiManager::class.java)
        val audio = ctx.getSystemService(AudioManager::class.java)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val loc = ctx.getSystemService(LocationManager::class.java)
        val bt = ctx.getSystemService(BluetoothManager::class.java)
        return listOf(
            TileSpec("WI-FI", "無線", { onOff(safe { wifi?.isWifiEnabled }) },
                { start(Intent(Settings.Panel.ACTION_WIFI)) },
                { start(Intent(Settings.ACTION_WIFI_SETTINGS)) }),
            TileSpec("NETWORK", "回線", {
                val link = Telemetry.latest?.link ?: "--"
                link to (link != "NO LINK" && link != "--")
            }, { start(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)) },
                { start(Intent(Settings.ACTION_WIRELESS_SETTINGS)) }),
            TileSpec("BLUETOOTH", "青歯", { onOff(safe { bt?.adapter?.isEnabled }) },
                { start(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }),
            TileSpec("TORCH", "灯火", {
                if (torch.available) onOff(torch.on) else "N/A" to false
            }, {
                if (torch.available) torch.toggle() else toast("No flashlight")
            }),
            TileSpec("SILENT", "静音", {
                onOff(nm != null &&
                    nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL)
            }, {
                if (nm != null && nm.isNotificationPolicyAccessGranted) {
                    val on = nm.currentInterruptionFilter !=
                        NotificationManager.INTERRUPTION_FILTER_ALL
                    nm.setInterruptionFilter(if (on) NotificationManager.INTERRUPTION_FILTER_ALL
                        else NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                    refreshTilesSoon()
                } else {
                    toast("Allow Do Not Disturb access for KuroDeck")
                    start(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                }
            }, { start(Intent(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS)) }),
            TileSpec("ROTATE", "回転", {
                onOff(Settings.System.getInt(ctx.contentResolver,
                    Settings.System.ACCELEROMETER_ROTATION, 0) == 1)
            }, {
                if (Settings.System.canWrite(ctx)) {
                    val on = Settings.System.getInt(ctx.contentResolver,
                        Settings.System.ACCELEROMETER_ROTATION, 0) == 1
                    Settings.System.putInt(ctx.contentResolver,
                        Settings.System.ACCELEROMETER_ROTATION, if (on) 0 else 1)
                    refreshTilesSoon()
                } else requestWriteSettings()
            }),
            TileSpec("VOLUME", "音量", {
                when (audio?.ringerMode) {
                    AudioManager.RINGER_MODE_NORMAL -> "RING" to true
                    AudioManager.RINGER_MODE_VIBRATE -> "VIBE" to false
                    AudioManager.RINGER_MODE_SILENT -> "MUTE" to false
                    else -> "--" to false
                }
            }, { start(Intent(Settings.Panel.ACTION_VOLUME)) },
                { start(Intent(Settings.ACTION_SOUND_SETTINGS)) }),
            TileSpec("NFC", "近接", {
                val a = safe { NfcAdapter.getDefaultAdapter(ctx) }
                if (a == null) "N/A" to false else onOff(safe { a.isEnabled })
            }, { start(Intent(Settings.Panel.ACTION_NFC)) }),
            TileSpec("LOCATION", "位置", { onOff(safe { loc?.isLocationEnabled }) },
                { start(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }),
            TileSpec("AIRPLANE", "機内", {
                onOff(Settings.Global.getInt(ctx.contentResolver,
                    Settings.Global.AIRPLANE_MODE_ON, 0) == 1)
            }, { start(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)) }),
            TileSpec("SETTINGS", "設定", { "OPEN" to false },
                { close(false); start(Intent(Settings.ACTION_SETTINGS)) }),
            TileSpec("SYSTEM", "系統", { "SHADE" to false }, { openSystemShade() }),
        )
    }

    private fun refreshTilesSoon() = postDelayed({ tiles.forEach { it.refresh() } }, 150)

    private fun <T> safe(block: () -> T?): T? = try { block() } catch (e: Exception) { null }

    /**
     * Opens the real system shade. There is no public API for this, so it may be refused by
     * the platform. Then the user is reminded that a swipe from the status bar opens it.
     */
    @SuppressLint("WrongConstant")
    private fun openSystemShade() {
        close(false)
        try {
            val sb = ctx.getSystemService("statusbar")
            sb.javaClass.getMethod("expandNotificationsPanel").invoke(sb)
        } catch (e: Throwable) {
            Log.i(TAG, "expandNotificationsPanel unavailable: $e")
            toast("Swipe down from the status bar for the system shade")
        }
    }

    private fun start(intent: Intent) {
        try {
            launcher.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Log.w(TAG, "Can't open $intent", e)
            toast("Not available on this device")
        }
    }

    private fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

    // ── notifications ─────────────────────────────────────────────────────────────────────

    private fun rebuildNotifications() {
        list.removeAllViews()
        val granted = HudNotificationService.isGranted(ctx)
        val items = if (granted) HudNotificationService.active() else emptyList()
        header.meta = if (granted) "SIG %02d".format(items.size) else "SIG --"
        header.invalidate()
        clearAll.visibility = if (items.any { it.isClearable }) VISIBLE else GONE

        if (!granted) {
            list.addView(notice("Notification access is off. KuroDeck needs it to list and " +
                "dismiss notifications here.", "GRANT ACCESS") {
                HudNotificationService.requestAccess(ctx)
            })
            return
        }
        if (items.isEmpty()) {
            list.addView(notice(if (HudNotificationService.isConnected()) "NO SIGNALS // 通知なし"
                else "LINKING // 接続中…", null, null))
            return
        }
        items.forEach { sbn ->
            list.addView(NotificationRow(ctx, pal, sbn,
                onOpen = { openNotification(sbn) },
                onDismiss = { HudNotificationService.dismiss(sbn) }),
                LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                    .apply { bottomMargin = ctx.dp(6f).toInt() })
        }
    }

    private fun notice(msg: String, action: String?, onAction: (() -> Unit)?): View {
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, ctx.dp(14f).toInt(), 0, ctx.dp(14f).toInt())
        }
        box.addView(TextView(ctx).apply {
            text = msg
            typeface = HudTheme.ui(ctx)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(pal.dim)
            gravity = Gravity.CENTER
        })
        if (action != null && onAction != null) {
            box.addView(chip(action).apply { setOnClickListener { onAction() } },
                LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = ctx.dp(10f).toInt() })
        }
        return box
    }

    private fun openNotification(sbn: StatusBarNotification) {
        val n = sbn.notification
        val pi = n.contentIntent
        try {
            if (pi != null) {
                pi.send(ctx, 0, null, null, null, null, sendOptions())
            } else {
                ctx.packageManager.getLaunchIntentForPackage(sbn.packageName)?.let(::start)
            }
        } catch (e: PendingIntent.CanceledException) {
            Log.w(TAG, "Notification intent cancelled", e)
        }
        if (n.flags and Notification.FLAG_AUTO_CANCEL != 0) HudNotificationService.dismiss(sbn)
        close(true)
    }

    /** Lets the notification start its activity using the launcher's foreground status. */
    private fun sendOptions() = ActivityOptions.makeBasic().apply {
        if (Build.VERSION.SDK_INT >= 36) {
            setPendingIntentBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE)
        } else if (Build.VERSION.SDK_INT >= 34) {
            @Suppress("DEPRECATION")
            setPendingIntentBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
        }
    }.toBundle()

    companion object {
        private const val TAG = "HudShadePanel"

        /** Adds a closed panel to the drag layer, ready to follow the opening drag. */
        @JvmStatic
        fun open(launcher: Launcher): HudShadePanel {
            getOpen(launcher)?.close(false)
            val panel = HudShadePanel(launcher)
            val insets = launcher.dragLayer.insets
            val side = launcher.dp(8f).toInt()
            panel.setPadding(side + insets.left, insets.top + launcher.dp(4f).toInt(),
                side + insets.right, insets.bottom + launcher.dp(28f).toInt())
            panel.markOpen()
            launcher.dragLayer.addView(panel, BaseDragLayer.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                .apply { ignoreInsets = true })
            panel.setProgress(0f)
            return panel
        }

        @JvmStatic
        fun getOpen(launcher: Launcher): HudShadePanel? =
            AbstractFloatingView.getOpenView(launcher, AbstractFloatingView.TYPE_KURODECK_PANEL)
    }

    private fun markOpen() { mIsOpen = true }

    // ── views ─────────────────────────────────────────────────────────────────────────────

    private class HeaderView(c: Context, private val pal: HudPalette) : View(c) {
        private val hud = HudDraw(c)
        var meta: String = ""
        init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
        override fun onDraw(canvas: Canvas) {
            hud.header(canvas, pal, "06", "CONSOLE", "操作盤", meta, 0f, context.dp(4f),
                width.toFloat())
        }
    }

    /** Drag handle at the bottom of the sheet. */
    private class Grip(c: Context, private val pal: HudPalette) : View(c) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        init { isClickable = true; isFocusable = true }
        override fun onDraw(canvas: Canvas) {
            val w = context.dp(56f)
            val cx = width / 2f
            val cy = height / 2f
            p.color = pal.line2
            canvas.drawRect(cx - w / 2, cy - context.dp(1.5f), cx + w / 2, cy + context.dp(1.5f), p)
            p.color = pal.a1
            p.setShadowLayer(context.dp(6f), 0f, 0f, pal.a1d)
            canvas.drawRect(cx - w / 6, cy - context.dp(1.5f), cx + w / 6, cy + context.dp(1.5f), p)
            p.clearShadowLayer()
        }
    }

    /** Chamfered quick tile: `LABEL / STATE / 日本語`, lit when on. */
    private class TileView(c: Context, private val pal: HudPalette, private val spec: TileSpec) :
        View(c) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val hud = HudDraw(c)
        private var stateText = ""
        private var on = false

        init {
            isClickable = true
            isFocusable = true
            setOnClickListener { spec.onTap(); refresh() }
            spec.onLong?.let { l -> setOnLongClickListener { l(); true } }
            refresh()
        }

        fun refresh() {
            val (s, lit) = try { spec.state() } catch (e: Exception) { "--" to false }
            if (s != stateText || lit != on) {
                stateText = s; on = lit
                background = PanelDrawable(context, pal,
                    if (on) PanelDrawable.Style.CHIP_ON else PanelDrawable.Style.CHIP)
                contentDescription = "${spec.label}, $s"
                invalidate()
            }
        }

        override fun drawableStateChanged() {
            super.drawableStateChanged()
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val ink = if (on) pal.bg else pal.text
            if (isPressed && !on) {
                p.color = withAlpha(pal.a1, .16f)
                val path = Path()
                hud.chamfer(path, 1f, 1f, width - 1f, height - 1f, context.dp(8f))
                canvas.drawPath(path, p)
            }
            val x = context.dp(8f)
            hud.font(p, HudDraw.Font.DISPLAY, 8.5f, ink, 700, .1f)
            canvas.drawText(TextUtils.ellipsize(spec.label, android.text.TextPaint(p),
                width - 2 * x, TextUtils.TruncateAt.END).toString(), x, context.dp(17f), p)
            hud.font(p, HudDraw.Font.MONO, 12f, if (on) pal.bg else pal.a1)
            canvas.drawText(stateText, x, context.dp(35f), p)
            hud.font(p, HudDraw.Font.JP, 9f, if (on) withAlpha(pal.bg, .8f) else pal.a2)
            canvas.drawText(spec.jp, x, height - context.dp(9f), p)
        }
    }

    /** Chamfered track with a glowing fill; reports 0..1 while dragged. */
    private class SliderView(
        c: Context,
        private val pal: HudPalette,
        private val canChange: () -> Boolean,
        private val onChange: (Float) -> Unit,
    ) : View(c) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val path = Path()
        private val hud = HudDraw(c)
        private var value = .5f
        private var dragging = false

        init {
            isFocusable = true
            contentDescription = "Brightness"
        }

        fun setValue(v: Float) {
            if (dragging) return
            value = v.coerceIn(0f, 1f)
            invalidate()
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    parent.requestDisallowInterceptTouchEvent(true)
                    if (!canChange()) return false
                    dragging = true
                    update(ev.x)
                }
                MotionEvent.ACTION_MOVE -> if (dragging) update(ev.x)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragging = false
            }
            return true
        }

        private fun update(x: Float) {
            val inset = context.dp(6f)
            value = ((x - inset) / (width - 2 * inset)).coerceIn(0f, 1f)
            onChange(value)
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val h = context.dp(14f)
            val t = (height - h) / 2f
            val cut = context.dp(5f)
            hud.chamfer(path, 1f, t, width - 1f, t + h, cut)
            p.style = Paint.Style.FILL
            p.color = withAlpha(pal.a1, .06f)
            canvas.drawPath(path, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = context.dp(1f)
            p.color = pal.line2
            canvas.drawPath(path, p)

            val fillR = 1f + (width - 2f) * value
            canvas.save()
            canvas.clipRect(0f, 0f, fillR, height.toFloat())
            hud.chamfer(path, context.dp(3f), t + context.dp(3f), width - context.dp(3f),
                t + h - context.dp(3f), cut - context.dp(2f))
            p.style = Paint.Style.FILL
            p.shader = android.graphics.LinearGradient(0f, 0f, width.toFloat(), 0f,
                pal.a1d, pal.a1, android.graphics.Shader.TileMode.CLAMP)
            canvas.drawPath(path, p)
            p.shader = null
            canvas.restore()

            p.color = pal.a1
            p.setShadowLayer(context.dp(8f), 0f, 0f, pal.a1d)
            canvas.drawRect(fillR - context.dp(1.5f), t - context.dp(3f), fillR + context.dp(1.5f),
                t + h + context.dp(3f), p)
            p.clearShadowLayer()

            hud.font(p, HudDraw.Font.MONO, 10f, pal.dim)
            val label = "%03d%%".format((value * 100).roundToInt())
            canvas.drawText(label, width - p.measureText(label) - context.dp(8f),
                t - context.dp(4f), p)
        }
    }

    /** One notification. Tap opens it; swipe sideways dismisses it (if it can be cleared). */
    @SuppressLint("ViewConstructor")
    private class NotificationRow(
        c: Context,
        pal: HudPalette,
        private val sbn: StatusBarNotification,
        private val onOpen: () -> Unit,
        private val onDismiss: () -> Unit,
    ) : FrameLayout(c) {
        private val slop = ViewConfiguration.get(c).scaledTouchSlop
        private var downX = 0f
        private var downY = 0f
        private var swiping = false
        private var tracker: VelocityTracker? = null

        init {
            background = PanelDrawable(c, pal, PanelDrawable.Style.CHIP)
            val pad = c.dp(10f).toInt()
            setPadding(pad, pad, pad, pad)
            isClickable = true
            isFocusable = true

            val n = sbn.notification
            val ex = n.extras
            val pm = c.packageManager
            val appLabel = try {
                pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString()
            } catch (e: Exception) { sbn.packageName }
            val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                ?: ex.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()
                ?: appLabel
            val body = ex.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                ?: ex.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                ?: ex.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
            contentDescription = "$appLabel: $title. ${body ?: ""}"

            val row = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(ImageView(c).apply {
                val icon = try { pm.getApplicationIcon(sbn.packageName) } catch (e: Exception) {
                    n.smallIcon?.loadDrawable(c)
                }
                setImageDrawable(icon)
            }, LinearLayout.LayoutParams(c.dp(30f).toInt(), c.dp(30f).toInt()).apply {
                marginEnd = c.dp(10f).toInt()
                topMargin = c.dp(2f).toInt()
            })
            val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
            val top = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
            top.addView(TextView(c).apply {
                text = appLabel
                isAllCaps = true
                typeface = HudTheme.display(c)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 8.5f)
                letterSpacing = .14f
                setTextColor(pal.a2)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            top.addView(TextView(c).apply {
                text = DateFormat.getTimeFormat(c).format(sbn.postTime)
                typeface = HudTheme.mono(c)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                setTextColor(pal.faint)
            })
            col.addView(top)
            col.addView(TextView(c).apply {
                text = title
                typeface = HudTheme.uiBold(c)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTextColor(pal.text)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
            if (!body.isNullOrBlank()) {
                col.addView(TextView(c).apply {
                    text = body
                    typeface = HudTheme.ui(c)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    setTextColor(pal.dim)
                    maxLines = 3
                    ellipsize = TextUtils.TruncateAt.END
                })
            }
            row.addView(col, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            addView(row)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(ev: MotionEvent): Boolean {
            if (tracker == null) tracker = VelocityTracker.obtain()
            tracker?.addMovement(ev)
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX; downY = ev.rawY; swiping = false
                    isPressed = true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - downX
                    val dy = ev.rawY - downY
                    if (!swiping && abs(dx) > slop && abs(dx) > abs(dy)) {
                        swiping = true
                        isPressed = false
                        parent.requestDisallowInterceptTouchEvent(true)
                    }
                    if (swiping) {
                        // Ongoing notifications resist: they can't be dismissed.
                        translationX = if (sbn.isClearable) dx else dx * .25f
                        alpha = 1f - (abs(translationX) / width).coerceIn(0f, .8f)
                    } else if (abs(dy) > slop) {
                        isPressed = false
                    }
                }
                MotionEvent.ACTION_UP -> {
                    tracker?.computeCurrentVelocity(1000)
                    val vx = tracker?.xVelocity ?: 0f
                    if (swiping) {
                        val far = abs(translationX) > width * .35f || abs(vx) > 1800f
                        if (sbn.isClearable && far) {
                            val dir = if (translationX + vx * .05f >= 0) 1 else -1
                            animate().translationX(dir * width.toFloat()).alpha(0f)
                                .setDuration(160).withEndAction(onDismiss).start()
                        } else {
                            animate().translationX(0f).alpha(1f).setDuration(160).start()
                        }
                    } else if (isPressed) {
                        performClick()
                        onOpen()
                    }
                    isPressed = false
                    tracker?.recycle(); tracker = null
                }
                MotionEvent.ACTION_CANCEL -> {
                    isPressed = false
                    animate().translationX(0f).alpha(1f).setDuration(160).start()
                    tracker?.recycle(); tracker = null
                }
            }
            return true
        }
    }

    /** Flashlight state through the public torch API (no camera permission needed). */
    private class Torch(c: Context, private val changed: () -> Unit) {
        private val cm = c.getSystemService(CameraManager::class.java)
        private val id: String? = try {
            cm?.cameraIdList?.firstOrNull {
                cm.getCameraCharacteristics(it)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (e: Exception) { null }
        val available get() = id != null
        var on = false
            private set
        private val callback = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                if (cameraId == id) { on = enabled; changed() }
            }
        }

        fun start() { try { cm?.registerTorchCallback(callback, null) } catch (e: Exception) {} }
        fun stop() { try { cm?.unregisterTorchCallback(callback) } catch (e: Exception) {} }
        fun toggle() {
            val camera = id ?: return
            try { cm?.setTorchMode(camera, !on) } catch (e: Exception) {
                Log.w(TAG, "torch failed", e)
            }
        }
    }
}

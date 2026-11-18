/*
 * Copyright (C) 2026 KuroDeck contributors
 * Licensed under the Apache License, Version 2.0.
 */
package com.kurodeck.hud

import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherState
import com.android.launcher3.util.TouchController
import com.kurodeck.theme.HudTheme
import kotlin.math.abs

/**
 * Opens [HudShadePanel] when the user swipes down on the home screen. The panel follows the
 * finger and then settles open or closed.
 *
 * Only swipes that start below the status bar are handled. The status bar is a system window
 * above the launcher, so swipes that start on it never reach here: they open the system shade.
 */
class HudShadeSwipeController(private val launcher: Launcher) : TouchController {
    private val slop = ViewConfiguration.get(launcher).scaledTouchSlop
    private var tracking = false
    private var downX = 0f
    private var downY = 0f
    private var panel: HudShadePanel? = null
    private var velocity: VelocityTracker? = null

    override fun onControllerInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                tracking = canStart(ev)
                downX = ev.x
                downY = ev.y
                resetVelocity()
                if (tracking) track(ev)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!tracking) return false
                track(ev)
                val dy = ev.y - downY
                val dx = ev.x - downX
                if (dy > slop && dy > abs(dx) * 1.3f) {
                    tracking = false
                    // Re-check: a drag or a popup may have started since the touch went down.
                    if (!canOpenNow()) return false
                    panel = HudShadePanel.open(launcher)
                    return true
                }
                if (dy < -slop || abs(dx) > slop * 2) tracking = false
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                tracking = false
                resetVelocity()
            }
        }
        return false
    }

    override fun onControllerTouchEvent(ev: MotionEvent): Boolean {
        val p = panel ?: return false
        track(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> p.onOpenDrag(ev.y - downY - slop)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                velocity?.computeCurrentVelocity(1000)
                val vy = if (ev.actionMasked == MotionEvent.ACTION_UP)
                    velocity?.yVelocity ?: 0f else 0f
                p.onOpenRelease(vy)
                panel = null
                resetVelocity()
            }
        }
        return true
    }

    private fun canStart(ev: MotionEvent): Boolean {
        if (!HudTheme.isEnabled(launcher, HudTheme.KEY_SHADE)) return false
        // Leave a margin under the status bar so near-edge swipes still feel like the system's.
        val top = launcher.dragLayer.insets.top + launcher.dp(6f)
        if (ev.y < top) return false
        return canOpenNow()
    }

    private fun canOpenNow(): Boolean =
        launcher.isInState(LauncherState.NORMAL) &&
            !launcher.workspace.isSwitchingState &&
            !launcher.dragController.isDragging &&
            AbstractFloatingView.getTopOpenView(launcher) == null

    private fun track(ev: MotionEvent) {
        if (velocity == null) velocity = VelocityTracker.obtain()
        velocity?.addMovement(ev)
    }

    private fun resetVelocity() {
        velocity?.recycle()
        velocity = null
    }

    override fun dump() = "HudShadeSwipeController(tracking=$tracking, open=${panel != null})"
}

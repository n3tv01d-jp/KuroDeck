/*
 * Copyright (C) 2026 KuroDeck contributors
 * Licensed under the Apache License, Version 2.0.
 */
package com.kurodeck.hud

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.android.launcher3.BubbleTextView
import com.android.launcher3.PagedView
import com.android.launcher3.R
import com.android.launcher3.allapps.AllAppsStore
import com.android.launcher3.allapps.AlphabeticalAppsList
import com.android.launcher3.allapps.BaseAllAppsAdapter
import com.android.launcher3.pageindicators.PageIndicator
import com.android.launcher3.popup.PopupContainerWithArrow
import com.android.launcher3.views.ActivityContext
import com.kurodeck.theme.HudPalette
import com.kurodeck.theme.HudPalette.Companion.withAlpha
import com.kurodeck.theme.HudTheme
import kotlin.math.ceil
import kotlin.math.max

/**
 * KuroDeck's paged app drawer: apps in A–Z order, laid out page by page in a fixed grid that
 * is swiped left and right (Samsung One UI style) instead of one long vertical list.
 * Each page is a widget-style HUD panel with a `[03] APPS アプリ` header, and a segmented HUD
 * indicator sits under the pages.
 *
 * The data comes from the same [AlphabeticalAppsList] that feeds Launcher3's A–Z list, so
 * filtering (personal / work tab, private space) and sorting stay identical. The drawer only
 * observes that list's adapter, so search and the rest of all apps are untouched.
 */
class HudAppDrawer(context: Context, private val store: AllAppsStore) : FrameLayout(context) {
    private val act: ActivityContext = ActivityContext.lookupContext(context)
    private val pal: HudPalette = HudTheme.current(context)
    val pager = HudAppsPagedView(context)
    private val indicator = HudPageIndicator(context, pal)
    private val emptyText = TextView(context)

    private var list: AlphabeticalAppsList? = null
    private var adapter: RecyclerView.Adapter<*>? = null
    private val iconPool = ArrayList<BubbleTextView>()
    private var cols = 0
    private var rows = 0
    private var cellHeight = 0
    private var attached = false

    private val rebuildTask = Runnable { rebuild() }
    private val observer = object : RecyclerView.AdapterDataObserver() {
        override fun onChanged() = scheduleRebuild()
        override fun onItemRangeChanged(start: Int, count: Int) = scheduleRebuild()
        override fun onItemRangeChanged(start: Int, count: Int, payload: Any?) = scheduleRebuild()
        override fun onItemRangeInserted(start: Int, count: Int) = scheduleRebuild()
        override fun onItemRangeRemoved(start: Int, count: Int) = scheduleRebuild()
        override fun onItemRangeMoved(from: Int, to: Int, count: Int) = scheduleRebuild()
    }

    init {
        id = R.id.kurodeck_app_drawer
        clipToPadding = false
        pager.setPageIndicator(indicator)
        pager.setPageSpacing(context.dp(14f).toInt())
        addView(pager, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
            bottomMargin = INDICATOR_HEIGHT_DP.let { context.dp(it).toInt() }
        })
        addView(indicator, LayoutParams(LayoutParams.MATCH_PARENT,
            context.dp(INDICATOR_HEIGHT_DP).toInt(), Gravity.BOTTOM))
        emptyText.apply {
            text = "NO APPS // 応用なし"
            typeface = HudTheme.display(context)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            letterSpacing = .2f
            setTextColor(pal.dim)
            gravity = Gravity.CENTER
            visibility = GONE
        }
        addView(emptyText, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    /** Shows the apps of [appsList] (the active profile tab), following its adapter's updates. */
    fun setSource(appsList: AlphabeticalAppsList?, appsAdapter: RecyclerView.Adapter<*>?) {
        if (appsList === list && appsAdapter === adapter) return
        if (attached) adapter?.unregisterAdapterDataObserver(observer)
        list = appsList
        adapter = appsAdapter
        if (attached) adapter?.registerAdapterDataObserver(observer)
        pager.setCurrentPage(0)
        scheduleRebuild()
    }

    /** Returns to the first page (when the drawer closes, like the A–Z list scrolls to top). */
    fun resetToFirstPage() {
        if (pager.childCount > 0 && pager.nextPage != 0) pager.setCurrentPage(0)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        adapter?.registerAdapterDataObserver(observer)
        scheduleRebuild()
    }

    override fun onDetachedFromWindow() {
        attached = false
        adapter?.unregisterAdapterDataObserver(observer)
        removeCallbacks(rebuildTask)
        super.onDetachedFromWindow()
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        if (updateGrid()) scheduleRebuild()
    }

    private fun scheduleRebuild() {
        removeCallbacks(rebuildTask)
        post(rebuildTask)
    }

    /** Recomputes columns and rows from the page size. Returns true if they changed. */
    private fun updateGrid(): Boolean {
        val profile = act.deviceProfile.allAppsProfile
        val c = max(1, profile.numShownAllAppsColumns)
        val cell = max(1, profile.cellHeightPx)
        val contentH = pager.measuredHeight - pager.paddingTop - pager.paddingBottom -
            HudAppPage.chromeHeight(context)
        if (contentH <= 0) return false
        val r = (contentH / cell).coerceIn(1, MAX_ROWS)
        if (c == cols && r == rows && cell == cellHeight) return false
        cols = c
        rows = r
        cellHeight = cell
        return true
    }

    private fun rebuild() {
        if (cols == 0 || rows == 0) {
            if (!updateGrid()) return
        }
        val apps = list?.adapterItems
            ?.filter { BaseAllAppsAdapter.isIconViewType(it.viewType) && it.itemInfo != null }
            .orEmpty()

        val keepPage = pager.nextPage
        for (i in 0 until pager.childCount) {
            val page = pager.getChildAt(i) as HudAppPage
            store.unregisterIconContainer(page)
            for (j in 0 until page.childCount) {
                (page.getChildAt(j) as? BubbleTextView)?.let { iconPool.add(it) }
            }
            page.removeAllViews()
        }
        pager.removeAllViews()

        emptyText.visibility = if (apps.isEmpty()) VISIBLE else GONE
        if (apps.isEmpty()) return

        val perPage = cols * rows
        val pageCount = max(1, ceil(apps.size / perPage.toFloat()).toInt())
        for (p in 0 until pageCount) {
            val page = HudAppPage(context, pal, cols, rows, cellHeight, p, pageCount, apps.size)
            val end = minOf(apps.size, (p + 1) * perPage)
            for (k in p * perPage until end) {
                val item = apps[k]
                val icon = obtainIcon(page)
                icon.reset()
                icon.applyFromApplicationInfo(item.itemInfo)
                icon.setTextColor(pal.text)
                val privateSpaceTile =
                    item.viewType == BaseAllAppsAdapter.VIEW_TYPE_PRIVATE_SPACE_APP_ICON
                icon.configureMinimalPopup(privateSpaceTile)
                if (privateSpaceTile) {
                    icon.setOnClickListener { PopupContainerWithArrow.showForPrivateSpaceApp(icon) }
                    icon.setOnLongClickListener {
                        PopupContainerWithArrow.showForPrivateSpaceApp(icon); true
                    }
                } else {
                    icon.setOnClickListener(act.itemOnClickListener)
                    icon.setOnLongClickListener(act.allAppsItemLongClickListener)
                }
                page.addView(icon)
            }
            pager.addView(page)
            store.registerIconContainer(page)
        }
        pager.setCurrentPage(keepPage.coerceIn(0, pageCount - 1))
        iconPool.clear()
    }

    private fun obtainIcon(parent: ViewGroup): BubbleTextView {
        if (iconPool.isNotEmpty()) return iconPool.removeAt(iconPool.size - 1)
        return (LayoutInflater.from(context)
            .inflate(R.layout.all_apps_icon, parent, false) as BubbleTextView).apply {
            setLongPressTimeoutFactor(1f)
        }
    }

    companion object {
        private const val INDICATOR_HEIGHT_DP = 28f
        private const val MAX_ROWS = 7
    }
}

/** Horizontal pager with snapping and fling, from Launcher3's [PagedView]. */
class HudAppsPagedView(context: Context) : PagedView<HudPageIndicator>(context) {
    init {
        clipToPadding = false
        isFocusable = false
        descendantFocusability = FOCUS_AFTER_DESCENDANTS
    }

    fun setPageIndicator(indicator: HudPageIndicator) {
        mPageIndicator = indicator
        indicator.setMarkersCount(childCount)
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        mPageIndicator?.setScroll(l, computeMaxScroll())
    }
}

/**
 * One drawer page: a HUD panel with a header and a [cols]×[rows] icon grid. Icons are direct
 * children so [AllAppsStore] can refresh their icons and notification dots.
 */
class HudAppPage(
    context: Context,
    private val pal: HudPalette,
    private val cols: Int,
    private val rows: Int,
    private val cellHeight: Int,
    private val index: Int,
    private val count: Int,
    private val total: Int,
) : ViewGroup(context) {
    private val hud = HudDraw(context)
    private val meta = "P%02d/%02d · %03d".format(index + 1, count, total)

    init {
        setWillNotDraw(false)
        clipChildren = false
        clipToPadding = false
        contentDescription = "Apps page ${index + 1} of $count"
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
        val cellW = max(1, (w - 2 * sidePad()) / cols)
        val cw = MeasureSpec.makeMeasureSpec(cellW, MeasureSpec.EXACTLY)
        val ch = MeasureSpec.makeMeasureSpec(cellHeight, MeasureSpec.EXACTLY)
        for (i in 0 until childCount) getChildAt(i).measure(cw, ch)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val w = r - l
        val h = b - t
        val left = sidePad()
        val top = chromeTop(context)
        val gridH = h - chromeHeight(context)
        val cellW = (w - 2 * left) / cols
        // Spread the rows over the page; leftover height becomes even gaps between them.
        val gap = max(0, (gridH - rows * cellHeight) / rows)
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        for (i in 0 until childCount) {
            val col = i % cols
            val row = i / cols
            val x = if (rtl) w - left - (col + 1) * cellW else left + col * cellW
            val y = top + gap / 2 + row * (cellHeight + gap)
            getChildAt(i).layout(x, y, x + cellW, y + cellHeight)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val inset = context.dp(PANEL_INSET_DP)
        val r = width - inset
        hud.panel(canvas, pal, inset, inset, r, height - inset)
        val pad = context.dp(12f)
        hud.header(canvas, pal, "03", "APPS", "アプリ", meta, inset + pad,
            inset + context.dp(10f), r - pad)
    }

    private fun sidePad() = context.dp(PANEL_INSET_DP + 4f).toInt()

    companion object {
        private const val PANEL_INSET_DP = 2f

        /** Height of the panel header plus bottom padding, above and below the icon grid. */
        fun chromeHeight(ctx: Context): Int = chromeTop(ctx) + ctx.dp(10f).toInt()

        /** Y where the grid starts: panel inset + header block. */
        fun chromeTop(ctx: Context): Int = ctx.dp(PANEL_INSET_DP + 10f + 22f + 5f + 10f).toInt()
    }
}

/**
 * Segmented HUD page indicator: one slanted segment per page, the current one lit and
 * glowing, with a cursor that slides with the scroll and a `01/05` readout.
 */
class HudPageIndicator(context: Context, private val pal: HudPalette) : View(context),
    PageIndicator {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private var count = 0
    private var active = 0
    private var progress = 0f

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun setScroll(currentScroll: Int, totalScroll: Int) {
        val next = if (totalScroll > 0) (currentScroll.toFloat() / totalScroll) else 0f
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        val value = (if (rtl) 1f - next else next).coerceIn(0f, 1f)
        if (value != progress) { progress = value; invalidate() }
    }

    override fun setActiveMarker(activePage: Int) {
        if (activePage != active) { active = activePage; invalidate() }
    }

    override fun setMarkersCount(numMarkers: Int) {
        if (numMarkers != count) {
            count = numMarkers
            visibility = if (count > 1) VISIBLE else INVISIBLE
            requestLayout(); invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (count <= 1) return
        val segW = context.dp(18f)
        val segH = context.dp(4f)
        val gap = context.dp(6f)
        val slant = context.dp(3f)
        val total = count * segW + (count - 1) * gap
        val startX = (width - total) / 2f
        val cy = height / 2f

        for (i in 0 until count) {
            val x = startX + i * (segW + gap)
            segment(x, cy, segW, segH, slant)
            p.style = Paint.Style.FILL
            p.color = if (i == active) withAlpha(pal.a1, .35f) else withAlpha(pal.a1, .08f)
            canvas.drawPath(path, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = context.dp(1f)
            p.color = if (i == active) pal.a1 else pal.line2
            canvas.drawPath(path, p)
        }

        // Cursor glides between segments with the scroll position.
        val cx = startX + progress * (count - 1) * (segW + gap)
        segment(cx, cy, segW, segH, slant)
        p.style = Paint.Style.FILL
        p.color = pal.a1
        p.setShadowLayer(context.dp(8f), 0f, 0f, pal.a1d)
        canvas.drawPath(path, p)
        p.clearShadowLayer()

        text.typeface = HudTheme.mono(context)
        text.textSize = context.sp(11f)
        text.color = pal.dim
        val label = "%02d/%02d".format(active + 1, count)
        canvas.drawText(label, startX + total + context.dp(12f),
            cy - (text.ascent() + text.descent()) / 2, text)
        text.color = pal.a2
        val tag = "頁"
        canvas.drawText(tag, startX - context.dp(12f) - text.measureText(tag),
            cy - (text.ascent() + text.descent()) / 2, text)
    }

    private fun segment(x: Float, cy: Float, w: Float, h: Float, slant: Float) {
        path.reset()
        path.moveTo(x + slant, cy - h / 2)
        path.lineTo(x + w, cy - h / 2)
        path.lineTo(x + w - slant, cy + h / 2)
        path.lineTo(x, cy + h / 2)
        path.close()
    }
}

/** HUD treatment for the all-apps search field: a chamfered chip in Rajdhani. */
object HudDrawerStyle {
    @JvmStatic
    fun styleSearchBar(search: View) {
        val ctx = search.context
        val pal = HudTheme.current(ctx)
        search.background = SearchChip(ctx, pal)
        if (search is TextView) {
            search.typeface = HudTheme.uiBold(ctx)
            search.letterSpacing = .08f
            search.setTextColor(pal.text)
            search.setHintTextColor(pal.dim)
        }
    }

    /** Chamfered chip with an accent edge that brightens while the field has focus. */
    private class SearchChip(private val ctx: Context, private val pal: HudPalette) : Drawable() {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val path = Path()
        private val hud = HudDraw(ctx)
        private var focused = false

        override fun draw(c: Canvas) {
            val b = bounds
            val cut = ctx.dp(10f)
            hud.chamfer(path, b.left + 1f, b.top + 1f, b.right - 1f, b.bottom - 1f, cut)
            p.style = Paint.Style.FILL
            p.color = withAlpha(pal.surface, .92f)
            c.drawPath(path, p)
            p.color = withAlpha(pal.a1, if (focused) .10f else .04f)
            c.drawPath(path, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = ctx.dp(1f)
            p.color = if (focused) pal.a1 else pal.line2
            if (focused) p.setShadowLayer(ctx.dp(6f), 0f, 0f, pal.a1d)
            c.drawPath(path, p)
            p.clearShadowLayer()
            // Accent tick on the chamfered corner, like the panels' hatched corner.
            p.strokeWidth = ctx.dp(2f)
            p.color = pal.a1
            c.drawLine(b.right - cut - 1f, b.top + 2f, b.right - 2f, b.top + cut + 1f, p)
        }

        override fun isStateful() = true

        override fun onStateChange(state: IntArray): Boolean {
            val f = state.contains(android.R.attr.state_focused)
            if (f == focused) return false
            focused = f
            invalidateSelf()
            return true
        }

        override fun setAlpha(alpha: Int) { p.alpha = alpha }
        override fun setColorFilter(cf: android.graphics.ColorFilter?) { p.colorFilter = cf }
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
    }
}

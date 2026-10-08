package com.tablehockey.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.tablehockey.game.game.MusicManager
import com.tablehockey.game.model.CrestFrame
import com.tablehockey.game.model.CrestType
import com.tablehockey.game.model.JerseyPattern
import com.tablehockey.game.model.TeamInfo
import com.tablehockey.game.model.TeamStyle
import com.tablehockey.game.model.TeamStyleStore
import com.tablehockey.game.ui.ChipView
import com.tablehockey.game.ui.FlowLayout
import com.tablehockey.game.ui.TeamArt
import com.tablehockey.game.ui.TeamPreviewView

/**
 * Customise Team: choose any club, edit uniform colours / pattern / helmet / socks and crest.
 *
 * Everything is a working copy until CONFIRM: per-club drafts (switching clubs keeps them), the
 * "stock kit" button, and the "my club" choice. CONFIRM commits all drafts to [TeamStyleStore];
 * CANCEL / Back discards them. The preview caption says whether anything is unsaved.
 */
class CustomiseTeamActivity : AppCompatActivity() {

    private var index = 0
    private lateinit var base: TeamInfo
    private lateinit var style: TeamStyle
    private val drafts = HashMap<Int, TeamStyle>()   // club index -> edited style (unsaved)
    private var pendingFavourite = 0
    private var tab = 0               // 0 = uniform, 1 = logo
    private var uniformTarget = 0     // 0 jersey, 1 stripe, 2 trim, 3 helmet, 4 socks
    private var logoColorTarget = 0   // 0 foreground, 1 background, 2 outline

    private lateinit var preview: TeamPreviewView
    private lateinit var panel: LinearLayout
    private lateinit var spinner: Spinner
    private lateinit var tabUniform: TextView
    private lateinit var tabLogo: TextView
    private lateinit var btnFav: TextView
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dp get() = resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TeamStyleStore.ensureLoaded(this)
        setContentView(R.layout.activity_customise_team)

        preview = findViewById(R.id.preview)
        panel = findViewById(R.id.panel)
        spinner = findViewById(R.id.spinnerClub)
        tabUniform = findViewById(R.id.tabUniform)
        tabLogo = findViewById(R.id.tabLogo)
        btnFav = findViewById(R.id.btnFav)
        pendingFavourite = TeamStyleStore.favourite(this)

        val adapter = ArrayAdapter(this, R.layout.item_spinner, TeamInfo.ALL.map { TeamInfo.label(it) }).apply {
            setDropDownViewResource(R.layout.item_spinner_dropdown)
        }
        spinner.adapter = adapter
        index = intent.getIntExtra(EXTRA_CLUB, pendingFavourite).coerceIn(0, TeamInfo.ALL.size - 1)
        spinner.setSelection(index)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position != index) loadClub(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        findViewById<View>(R.id.btnPrev).setOnClickListener { step(-1) }
        findViewById<View>(R.id.btnNext).setOnClickListener { step(1) }
        tabUniform.setOnClickListener { tab = 0; refresh() }
        tabLogo.setOnClickListener { tab = 1; refresh() }
        findViewById<View>(R.id.btnCancel).setOnClickListener { finish() }
        findViewById<View>(R.id.btnConfirm).setOnClickListener { confirm() }
        // Stock kit: puts the club's original look into the preview. It is a draft like any
        // other edit and only takes effect when CONFIRM is pressed.
        findViewById<View>(R.id.btnReset).setOnClickListener {
            style = TeamStyle.defaultFor(base)
            refresh()
        }
        btnFav.setOnClickListener {
            pendingFavourite = index
            refresh()
        }
        loadClub(index)
    }

    override fun onStart() {
        super.onStart()
        MusicManager.menuStarted(this)
    }

    override fun onStop() {
        super.onStop()
        MusicManager.menuStopped()
    }

    private fun confirm() {
        MusicManager.click(this)
        stashDraft()
        for ((i, st) in drafts) {
            val club = TeamInfo.ALL[i]
            if (st == TeamStyle.defaultFor(club)) TeamStyleStore.reset(this, club) else TeamStyleStore.save(this, club, st)
        }
        TeamStyleStore.setFavourite(this, pendingFavourite)
        finish()
    }

    private fun step(delta: Int) {
        val n = TeamInfo.ALL.size
        spinner.setSelection((index + delta + n) % n)
    }

    /** Keeps the current edits for this club so switching away and back does not lose them. */
    private fun stashDraft() {
        if (!::base.isInitialized) return
        if (style == TeamStyleStore.styleFor(base)) drafts.remove(index) else drafts[index] = style
    }

    private fun loadClub(i: Int) {
        stashDraft()
        index = i
        base = TeamInfo.ALL[i]
        style = drafts[i] ?: TeamStyleStore.styleFor(base)
        refresh()
    }

    private fun edit(s: TeamStyle) {
        style = s
        refresh()
    }

    private fun refresh() {
        preview.team = base
        preview.style = style
        val unsaved = style != TeamStyleStore.styleFor(base) || drafts.keys.any { it != index } ||
            pendingFavourite != TeamStyleStore.favourite(this)
        preview.caption = when {
            unsaved -> "UNSAVED CHANGES - PRESS CONFIRM"
            TeamStyleStore.isCustom(base) -> "CUSTOM KIT"
            else -> "STOCK KIT"
        }
        tabUniform.setBackgroundResource(if (tab == 0) R.drawable.tab_on else R.drawable.tab_off)
        tabLogo.setBackgroundResource(if (tab == 1) R.drawable.tab_on else R.drawable.tab_off)
        tabUniform.setTextColor(if (tab == 0) 0xFF0B1622.toInt() else Color.WHITE)
        tabLogo.setTextColor(if (tab == 1) 0xFF0B1622.toInt() else Color.WHITE)
        btnFav.text = getString(if (pendingFavourite == index) R.string.customise_fav_on else R.string.customise_fav)
        panel.removeAllViews()
        if (tab == 0) buildUniformTab() else buildLogoTab()
    }

    // ------------------------------------------------------------------ tabs

    private fun buildUniformTab() {
        // One shared swatch grid for five colour targets (like the reference's colour boxes),
        // so the whole tab fits without scrolling.
        val names = arrayOf("Jersey", "Stripe", "Trim", "Helmet", "Socks")
        val colors = intArrayOf(style.primary, style.secondary, style.trim, style.helmet, style.sock)
        addTargetRow(names, colors, uniformTarget) { uniformTarget = it; refresh() }
        addSwatches(colors[uniformTarget]) {
            edit(when (uniformTarget) {
                0 -> style.copy(primary = it)
                1 -> style.copy(secondary = it)
                2 -> style.copy(trim = it)
                3 -> style.copy(helmet = it)
                else -> style.copy(sock = it)
            })
        }
        addLabel("Pattern")
        val flow = newFlow()
        val patterns = JerseyPattern.values()
        for (p in patterns) {
            val chip = ChipView(this, 56) { c, cx, cy, r -> TeamArt.drawPatternTile(c, cx, cy, r * 1.05f, style, p) }
            chip.chosen = style.pattern == p
            chip.contentDescription = p.label
            chip.setOnClickListener { edit(style.copy(pattern = p)) }
            flow.addView(chip, chipParams())
        }
    }

    private fun buildLogoTab() {
        addLabel("Foreground")
        val emblems = newFlow()
        val emblemStyles = CrestType.values().map { style.copy(crest = it) }
        for ((k, t) in CrestType.values().withIndex()) {
            val s = emblemStyles[k]
            val chip = ChipView(this, 46) { c, cx, cy, r -> TeamArt.drawCrest(c, cx, cy, r * 1.25f, s, base.abbr) }
            chip.chosen = style.crest == t
            chip.contentDescription = t.label
            chip.setOnClickListener { edit(style.copy(crest = t)) }
            emblems.addView(chip, chipParams())
        }
        addLabel("Background")
        val frames = newFlow()
        val frameStyles = CrestFrame.values().map { style.copy(frame = it) }
        for ((k, f) in CrestFrame.values().withIndex()) {
            val s = frameStyles[k]
            val chip = ChipView(this, 46) { c, cx, cy, r -> TeamArt.drawCrest(c, cx, cy, r * 1.25f, s, base.abbr) }
            chip.chosen = style.frame == f
            chip.contentDescription = f.label
            chip.setOnClickListener { edit(style.copy(frame = f)) }
            frames.addView(chip, chipParams())
        }
        // Foreground / Background / Outline colour targets, as in the reference.
        val colors = intArrayOf(style.crestFg, style.crestBg, style.crestOutline)
        addTargetRow(arrayOf("Foreground", "Background", "Outline"), colors, logoColorTarget) { logoColorTarget = it; refresh() }
        addSwatches(colors[logoColorTarget]) {
            edit(when (logoColorTarget) {
                0 -> style.copy(crestFg = it)
                1 -> style.copy(crestBg = it)
                else -> style.copy(crestOutline = it)
            })
        }
    }

    // ------------------------------------------------------------------ helpers

    private fun addTargetRow(names: Array<String>, colors: IntArray, selected: Int, onPick: (Int) -> Unit) {
        val flow = newFlow()
        for (k in names.indices) {
            val chip = ChipView(this, 52, names[k]) { c, cx, cy, r -> fillDot(c, cx, cy, r * 1.2f, colors[k]) }
            chip.chosen = selected == k
            chip.contentDescription = names[k]
            chip.setOnClickListener { onPick(k) }
            flow.addView(chip, chipParams())
        }
    }

    private fun addLabel(s: String) = panel.addView(smallLabel(s))

    private fun smallLabel(s: String) = TextView(this).apply {
        text = s.uppercase()
        setTextColor(0xFF33475B.toInt())
        textSize = 12f
        letterSpacing = 0.08f
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = (6 * dp).toInt()
            bottomMargin = (2 * dp).toInt()
        }
    }

    private fun newFlow(): FlowLayout {
        val f = FlowLayout(this)
        panel.addView(f, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return f
    }

    private fun chipParams() = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        val m = (2.5f * dp).toInt()
        setMargins(m, m, m, m)
    }

    private fun fillDot(c: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        dot.style = Paint.Style.FILL
        dot.color = color
        c.drawCircle(cx, cy, r, dot)
        dot.style = Paint.Style.STROKE
        dot.strokeWidth = 1.5f * dp
        dot.color = 0x44000000
        c.drawCircle(cx, cy, r, dot)
    }

    private fun addSwatches(current: Int, onPick: (Int) -> Unit) {
        val flow = newFlow()
        val list = (listOf(base.primary, base.secondary) + PALETTE).distinct()
        for (col in list) {
            val chip = ChipView(this, 38) { c, cx, cy, r -> fillDot(c, cx, cy, r * 1.25f, col) }
            chip.chosen = col == current
            chip.setOnClickListener { onPick(col) }
            flow.addView(chip, chipParams())
        }
    }

    companion object {
        const val EXTRA_CLUB = "club_index"

        private val PALETTE: List<Int> = listOf(
            "#F8FAFC", "#111111", "#374151", "#9CA3AF", "#C8102E", "#7F1D1D", "#EA580C", "#F5D130",
            "#0B6E3A", "#14B8A6", "#7DD3FC", "#1D4ED8", "#1C2E5A", "#4B2E83", "#F472B6", "#7C4A21"
        ).map { Color.parseColor(it) }
    }
}

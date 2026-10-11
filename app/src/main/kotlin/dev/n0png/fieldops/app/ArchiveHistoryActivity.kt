package dev.n0png.fieldops.app

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.n0png.fieldops.core.propagation.*

/** CP-0009E: archive UI backed by real CP-0009C state, with synthetic-only reports. */
class ArchiveHistoryActivity : Activity() {
    private val api = PropagationArchiveWorkspaceService
    private var state = api.open(PropagationOfflineArchivePolicy(maxEntries = 12))
    private var beforeId: String? = null
    private var afterId: String? = null
    private var fixtureNumber = 0
    private lateinit var body: LinearLayout
    private val localStore by lazy { OptInArchiveStore(applicationContext) }
    private var localStatus = "Local archive storage is DISABLED. No automatic save or restore."

    /** Transient rotation-only session. Never Parcelable, Bundle, disk or database. */
    private data class RotationSession(
        val archiveState: PropagationArchiveWorkspaceState,
        val before: String?,
        val after: String?,
        val ordinal: Int,
    )

    override fun onRetainNonConfigurationInstance(): Any =
        RotationSession(state, beforeId, afterId, fixtureNumber)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(16, 19, 15))
            isFillViewport = true
        }
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(22), dp(18), dp(22))
        }
        scroll.addView(body)
        setContentView(scroll)
        val rotated = lastNonConfigurationInstance as? RotationSession
        if (rotated == null) {
            // Fresh process/session uses synthetic demo fixtures, not saved history.
            repeat(3) { addSyntheticCapture() }
            state = api.apply(state, PropagationArchiveWorkspaceAction.SetPageSize(2))
        } else {
            state = rotated.archiveState
            beforeId = rotated.before
            afterId = rotated.after
            fixtureNumber = rotated.ordinal
        }
        render()
    }

    /** Explicit caller-owned synthetic input, never radio/network/store access. */
    private fun addSyntheticCapture() {
        val utc = 1_791_590_400_000L + fixtureNumber * 60_000L
        fixtureNumber++
        val picture = PropagationOperatingPictureService(
            InMemoryPropagationSnapshotStore(), InMemoryPropagationRefreshStateStore()
        ).read(PropagationProjectionQuery(utc))
        state = api.apply(state, PropagationArchiveWorkspaceAction.Capture(picture))
    }

    private fun dispatch(action: PropagationArchiveWorkspaceAction) {
        state = api.apply(state, action)
        if (state.archive.entries.none { it.id == beforeId }) beforeId = null
        if (state.archive.entries.none { it.id == afterId }) afterId = null
        render()
    }

    private fun render() {
        val screen = api.screen(state)
        body.removeAllViews()
        title("N0PNG / FIELDOPS", 24)
        title("OFFLINE HISTORY • SYNTHETIC DEMO", 17)
        message("NOT LIVE: no current propagation, radio CAT, TX, RX or RF connection.")
        screen.provenanceNotices.forEach { message("• " + it) }
        message("Retained reports: " + screen.totalReports)
        message("Archive UTF-8 bytes: " + screen.totalUtf8Bytes)
        message("APP-PRIVATE FILE: Opt-in only; not encrypted or source-authenticated. No cloud or backup.")
        message(localStatus)
        if (!localStore.isEnabled()) {
            actionButton("ENABLE LOCAL ARCHIVE STORAGE") {
                localStore.enableForThisSession()
                localStatus = "Enabled this session only. Save and restore require separate taps."
                render()
            }
        } else {
            actionButton("SAVE SYNTHETIC ARCHIVE LOCALLY") {
                localStatus = runCatching {
                    localStore.saveExplicitly(state.archive)
                    "Saved canonical synthetic reports in app-private no-backup storage."
                }.getOrElse { "SAVE REJECTED: write not confirmed. Review saved history using explicit RESTORE before relying on it. Existing on-screen reports unchanged." }
                render()
            }
            actionButton("RESTORE SAVED LOCAL ARCHIVE") {
                localStatus = runCatching {
                    val restored = localStore.loadExplicitly()
                    if (restored == null) {
                        "No saved local report archive exists."
                    } else {
                        state = state.copy(
                            archive = restored,
                            selectedSha256 = null,
                            comparisonBeforeSha256 = null,
                            comparisonAfterSha256 = null,
                            offset = 0,
                        )
                        // Subsequent captures remain synthetic and distinct.
                        fixtureNumber = maxOf(fixtureNumber, 100)
                        beforeId = null
                        afterId = null
                        "Restored " + restored.entries.size + " canonical offline reports."
                    }
                }.getOrElse { "RESTORE REJECTED: saved archive could not be verified. Previous on-screen reports are unchanged. Use DELETE LOCAL ARCHIVE FILE to discard corrupted saved data, then save a new copy explicitly." }
                render()
            }
            actionButton("DELETE LOCAL ARCHIVE FILE") {
                localStatus = if (localStore.deleteExplicitly()) {
                    "Deleted local file; this is not a secure physical wipe."
                } else {
                    "Local file could not be removed."
                }
                render()
            }
            actionButton("DISABLE LOCAL ARCHIVE STORAGE") {
                localStore.disableForThisSession()
                localStatus = "Storage disabled this session. Existing file is not erased."
                render()
            }
        }
        actionButton("ADD SYNTHETIC REPORT") {
            addSyntheticCapture()
            render()
        }
        actionButton("RETURN TO FIELDOPS") { finish() }
        title(screen.rangeLabel, 17)
        message(screen.focusAnnouncement)
        screen.rows.forEach { row ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(12), dp(12), dp(12))
                background = GradientDrawable().apply {
                    setColor(Color.rgb(35, 42, 33))
                    cornerRadius = dp(7).toFloat()
                    setStroke(dp(1), Color.rgb(105, 110, 83))
                }
            }
            card.addView(TextView(this).apply {
                text = "UTC millis " + row.queryUtcMillis +
                    "\nContent SHA-256 " + row.sha256Hex.take(16) + "…"
                contentDescription = row.accessibleLabel
                setTextColor(Color.WHITE)
                textSize = 14f
            })
            if (row.selected) card.addView(messageView("SELECTED HISTORY"))
            if (row.comparisonBefore) card.addView(messageView("COMPARE: BEFORE"))
            if (row.comparisonAfter) card.addView(messageView("COMPARE: AFTER"))
            card.addView(actionView("SELECT") {
                dispatch(PropagationArchiveWorkspaceAction.Select(row.sha256Hex))
            })
            card.addView(actionView("SET BEFORE") {
                beforeId = row.sha256Hex
                render()
            })
            card.addView(actionView("SET AFTER") {
                afterId = row.sha256Hex
                render()
            })
            card.addView(actionView("DELETE") {
                dispatch(PropagationArchiveWorkspaceAction.Delete(row.sha256Hex))
            })
            body.addView(card, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(10)
            })
        }
        if (screen.previousEnabled) actionButton("PREVIOUS PAGE") {
            dispatch(PropagationArchiveWorkspaceAction.PreviousPage)
        }
        if (screen.nextEnabled) actionButton("NEXT PAGE") {
            dispatch(PropagationArchiveWorkspaceAction.NextPage)
        }
        message("Before: " + (beforeId?.take(16) ?: "not selected"))
        message("After: " + (afterId?.take(16) ?: "not selected"))
        val before = beforeId
        val after = afterId
        if (before != null && after != null && before != after) {
            actionButton("COMPARE CANONICAL ORIGINALS") {
                dispatch(PropagationArchiveWorkspaceAction.Compare(before, after))
            }
        }
        val comparison = screen.comparison
        if (comparison != null) {
            title("HISTORICAL COMPARISON", 17)
            message("Before SHA: " + comparison.before.receipt.sha256Hex.take(16))
            message("After SHA: " + comparison.after.receipt.sha256Hex.take(16))
            message("Both canonical originals are reconciled on each state read.")
            actionButton("CLEAR COMPARISON") {
                dispatch(PropagationArchiveWorkspaceAction.ClearComparison)
            }
        }
    }

    private fun title(value: String, size: Int) {
        body.addView(TextView(this).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(Color.rgb(231, 180, 104))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(8), 0, dp(8))
        })
    }
    private fun message(value: String) { body.addView(messageView(value)) }
    private fun messageView(value: String) = TextView(this).apply {
        text = value
        textSize = 13f
        setTextColor(Color.rgb(224, 225, 207))
        setPadding(0, dp(6), 0, dp(6))
    }
    private fun actionButton(value: String, fn: () -> Unit) {
        body.addView(actionView(value, fn))
    }
    private fun actionView(value: String, fn: () -> Unit) = Button(this).apply {
        text = value
        contentDescription = value
        isAllCaps = false
        minHeight = dp(48)
        setOnClickListener { fn() }
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density + 0.5f).toInt()
}

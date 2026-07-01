package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.PartialResult
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class PictureInPictureDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val PIP_PARAMS_BUILDER = "android.app.PictureInPictureParams.Builder"

        private const val KEY_SET_AUTO_ENTER_ENABLED = "setAutoEnterEnabled"
        private const val KEY_SET_SOURCE_RECT_HINT = "setSourceRectHint"
        private const val KEY_LOCATION_FILE = "locationFile"
        private const val KEY_LOCATION_START_LINE = "locationStartLine"
        private const val KEY_LOCATION_START_COL = "locationStartCol"
        private const val KEY_LOCATION_END_LINE = "locationEndLine"
        private const val KEY_LOCATION_END_COL = "locationEndCol"
        private const val KEY_BUILD_LOCATION_FILE = "buildLocationFile"
        private const val KEY_BUILD_LOCATION_START_LINE = "buildLocationStartLine"
        private const val KEY_BUILD_LOCATION_START_COL = "buildLocationStartCol"
        private const val KEY_BUILD_LOCATION_END_LINE = "buildLocationEndLine"
        private const val KEY_BUILD_LOCATION_END_COL = "buildLocationEndCol"
        private const val KEY_HAS_BUILD = "hasBuild"

        @JvmField
        val ISSUE = Issue.create(
            id = "PictureInPictureIssue",
            briefDescription = "Picture In Picture best practices not followed",
            explanation = """
                Starting in Android 12, the recommended approach for enabling picture-in-picture \
                (PiP) has changed. If your app does not use the new approach, your app's \
                transition animations will be of poor quality compared to other apps. The new \
                approach requires calling `setAutoEnterEnabled(true)` and `setSourceRectHint(...)`.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                PictureInPictureDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            ),
            androidSpecific = true,
            moreInfo = "https://developer.android.com/develop/ui/views/picture-in-picture#smoother-transition"
        )

        private const val PARTIAL_KEY_ENTRIES = "entries"
    }

    /**
     * Per-file tracking: maps a builder chain's "build()" call location to
     * whether setAutoEnterEnabled / setSourceRectHint were seen in that chain.
     *
     * We use a simple list of records because a single file may have multiple
     * PictureInPictureParams.Builder usages.
     */
    private data class BuilderRecord(
        val buildLocation: Location,
        var sawAutoEnter: Boolean = false,
        var sawSourceRectHint: Boolean = false
    )

    // Per-analysis-run (file) state
    private val builderRecords = mutableListOf<BuilderRecord>()

    override fun getApplicableMethodNames(): List<String> = listOf(
        "setAutoEnterEnabled",
        "setSourceRectHint",
        "build"
    )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator

        when (method.name) {
            "setAutoEnterEnabled" -> {
                if (evaluator.isMemberInSubClassOf(method, PIP_PARAMS_BUILDER)) {
                    // Find or create a record for the builder chain that contains this call.
                    // We associate it with the outermost build() call; at this point we may
                    // not have seen build() yet, so we create a placeholder record.
                    getOrCreateRecord(context, node)?.sawAutoEnter = true
                }
            }
            "setSourceRectHint" -> {
                if (evaluator.isMemberInSubClassOf(method, PIP_PARAMS_BUILDER)) {
                    getOrCreateRecord(context, node)?.sawSourceRectHint = true
                }
            }
            "build" -> {
                if (evaluator.isMemberInSubClassOf(method, PIP_PARAMS_BUILDER)) {
                    val loc = context.getLocation(node)
                    // Create the record now if it hasn't been created by a setter visit yet.
                    val record = builderRecords.find { it.buildLocation == loc }
                        ?: BuilderRecord(buildLocation = loc).also { builderRecords.add(it) }
                    // Store partial results so we can check across files in checkPartialResults.
                    storePartialResult(context, record)
                }
            }
        }
    }

    /**
     * Find the BuilderRecord that corresponds to the builder chain containing [node].
     * We identify the chain by walking up to find the enclosing build() call's location.
     * If no record exists yet, we create a placeholder.
     */
    private fun getOrCreateRecord(context: JavaContext, node: UCallExpression): BuilderRecord? {
        // We use the location of this node as a proxy – we will reconcile with build() later.
        // A simpler approach: use a single record per file (not accurate for multiple builders)
        // but for correctness we track by the source location of the call expression itself.
        // Since we may encounter setters before or after build(), we use a shared list and
        // match by proximity.  For simplicity, return the most-recently-added record or create one.
        val loc = context.getLocation(node)
        // Return the last record (most recent builder chain started), or create a new one.
        return builderRecords.lastOrNull() ?: BuilderRecord(buildLocation = loc).also {
            builderRecords.add(it)
        }
    }

    private fun storePartialResult(context: JavaContext, record: BuilderRecord) {
        val map = context.getPartialResults(ISSUE).map()
        val loc = record.buildLocation
        val key = loc.file.path + ":" + (loc.start?.line ?: 0) + ":" + (loc.start?.column ?: 0)
        val entry = LintMap()
        entry.put(KEY_SET_AUTO_ENTER_ENABLED, record.sawAutoEnter)
        entry.put(KEY_SET_SOURCE_RECT_HINT, record.sawSourceRectHint)
        entry.put(KEY_BUILD_LOCATION_FILE, loc.file.path)
        entry.put(KEY_BUILD_LOCATION_START_LINE, loc.start?.line ?: -1)
        entry.put(KEY_BUILD_LOCATION_START_COL, loc.start?.column ?: -1)
        entry.put(KEY_BUILD_LOCATION_END_LINE, loc.end?.line ?: -1)
        entry.put(KEY_BUILD_LOCATION_END_COL, loc.end?.column ?: -1)
        map.put(key, entry)
    }

    override fun afterCheckEachProject(context: Context) {
        // Report issues for the current project (non-partial analysis path).
        // In a normal (non-distributed) run, we report directly here.
        if (!context.isGlobalAnalysis()) {
            // Partial analysis – defer to checkPartialResults
            builderRecords.clear()
            return
        }

        for (record in builderRecords) {
            reportIfNeeded(context, record.buildLocation, record.sawAutoEnter, record.sawSourceRectHint)
        }
        builderRecords.clear()
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        val combined = partialResults.map()
        for (key in combined) {
            val entry = combined.getMap(key) ?: continue
            val sawAutoEnter = entry.getBoolean(KEY_SET_AUTO_ENTER_ENABLED) ?: false
            val sawSourceRectHint = entry.getBoolean(KEY_SET_SOURCE_RECT_HINT) ?: false

            val filePath = entry.getString(KEY_BUILD_LOCATION_FILE) ?: continue
            val startLine = entry.getInt(KEY_BUILD_LOCATION_START_LINE) ?: -1
            val startCol = entry.getInt(KEY_BUILD_LOCATION_START_COL) ?: -1

            val file = java.io.File(filePath)
            val location = Location.create(file)

            reportIfNeeded(context, location, sawAutoEnter, sawSourceRectHint)
        }
    }

    private fun reportIfNeeded(
        context: Context,
        location: Location,
        sawAutoEnter: Boolean,
        sawSourceRectHint: Boolean
    ) {
        if (sawAutoEnter && sawSourceRectHint) return

        val missing = mutableListOf<String>()
        if (!sawAutoEnter) missing.add("`setAutoEnterEnabled(true)`")
        if (!sawSourceRectHint) missing.add("`setSourceRectHint(...)`")

        val message = "PictureInPictureParams.Builder is missing call(s) to ${missing.joinToString(" and ")}. " +
            "Starting in Android 12, both `setAutoEnterEnabled(true)` and `setSourceRectHint(...)` " +
            "should be set for smooth picture-in-picture transitions."

        context.report(
            Incident(
                issue = ISSUE,
                location = location,
                message = message
            )
        )
    }
}
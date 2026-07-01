package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.PartialResult
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class PictureInPictureDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val PIP_PARAMS_BUILDER = "android.app.PictureInPictureParams.Builder"

        private const val KEY_HAS_AUTO_ENTER = "hasAutoEnterEnabled"
        private const val KEY_HAS_SOURCE_RECT_HINT = "hasSourceRectHint"
        private const val KEY_LOCATION = "location"

        @JvmField
        val PICTURE_IN_PICTURE_ISSUE: Issue = Issue.create(
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

        private const val METHOD_SET_AUTO_ENTER_ENABLED = "setAutoEnterEnabled"
        private const val METHOD_SET_SOURCE_RECT_HINT = "setSourceRectHint"
        private const val METHOD_BUILD = "build"
    }

    /**
     * Per-project state tracking which builder call sites have set autoEnterEnabled
     * and/or sourceRectHint. Keyed by the UCallExpression identity of the `build()` call,
     * but since we can't easily correlate builder chains at the AST level without data-flow
     * analysis, we track flags globally per-module and report on `build()` calls only when
     * the flags are missing.
     *
     * We use a simpler heuristic: if the project contains ANY call to
     * setAutoEnterEnabled / setSourceRectHint on a Builder, we assume best practices are
     * followed. If the project has `build()` calls on PictureInPictureParams.Builder but
     * never calls setAutoEnterEnabled or setSourceRectHint, we report.
     */

    // per-file state accumulated into partial results
    private var hasAutoEnterEnabled = false
    private var hasSourceRectHint = false
    private var buildCallLocations = mutableListOf<Pair<UCallExpression, JavaContext>>()

    override fun getApplicableMethodNames(): List<String> = listOf(
        METHOD_SET_AUTO_ENTER_ENABLED,
        METHOD_SET_SOURCE_RECT_HINT,
        METHOD_BUILD
    )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        when (method.name) {
            METHOD_SET_AUTO_ENTER_ENABLED -> {
                if (context.evaluator.isMemberInSubClassOf(method, PIP_PARAMS_BUILDER)) {
                    hasAutoEnterEnabled = true
                }
            }
            METHOD_SET_SOURCE_RECT_HINT -> {
                if (context.evaluator.isMemberInSubClassOf(method, PIP_PARAMS_BUILDER)) {
                    hasSourceRectHint = true
                }
            }
            METHOD_BUILD -> {
                if (context.evaluator.isMemberInSubClassOf(method, PIP_PARAMS_BUILDER)) {
                    buildCallLocations.add(Pair(node, context))
                }
            }
        }
    }

    override fun afterCheckEachProject(context: Context) {
        if (context.isGlobalAnalysis()) {
            // In global analysis mode, report directly if conditions are not met
            if (buildCallLocations.isNotEmpty()) {
                val missingAutoEnter = !hasAutoEnterEnabled
                val missingSourceRectHint = !hasSourceRectHint

                if (missingAutoEnter || missingSourceRectHint) {
                    for ((node, javaContext) in buildCallLocations) {
                        val missing = buildMissingMethodsList(missingAutoEnter, missingSourceRectHint)
                        val message = buildMessage(missing)
                        javaContext.report(
                            Incident(
                                PICTURE_IN_PICTURE_ISSUE,
                                node,
                                javaContext.getLocation(node),
                                message
                            )
                        )
                    }
                }
            }
        } else {
            // Partial analysis: store results for checkPartialResults
            val map = context.getPartialResults(PICTURE_IN_PICTURE_ISSUE).map()
            map.put(KEY_HAS_AUTO_ENTER, hasAutoEnterEnabled)
            map.put(KEY_HAS_SOURCE_RECT_HINT, hasSourceRectHint)

            // Store build call locations as encoded strings
            var index = 0
            for ((node, javaContext) in buildCallLocations) {
                val location = javaContext.getLocation(node)
                map.put("${KEY_LOCATION}_$index", location)
                index++
            }
            map.put("buildCallCount", index)
        }

        // Reset per-project state
        hasAutoEnterEnabled = false
        hasSourceRectHint = false
        buildCallLocations = mutableListOf()
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        var anyAutoEnter = false
        var anySourceRectHint = false
        val allLocations = mutableListOf<com.android.tools.lint.detector.api.Location>()

        for (map in partialResults.maps()) {
            if (map.getBoolean(KEY_HAS_AUTO_ENTER) == true) {
                anyAutoEnter = true
            }
            if (map.getBoolean(KEY_HAS_SOURCE_RECT_HINT) == true) {
                anySourceRectHint = true
            }
            val count = map.getInt("buildCallCount") ?: 0
            for (i in 0 until count) {
                val location = map.getLocation("${KEY_LOCATION}_$i")
                if (location != null) {
                    allLocations.add(location)
                }
            }
        }

        if (allLocations.isNotEmpty()) {
            val missingAutoEnter = !anyAutoEnter
            val missingSourceRectHint = !anySourceRectHint

            if (missingAutoEnter || missingSourceRectHint) {
                val missing = buildMissingMethodsList(missingAutoEnter, missingSourceRectHint)
                val message = buildMessage(missing)
                for (location in allLocations) {
                    context.report(
                        Incident(
                            PICTURE_IN_PICTURE_ISSUE,
                            location,
                            message
                        )
                    )
                }
            }
        }
    }

    private fun buildMissingMethodsList(missingAutoEnter: Boolean, missingSourceRectHint: Boolean): List<String> {
        val missing = mutableListOf<String>()
        if (missingAutoEnter) missing.add("`setAutoEnterEnabled(true)`")
        if (missingSourceRectHint) missing.add("`setSourceRectHint(...)`")
        return missing
    }

    private fun buildMessage(missing: List<String>): String {
        return if (missing.size == 1) {
            "PictureInPictureParams.Builder is missing a call to ${missing[0]}. " +
                "Starting in Android 12, both `setAutoEnterEnabled(true)` and `setSourceRectHint(...)` " +
                "should be set for smooth PiP transition animations."
        } else {
            "PictureInPictureParams.Builder is missing calls to ${missing.joinToString(" and ")}. " +
                "Starting in Android 12, both `setAutoEnterEnabled(true)` and `setSourceRectHint(...)` " +
                "should be set for smooth PiP transition animations."
        }
    }
}
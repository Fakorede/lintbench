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
        private const val KEY_MESSAGE = "message"

        @JvmField
        val ISSUE = Issue.create(
            id = "PictureInPictureIssue",
            briefDescription = "Picture In Picture best practices not followed",
            explanation = """
                Starting in Android 12, the recommended approach for enabling picture-in-picture \
                (PiP) has changed. If your app does not use the new approach, your app's transition \
                animations will be of poor quality compared to other apps. The new approach requires \
                calling `setAutoEnterEnabled(true)` and `setSourceRectHint(...)`.
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
     * Per-project state tracking for each Builder instance (keyed by the call site of build()).
     * We track whether setAutoEnterEnabled and setSourceRectHint were called on each builder chain.
     */
    private data class BuilderState(
        var hasAutoEnterEnabled: Boolean = false,
        var hasSourceRectHint: Boolean = false,
        var buildNode: UCallExpression? = null
    )

    // Maps from a build() call expression identity to its state, per analysis run
    private val builderStates = mutableListOf<BuilderState>()
    private var currentState: BuilderState? = null

    override fun getApplicableMethodNames(): List<String> = listOf(
        METHOD_SET_AUTO_ENTER_ENABLED,
        METHOD_SET_SOURCE_RECT_HINT,
        METHOD_BUILD
    )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator

        when (method.name) {
            METHOD_SET_AUTO_ENTER_ENABLED -> {
                if (evaluator.isMemberInSubClassOf(method, PIP_PARAMS_BUILDER)) {
                    getOrCreateState(node).hasAutoEnterEnabled = true
                }
            }
            METHOD_SET_SOURCE_RECT_HINT -> {
                if (evaluator.isMemberInSubClassOf(method, PIP_PARAMS_BUILDER)) {
                    getOrCreateState(node).hasSourceRectHint = true
                }
            }
            METHOD_BUILD -> {
                if (evaluator.isMemberInSubClassOf(method, PIP_PARAMS_BUILDER)) {
                    val state = getOrCreateState(node)
                    state.buildNode = node

                    if (!state.hasAutoEnterEnabled || !state.hasSourceRectHint) {
                        val missing = mutableListOf<String>()
                        if (!state.hasAutoEnterEnabled) missing.add("`setAutoEnterEnabled(true)`")
                        if (!state.hasSourceRectHint) missing.add("`setSourceRectHint(...)`")
                        val message = "PictureInPictureParams.Builder is missing calls to: " +
                                "${missing.joinToString(" and ")}. " +
                                "These are required for smooth PiP transition animations on Android 12+."

                        val location = context.getLocation(node)

                        if (context.isGlobalAnalysis()) {
                            context.report(
                                Incident(ISSUE, node, location, message)
                            )
                        } else {
                            val lintMap = context.getPartialResults(ISSUE).map()
                            val key = "build_${builderStates.size}_${node.sourcePsi?.textOffset ?: 0}"
                            lintMap.put("${key}_$KEY_MESSAGE", message)
                            lintMap.put("${key}_$KEY_HAS_AUTO_ENTER", state.hasAutoEnterEnabled)
                            lintMap.put("${key}_$KEY_HAS_SOURCE_RECT_HINT", state.hasSourceRectHint)
                            context.report(
                                Incident(ISSUE, node, location, message)
                            )
                        }
                    }

                    // Reset for next builder chain
                    currentState = null
                }
            }
        }
    }

    /**
     * Gets or creates a BuilderState for the current builder chain being analyzed.
     * We use a simple approach: the "current" state accumulates calls until build() is seen.
     */
    private fun getOrCreateState(node: UCallExpression): BuilderState {
        if (currentState == null) {
            currentState = BuilderState()
            builderStates.add(currentState!!)
        }
        return currentState!!
    }

    override fun afterCheckEachProject(context: Context) {
        // Reset per-project state after each project is checked
        builderStates.clear()
        currentState = null
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        // In sharded/partial analysis mode, aggregate results from all shards.
        // Incidents are already reported during visitMethodCall; nothing additional needed here
        // unless we want to do cross-module deduplication.
        partialResults.maps().forEach { lintMap ->
            // All incidents have already been reported via context.report() in visitMethodCall.
            // This hook is here for completeness and potential future cross-module checks.
        }
    }
}
package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
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
        private val IMPLEMENTATION = Implementation(
            PictureInPictureDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

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
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private const val PIP_PARAMS_BUILDER_CLASS = "android.app.PictureInPictureParams.Builder"
        private const val METHOD_SET_AUTO_ENTER_ENABLED = "setAutoEnterEnabled"
        private const val METHOD_SET_SOURCE_RECT_HINT = "setSourceRectHint"
        private const val METHOD_BUILD = "build"

        private const val KEY_HAS_AUTO_ENTER = "hasAutoEnter"
        private const val KEY_HAS_SOURCE_RECT_HINT = "hasSourceRectHint"
        private const val KEY_HAS_BUILD = "hasBuild"
        private const val KEY_BUILD_LOCATION = "buildLocation"
        private const val KEY_BUILD_FILE = "buildFile"
    }

    // Track per-file state during single-file analysis
    private var hasAutoEnterEnabled = false
    private var hasSourceRectHint = false
    private var hasBuildCall = false
    private var buildCallNode: UCallExpression? = null
    private var buildCallContext: JavaContext? = null

    override fun getApplicableMethodNames(): List<String> = listOf(
        METHOD_SET_AUTO_ENTER_ENABLED,
        METHOD_SET_SOURCE_RECT_HINT,
        METHOD_BUILD,
    )

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        val containingClass = method.containingClass ?: return
        val qualifiedName = containingClass.qualifiedName ?: return

        if (qualifiedName != PIP_PARAMS_BUILDER_CLASS) return

        when (method.name) {
            METHOD_SET_AUTO_ENTER_ENABLED -> {
                hasAutoEnterEnabled = true
            }
            METHOD_SET_SOURCE_RECT_HINT -> {
                hasSourceRectHint = true
            }
            METHOD_BUILD -> {
                hasBuildCall = true
                buildCallNode = node
                buildCallContext = context
            }
        }
    }

    override fun afterCheckEachProject(context: Context) {
        if (!hasBuildCall) {
            // Reset state for next project/file
            resetState()
            return
        }

        if (context.isGlobalAnalysis()) {
            // In global analysis mode, report directly
            reportIfNeeded()
        } else {
            // In partial analysis mode, store results for later aggregation
            val map = context.getPartialResults(ISSUE).map()
            map.put(KEY_HAS_AUTO_ENTER, hasAutoEnterEnabled)
            map.put(KEY_HAS_SOURCE_RECT_HINT, hasSourceRectHint)
            map.put(KEY_HAS_BUILD, hasBuildCall)
        }

        resetState()
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        var anyBuildCall = false
        var allHaveAutoEnter = true
        var allHaveSourceRectHint = true

        for (map in partialResults.maps()) {
            val hasBuild = map.getBoolean(KEY_HAS_BUILD) ?: false
            if (hasBuild) {
                anyBuildCall = true
                val autoEnter = map.getBoolean(KEY_HAS_AUTO_ENTER) ?: false
                val sourceRectHint = map.getBoolean(KEY_HAS_SOURCE_RECT_HINT) ?: false
                if (!autoEnter) allHaveAutoEnter = false
                if (!sourceRectHint) allHaveSourceRectHint = false
            }
        }

        if (anyBuildCall) {
            val missingMethods = mutableListOf<String>()
            if (!allHaveAutoEnter) missingMethods.add("`setAutoEnterEnabled(true)`")
            if (!allHaveSourceRectHint) missingMethods.add("`setSourceRectHint(...)`")

            if (missingMethods.isNotEmpty()) {
                val missing = missingMethods.joinToString(" and ")
                context.report(
                    ISSUE,
                    context.getProject().dir,
                    "PictureInPictureParams.Builder is used without calling $missing. " +
                        "Starting in Android 12, both `setAutoEnterEnabled(true)` and " +
                        "`setSourceRectHint(...)` should be called for smoother PiP transitions.",
                )
            }
        }
    }

    private fun reportIfNeeded() {
        val ctx = buildCallContext ?: return
        val node = buildCallNode ?: return

        val missingMethods = mutableListOf<String>()
        if (!hasAutoEnterEnabled) missingMethods.add("`setAutoEnterEnabled(true)`")
        if (!hasSourceRectHint) missingMethods.add("`setSourceRectHint(...)`")

        if (missingMethods.isNotEmpty()) {
            val missing = missingMethods.joinToString(" and ")
            ctx.report(
                ISSUE,
                node,
                ctx.getLocation(node),
                "PictureInPictureParams.Builder is used without calling $missing. " +
                    "Starting in Android 12, both `setAutoEnterEnabled(true)` and " +
                    "`setSourceRectHint(...)` should be called for smoother PiP transitions.",
            )
        }
    }

    private fun resetState() {
        hasAutoEnterEnabled = false
        hasSourceRectHint = false
        hasBuildCall = false
        buildCallNode = null
        buildCallContext = null
    }
}
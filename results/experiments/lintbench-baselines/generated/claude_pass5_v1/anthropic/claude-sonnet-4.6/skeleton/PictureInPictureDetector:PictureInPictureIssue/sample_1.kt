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
        private const val KEY_BUILD_LOCATION = "buildLocation"
        private const val KEY_BUILD_FILE = "buildFile"
        private const val KEY_BUILD_LINE = "buildLine"
        private const val KEY_BUILD_MESSAGE = "buildMessage"
    }

    /**
     * Per-file tracking: whether we've seen setAutoEnterEnabled and setSourceRectHint
     * calls on a PictureInPictureParams.Builder in the current file.
     */
    private var fileHasAutoEnterEnabled = false
    private var fileHasSourceRectHint = false

    // Track the location of a `build()` call so we can report it if needed
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
                fileHasAutoEnterEnabled = true
            }
            METHOD_SET_SOURCE_RECT_HINT -> {
                fileHasSourceRectHint = true
            }
            METHOD_BUILD -> {
                // Record the build() call location so we can report against it
                buildCallNode = node
                buildCallContext = context
            }
        }
    }

    override fun afterCheckEachProject(context: Context) {
        val buildNode = buildCallNode
        val buildContext = buildCallContext

        if (buildNode != null && buildContext != null) {
            // We found a PictureInPictureParams.Builder.build() call in this project/file
            if (!fileHasAutoEnterEnabled || !fileHasSourceRectHint) {
                if (context.isGlobalAnalysis()) {
                    // In global analysis mode, report directly
                    reportIssue(buildContext, buildNode)
                } else {
                    // In partial analysis mode, store findings for later aggregation
                    val map = context.getPartialResults(ISSUE).map()
                    map.put(KEY_HAS_AUTO_ENTER, fileHasAutoEnterEnabled)
                    map.put(KEY_HAS_SOURCE_RECT_HINT, fileHasSourceRectHint)
                    val location = buildContext.getLocation(buildNode)
                    map.put(KEY_BUILD_FILE, location.file.path)
                    map.put(KEY_BUILD_MESSAGE, getMissingMethodsMessage())
                }
            }
        }

        // Reset per-file state
        fileHasAutoEnterEnabled = false
        fileHasSourceRectHint = false
        buildCallNode = null
        buildCallContext = null
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        for (map in partialResults.maps()) {
            val hasAutoEnter = map.getBoolean(KEY_HAS_AUTO_ENTER) ?: true
            val hasSourceRectHint = map.getBoolean(KEY_HAS_SOURCE_RECT_HINT) ?: true

            if (!hasAutoEnter || !hasSourceRectHint) {
                val missing = buildMissingMessage(hasAutoEnter, hasSourceRectHint)
                // Report at project level since we don't have the original location in partial mode
                context.report(
                    ISSUE,
                    context.getLocation(context.project.dir),
                    "PictureInPictureParams.Builder is missing recommended calls: $missing. " +
                        "Starting in Android 12, you should call `setAutoEnterEnabled(true)` " +
                        "and `setSourceRectHint(...)` for smoother PiP transitions.",
                )
            }
        }
    }

    private fun reportIssue(context: JavaContext, node: UCallExpression) {
        val missing = getMissingMethodsMessage()
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "PictureInPictureParams.Builder is missing recommended calls: $missing. " +
                "Starting in Android 12, you should call `setAutoEnterEnabled(true)` " +
                "and `setSourceRectHint(...)` for smoother PiP transitions.",
        )
    }

    private fun getMissingMethodsMessage(): String {
        return buildMissingMessage(fileHasAutoEnterEnabled, fileHasSourceRectHint)
    }

    private fun buildMissingMessage(hasAutoEnter: Boolean, hasSourceRectHint: Boolean): String {
        val missing = mutableListOf<String>()
        if (!hasAutoEnter) missing.add("`setAutoEnterEnabled(true)`")
        if (!hasSourceRectHint) missing.add("`setSourceRectHint(...)`")
        return missing.joinToString(" and ")
    }
}
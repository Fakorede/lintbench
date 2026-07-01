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

        // Keys for storing per-file results in partial analysis
        private const val KEY_FILE_HAS_AUTO_ENTER = "fileHasAutoEnter"
        private const val KEY_FILE_HAS_SOURCE_RECT_HINT = "fileHasSourceRectHint"
        private const val KEY_FILE_HAS_BUILD = "fileHasBuild"
    }

    // Track state within a single file analysis
    private var fileHasAutoEnterEnabled = false
    private var fileHasSourceRectHint = false
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
                buildCallNode = node
                buildCallContext = context
            }
        }
    }

    override fun afterCheckEachProject(context: Context) {
        // In non-partial analysis (single module), report directly if we found a build() call
        // but didn't find the required setup methods.
        val buildNode = buildCallNode
        val buildContext = buildCallContext

        if (buildNode != null && buildContext != null) {
            if (!fileHasAutoEnterEnabled || !fileHasSourceRectHint) {
                val missing = buildMissingMethodsList()
                buildContext.report(
                    ISSUE,
                    buildNode,
                    buildContext.getLocation(buildNode),
                    buildMessage(missing),
                )
            }
        }

        // Reset state for next project/file
        resetState()
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        var hasAutoEnter = false
        var hasSourceRectHint = false
        var buildLocation: com.android.tools.lint.detector.api.Location? = null
        var buildMessage: String? = null

        for ((_, map) in partialResults.maps()) {
            if (map.getBoolean(KEY_FILE_HAS_AUTO_ENTER) == true) {
                hasAutoEnter = true
            }
            if (map.getBoolean(KEY_FILE_HAS_SOURCE_RECT_HINT) == true) {
                hasSourceRectHint = true
            }
        }

        // Find any build() call location from the partial results
        for ((_, map) in partialResults.maps()) {
            if (map.getBoolean(KEY_FILE_HAS_BUILD) == true) {
                buildMessage = map.getString(KEY_BUILD_MESSAGE, null)
                // We can't reconstruct a full Location from partial results easily,
                // so we'll use the context location
                break
            }
        }

        if (buildMessage != null && (!hasAutoEnter || !hasSourceRectHint)) {
            val missing = mutableListOf<String>()
            if (!hasAutoEnter) missing.add("`setAutoEnterEnabled(true)`")
            if (!hasSourceRectHint) missing.add("`setSourceRectHint(...)`")
            context.report(
                ISSUE,
                context.getLocation(context.project.dir),
                buildMessage(missing),
            )
        }
    }

    private fun buildMissingMethodsList(): List<String> {
        val missing = mutableListOf<String>()
        if (!fileHasAutoEnterEnabled) missing.add("`setAutoEnterEnabled(true)`")
        if (!fileHasSourceRectHint) missing.add("`setSourceRectHint(...)`")
        return missing
    }

    private fun buildMessage(missingMethods: List<String>): String {
        return if (missingMethods.isEmpty()) {
            "PictureInPictureParams.Builder is missing recommended setup for smooth transitions."
        } else {
            "PictureInPictureParams.Builder is missing ${missingMethods.joinToString(" and ")} " +
                "for smooth transitions in Android 12+. See " +
                "https://developer.android.com/develop/ui/views/picture-in-picture#smoother-transition"
        }
    }

    private fun resetState() {
        fileHasAutoEnterEnabled = false
        fileHasSourceRectHint = false
        buildCallNode = null
        buildCallContext = null
    }
}
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
        private const val METHOD_ENTER_PIP_MODE = "enterPictureInPictureMode"
        private const val METHOD_SET_PIP_PARAMS = "setPictureInPictureParams"

        private const val KEY_HAS_SET_AUTO_ENTER_ENABLED = "hasSetAutoEnterEnabled"
        private const val KEY_HAS_SET_SOURCE_RECT_HINT = "hasSetSourceRectHint"
        private const val KEY_HAS_PIP_USAGE = "hasPipUsage"
    }

    // Track per-file state
    private var hasSetAutoEnterEnabled = false
    private var hasSetSourceRectHint = false
    private var hasPipUsage = false

    // Track incident locations for reporting
    private var enterPipLocation: UCallExpression? = null
    private var setPipParamsLocation: UCallExpression? = null
    private var enterPipContext: JavaContext? = null
    private var setPipParamsContext: JavaContext? = null

    override fun getApplicableMethodNames(): List<String> = listOf(
        METHOD_SET_AUTO_ENTER_ENABLED,
        METHOD_SET_SOURCE_RECT_HINT,
        METHOD_ENTER_PIP_MODE,
        METHOD_SET_PIP_PARAMS,
    )

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        val containingClass = method.containingClass?.qualifiedName ?: ""

        when (method.name) {
            METHOD_SET_AUTO_ENTER_ENABLED -> {
                if (containingClass == PIP_PARAMS_BUILDER_CLASS) {
                    hasSetAutoEnterEnabled = true
                }
            }
            METHOD_SET_SOURCE_RECT_HINT -> {
                if (containingClass == PIP_PARAMS_BUILDER_CLASS) {
                    hasSetSourceRectHint = true
                }
            }
            METHOD_ENTER_PIP_MODE -> {
                val activityClass = method.containingClass
                val isActivityMethod = activityClass != null &&
                        isActivitySubclass(context, activityClass)
                if (isActivityMethod) {
                    hasPipUsage = true
                    if (enterPipLocation == null) {
                        enterPipLocation = node
                        enterPipContext = context
                    }
                }
            }
            METHOD_SET_PIP_PARAMS -> {
                val activityClass = method.containingClass
                val isActivityMethod = activityClass != null &&
                        isActivitySubclass(context, activityClass)
                if (isActivityMethod) {
                    hasPipUsage = true
                    if (setPipParamsLocation == null) {
                        setPipParamsLocation = node
                        setPipParamsContext = context
                    }
                }
            }
        }
    }

    private fun isActivitySubclass(
        context: JavaContext,
        psiClass: com.intellij.psi.PsiClass,
    ): Boolean {
        val evaluator = context.evaluator
        return evaluator.extendsClass(psiClass, "android.app.Activity", true)
    }

    override fun afterCheckEachProject(context: Context) {
        if (!hasPipUsage) {
            reset()
            return
        }

        val missingAutoEnter = !hasSetAutoEnterEnabled
        val missingSourceRectHint = !hasSetSourceRectHint

        if (context.isGlobalAnalysis()) {
            // Report directly
            if (missingAutoEnter || missingSourceRectHint) {
                reportIssue(context, missingAutoEnter, missingSourceRectHint)
            }
        } else {
            // Store partial results for later aggregation
            val map = context.getPartialResults(ISSUE).map()
            map.put(KEY_HAS_PIP_USAGE, true)
            map.put(KEY_HAS_SET_AUTO_ENTER_ENABLED, hasSetAutoEnterEnabled)
            map.put(KEY_HAS_SET_SOURCE_RECT_HINT, hasSetSourceRectHint)
        }

        reset()
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        var anyPipUsage = false
        var anyAutoEnterEnabled = false
        var anySourceRectHint = false

        for (map in partialResults.maps()) {
            if (map.getBoolean(KEY_HAS_PIP_USAGE) == true) {
                anyPipUsage = true
            }
            if (map.getBoolean(KEY_HAS_SET_AUTO_ENTER_ENABLED) == true) {
                anyAutoEnterEnabled = true
            }
            if (map.getBoolean(KEY_HAS_SET_SOURCE_RECT_HINT) == true) {
                anySourceRectHint = true
            }
        }

        if (!anyPipUsage) return

        val missingAutoEnter = !anyAutoEnterEnabled
        val missingSourceRectHint = !anySourceRectHint

        if (missingAutoEnter || missingSourceRectHint) {
            reportIssue(context, missingAutoEnter, missingSourceRectHint)
        }
    }

    private fun reportIssue(
        context: Context,
        missingAutoEnter: Boolean,
        missingSourceRectHint: Boolean,
    ) {
        val missingMethods = buildList {
            if (missingAutoEnter) add("`setAutoEnterEnabled(true)`")
            if (missingSourceRectHint) add("`setSourceRectHint(...)`")
        }

        val message = "Picture-in-picture best practices not followed: missing call(s) to " +
                "${missingMethods.joinToString(" and ")} on `PictureInPictureParams.Builder`. " +
                "Starting in Android 12, these are required for smooth PiP transition animations."

        // Try to report on a specific node if we have one from a JavaContext
        val javaCtx = enterPipContext ?: setPipParamsContext
        val node = enterPipLocation ?: setPipParamsLocation

        if (javaCtx != null && node != null) {
            javaCtx.report(
                issue = ISSUE,
                scope = node,
                location = javaCtx.getLocation(node),
                message = message,
            )
        } else {
            // Fallback: report at project level
            context.report(
                issue = ISSUE,
                location = com.android.tools.lint.detector.api.Location.create(context.project.dir),
                message = message,
            )
        }
    }

    private fun reset() {
        hasSetAutoEnterEnabled = false
        hasSetSourceRectHint = false
        hasPipUsage = false
        enterPipLocation = null
        setPipParamsLocation = null
        enterPipContext = null
        setPipParamsContext = null
    }
}
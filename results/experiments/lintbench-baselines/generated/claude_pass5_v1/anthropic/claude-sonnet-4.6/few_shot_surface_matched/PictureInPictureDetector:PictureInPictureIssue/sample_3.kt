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
        private const val KEY_HAS_ENTER_PIP_CALL = "hasEnterPipCall"

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

        private val APPLICABLE_METHODS = listOf(
            "setAutoEnterEnabled",
            "setSourceRectHint",
            "enterPictureInPictureMode"
        )
    }

    // Per-module state tracking
    private var hasAutoEnterEnabled = false
    private var hasSourceRectHint = false
    private var hasEnterPipCall = false
    private var enterPipCallLocation: com.android.tools.lint.detector.api.Location? = null

    override fun getApplicableMethodNames(): List<String> = APPLICABLE_METHODS

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val methodName = method.name

        when (methodName) {
            "setAutoEnterEnabled" -> {
                if (context.evaluator.isMemberInSubClassOf(method, PIP_PARAMS_BUILDER)) {
                    // Check that the argument is true
                    val arg = node.valueArguments.firstOrNull()
                    val value = arg?.let {
                        context.evaluator.computeArgumentMapping(node, method)
                    }
                    // We consider any call to setAutoEnterEnabled as an attempt;
                    // we track it regardless and check the value loosely
                    val argValue = node.valueArguments.firstOrNull()
                    val constValue = argValue?.evaluate()
                    if (constValue == true || constValue == null) {
                        // null means we can't evaluate it statically — give benefit of doubt
                        hasAutoEnterEnabled = true
                    }
                }
            }
            "setSourceRectHint" -> {
                if (context.evaluator.isMemberInSubClassOf(method, PIP_PARAMS_BUILDER)) {
                    hasSourceRectHint = true
                }
            }
            "enterPictureInPictureMode" -> {
                // android.app.Activity.enterPictureInPictureMode
                if (context.evaluator.isMemberInSubClassOf(method, "android.app.Activity")) {
                    hasEnterPipCall = true
                    if (enterPipCallLocation == null) {
                        enterPipCallLocation = context.getLocation(node)
                    }
                }
            }
        }
    }

    override fun afterCheckEachProject(context: Context) {
        if (!context.isGlobalAnalysis()) {
            // Store partial results for later aggregation
            val map = context.getPartialResults(ISSUE).map()
            if (hasEnterPipCall) {
                map.put(KEY_HAS_ENTER_PIP_CALL, true)
                map.put(KEY_HAS_AUTO_ENTER, hasAutoEnterEnabled)
                map.put(KEY_HAS_SOURCE_RECT_HINT, hasSourceRectHint)
                enterPipCallLocation?.let { loc ->
                    map.put(KEY_LOCATION, loc)
                }
            }
        } else {
            // Global analysis: report directly
            if (hasEnterPipCall) {
                val missingAutoEnter = !hasAutoEnterEnabled
                val missingSourceRectHint = !hasSourceRectHint
                if (missingAutoEnter || missingSourceRectHint) {
                    val missing = buildMissingList(missingAutoEnter, missingSourceRectHint)
                    val location = enterPipCallLocation ?: return
                    val message = buildMessage(missing)
                    context.report(
                        Incident(
                            ISSUE,
                            location,
                            message
                        )
                    )
                }
            }
        }

        // Reset state for next project
        hasAutoEnterEnabled = false
        hasSourceRectHint = false
        hasEnterPipCall = false
        enterPipCallLocation = null
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        var anyEnterPipCall = false
        var anyAutoEnter = false
        var anySourceRectHint = false
        var firstLocation: com.android.tools.lint.detector.api.Location? = null

        for (map in partialResults.maps()) {
            if (map.getBoolean(KEY_HAS_ENTER_PIP_CALL) == true) {
                anyEnterPipCall = true
                if (map.getBoolean(KEY_HAS_AUTO_ENTER) == true) {
                    anyAutoEnter = true
                }
                if (map.getBoolean(KEY_HAS_SOURCE_RECT_HINT) == true) {
                    anySourceRectHint = true
                }
                if (firstLocation == null) {
                    firstLocation = map.getLocation(KEY_LOCATION)
                }
            }
        }

        if (anyEnterPipCall) {
            val missingAutoEnter = !anyAutoEnter
            val missingSourceRectHint = !anySourceRectHint
            if (missingAutoEnter || missingSourceRectHint) {
                val missing = buildMissingList(missingAutoEnter, missingSourceRectHint)
                val location = firstLocation ?: context.project.dir.let {
                    com.android.tools.lint.detector.api.Location.create(it)
                }
                val message = buildMessage(missing)
                context.report(
                    Incident(
                        ISSUE,
                        location,
                        message
                    )
                )
            }
        }
    }

    private fun buildMissingList(missingAutoEnter: Boolean, missingSourceRectHint: Boolean): List<String> {
        val missing = mutableListOf<String>()
        if (missingAutoEnter) missing.add("`setAutoEnterEnabled(true)`")
        if (missingSourceRectHint) missing.add("`setSourceRectHint(...)`")
        return missing
    }

    private fun buildMessage(missing: List<String>): String {
        return if (missing.size == 1) {
            "Picture-in-picture best practices not followed: missing call to ${missing[0]} " +
                "on `PictureInPictureParams.Builder` for smoother transitions on Android 12+"
        } else {
            "Picture-in-picture best practices not followed: missing calls to ${missing.joinToString(" and ")} " +
                "on `PictureInPictureParams.Builder` for smoother transitions on Android 12+"
        }
    }
}
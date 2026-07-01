package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.PartialResults
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class PictureInPictureDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "PictureInPictureIssue",
            briefDescription = "Picture In Picture best practices not followed",
            explanation = """
                Starting in Android 12, the recommended approach for enabling picture-in-picture (PiP) \
                has changed. If your app does not use the new approach, your app's transition animations \
                will be of poor quality compared to other apps. The new approach requires calling \
                `setAutoEnterEnabled(true)` and `setSourceRectHint(...)`.
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(PictureInPictureDetector::class.java, Scope.JAVA_FILE_SCOPE),
            androidSpecific = true,
        )
    }

    private var hasAutoEnter = false
    private var hasSourceRect = false
    private var hasEnterPiP = false

    private var autoEnterLocation: Location? = null
    private var enterPiPLocation: Location? = null

    override fun getApplicableMethodNames(): List<String> {
        return listOf("setAutoEnterEnabled", "setSourceRectHint", "enterPictureInPictureMode")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val name = method.name
        val evaluator = context.evaluator

        when (name) {
            "setAutoEnterEnabled" -> {
                if (evaluator.isMemberInSubClassOf(method, "android.app.PictureInPictureParams.Builder")) {
                    val arg = node.valueArguments.firstOrNull()
                    val value = arg?.evaluate()
                    if (value == true) {
                        hasAutoEnter = true
                        autoEnterLocation = context.getLocation(node)
                    }
                }
            }
            "setSourceRectHint" -> {
                if (evaluator.isMemberInSubClassOf(method, "android.app.PictureInPictureParams.Builder")) {
                    hasSourceRect = true
                }
            }
            "enterPictureInPictureMode" -> {
                if (evaluator.isMemberInSubClassOf(method, "android.app.Activity")) {
                    hasEnterPiP = true
                    enterPiPLocation = context.getLocation(node)
                }
            }
        }
    }

    override fun afterCheckEachProject(context: Context) {
        checkAndReport(context)
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResults) {
        checkAndReport(context)
    }

    private fun checkAndReport(context: Context) {
        if (hasEnterPiP && (!hasAutoEnter || !hasSourceRect)) {
            val loc = enterPiPLocation
            if (loc != null) {
                context.report(
                    Incident(
                        ISSUE,
                        loc,
                        "To support smoother PiP transitions on Android 12+, use setAutoEnterEnabled(true) and setSourceRectHint(...) instead of enterPictureInPictureMode()"
                    )
                )
                hasEnterPiP = false
            }
        } else if (hasAutoEnter && !hasSourceRect) {
            val loc = autoEnterLocation
            if (loc != null) {
                context.report(
                    Incident(
                        ISSUE,
                        loc,
                        "When using setAutoEnterEnabled(true), also call setSourceRectHint(...) to ensure a smooth transition animation"
                    )
                )
                hasAutoEnter = false
            }
        }
    }
}
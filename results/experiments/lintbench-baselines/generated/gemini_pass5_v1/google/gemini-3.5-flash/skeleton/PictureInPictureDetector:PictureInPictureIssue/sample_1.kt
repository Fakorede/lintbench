package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.PartialResult
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getParentOfType

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
                Starting in Android 12, the recommended approach for enabling picture-in-picture (PiP) 
                has changed. If your app does not use the new approach, your app's transition animations 
                will be of poor quality compared to other apps. The new approach requires calling 
                `setAutoEnterEnabled(true)` and `setSourceRectHint(...)`.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableMethodNames(): List<String>? {
        return listOf("build", "enterPictureInPictureMode")
    }

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        val evaluator = context.evaluator
        if (method.name == "build" && evaluator.isMemberInClass(method, "android.app.PictureInPictureParams.Builder")) {
            val uMethod = node.getParentOfType<UMethod>()
            if (uMethod != null) {
                var hasAutoEnter = false
                var hasSourceRectHint = false

                checkElement(uMethod) { element ->
                    if (element is UCallExpression) {
                        val name = element.methodName
                        if (name == "setAutoEnterEnabled") {
                            val args = element.valueArguments
                            if (args.isNotEmpty()) {
                                val value = args[0].evaluate()
                                if (value == true) {
                                    hasAutoEnter = true
                                }
                            }
                        } else if (name == "setSourceRectHint") {
                            hasSourceRectHint = true
                        }
                    }
                }

                if (!hasAutoEnter || !hasSourceRectHint) {
                    val missing = mutableListOf<String>()
                    if (!hasAutoEnter) missing.add("setAutoEnterEnabled(true)")
                    if (!hasSourceRectHint) missing.add("setSourceRectHint(...)")

                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "To support smoother Picture-in-Picture transitions on Android 12+, " +
                                "call ${missing.joinToString(" and ")} on the PictureInPictureParams.Builder."
                    )
                }
            }
        } else if (method.name == "enterPictureInPictureMode" && evaluator.isMemberInClass(method, "android.app.Activity")) {
            val containingMethod = node.getParentOfType<UMethod>()
            if (containingMethod != null && containingMethod.name == "onUserLeaveHint") {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Instead of calling enterPictureInPictureMode() in onUserLeaveHint(), " +
                            "use setPictureInPictureParams() with setAutoEnterEnabled(true) " +
                            "and setSourceRectHint(...) for a smoother transition to PiP."
                )
            }
        }
    }

    private fun checkElement(element: UElement, visitor: (UElement) -> Unit) {
        visitor(element)
        for (child in element.uastChildren) {
            checkElement(child, visitor)
        }
    }

    override fun afterCheckEachProject(context: Context) {
        // No-op
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        // No-op
    }
}
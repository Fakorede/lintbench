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
import org.jetbrains.uast.UClass
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.visitor.AbstractUastVisitor

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
                `setAutoEnterEnabled(true)` and `setSourceRectHint(...)` on `PictureInPictureParams.Builder`.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableMethodNames(): List<String>? {
        return listOf("enterPictureInPictureMode", "setPictureInPictureParams")
    }

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        val evaluator = context.evaluator
        if (!evaluator.isMemberInSubClassOf(method, "android.app.Activity", false)) {
            return
        }

        val containingClass = node.getParentOfType(UClass::class.java) ?: return
        var callsAutoEnter = false
        var callsSourceRect = false

        containingClass.accept(object : AbstractUastVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
                val name = node.methodName
                if (name == "setAutoEnterEnabled") {
                    val arg = node.valueArguments.firstOrNull()
                    if (arg != null && (arg.evaluate() == true || arg.asSourceString() == "true")) {
                        callsAutoEnter = true
                    }
                } else if (name == "setSourceRectHint") {
                    callsSourceRect = true
                }
                return super.visitCallExpression(node)
            }
        })

        if (!callsAutoEnter || !callsSourceRect) {
            val message = "To support smoother transitions into picture-in-picture (PiP) mode " +
                    "starting in Android 12, you should call `setAutoEnterEnabled(true)` " +
                    "and `setSourceRectHint(...)` on your `PictureInPictureParams.Builder`."
            context.report(ISSUE, node, context.getLocation(node), message)
        }
    }

    override fun afterCheckEachProject(context: Context) {
        // No-op
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        // No-op
    }
}
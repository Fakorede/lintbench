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
            explanation = "Starting in Android 12, the recommended approach for enabling picture-in-picture (PiP) " +
                    "has changed. If your app does not use the new approach, your app's transition animations " +
                    "will be of poor quality compared to other apps. The new approach requires calling " +
                    "setAutoEnterEnabled(true) and setSourceRectHint(...).",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    private val processedClasses = mutableSetOf<UClass>()

    override fun beforeCheckFile(context: Context) {
        processedClasses.clear()
    }

    override fun getApplicableMethodNames(): List<String>? {
        return listOf("enterPictureInPictureMode", "setPictureInPictureParams")
    }

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        var current = node.uastParent
        var containingClass: UClass? = null
        while (current != null) {
            if (current is UClass) {
                containingClass = current
                break
            }
            current = current.uastParent
        }
        
        if (containingClass == null || !processedClasses.add(containingClass)) {
            return
        }

        var hasAutoEnterEnabled = false
        var hasSourceRectHint = false

        containingClass.accept(object : AbstractUastVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
                val methodName = node.methodName
                if (methodName == "setAutoEnterEnabled") {
                    val arg = node.valueArguments.firstOrNull()
                    if (arg != null) {
                        val value = arg.evaluate()
                        if (value == true) {
                            hasAutoEnterEnabled = true
                        }
                    }
                } else if (methodName == "setSourceRectHint") {
                    hasSourceRectHint = true
                }
                return super.visitCallExpression(node)
            }
        })

        if (!hasAutoEnterEnabled || !hasSourceRectHint) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "To support smooth PiP transitions on Android 12 and higher, " +
                        "call `setAutoEnterEnabled(true)` and `setSourceRectHint(...)` " +
                        "on the `PictureInPictureParams.Builder`."
            )
        }
    }

    override fun afterCheckEachProject(context: Context) {
        // No-op required by the skeleton
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        // No-op required by the skeleton
    }
}
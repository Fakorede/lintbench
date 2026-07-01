package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.skipParenthesizedExprDown

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "UnsupportedChromeOsCameraSystemFeature",
            briefDescription = "Looking for Rear Camera only feature",
            explanation = """
                Looking for `FEATURE_CAMERA` only looks for a rear facing camera, which \
                certain large screen devices don't have, as well as newer device configurations \
                and modes may place the device in a state where the rear camera is not available. \
                To fix the issue, look for `FEATURE_CAMERA_ANY` instead.
                """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                ChromeOsSourceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private const val FEATURE_CAMERA = "android.hardware.camera"
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf("hasSystemFeature")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        if (!evaluator.isMemberInClass(method, "android.content.pm.PackageManager")) {
            return
        }

        val args = node.valueArguments
        if (args.isEmpty()) return
        val firstArg = args[0].skipParenthesizedExprDown() ?: return

        if (isFeatureCamera(firstArg)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(firstArg),
                "Look for `FEATURE_CAMERA_ANY` instead of `FEATURE_CAMERA` to support Chromebooks and devices without a rear camera"
            )
        }
    }

    private fun isFeatureCamera(expression: UExpression): Boolean {
        if (expression is UReferenceExpression) {
            val resolved = expression.resolve()
            if (resolved is PsiField) {
                val containingClass = resolved.containingClass
                if (containingClass != null && "android.content.pm.PackageManager" == containingClass.qualifiedName) {
                    if ("FEATURE_CAMERA" == resolved.name) {
                        return true
                    }
                }
            }
        }

        val constant = expression.evaluate()
        if (constant is String && constant == FEATURE_CAMERA) {
            return true
        }

        return false
    }
}
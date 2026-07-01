package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiField
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UElement

class ChromeOsSourceDetector : Detector(), UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UReferenceExpression::class.java, ULiteralExpression::class.java)
    }

    override fun visitNode(context: JavaContext, node: UElement) {
        when (node) {
            is UReferenceExpression -> {
                val resolved = node.resolve()
                if (resolved is PsiField) {
                    val containingClass = resolved.containingClass
                    if (containingClass?.qualifiedName == "android.content.pm.PackageManager" &&
                        resolved.name == "FEATURE_CAMERA") {
                        reportIssue(context, node)
                    }
                }
            }
            is ULiteralExpression -> {
                if (node.value == "android.hardware.camera") {
                    reportIssue(context, node)
                }
            }
        }
    }

    private fun reportIssue(context: JavaContext, node: UElement) {
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Use `FEATURE_CAMERA_ANY` instead of `FEATURE_CAMERA` to support devices without a rear camera."
        )
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "UnsupportedChromeOsCameraSystemFeature",
            briefDescription = "Looking for Rear Camera only feature",
            explanation = """
                You should look for the `FEATURE_CAMERA_ANY` features to include all possible cameras that may be on the device. Looking for `FEATURE_CAMERA` only looks for a rear facing camera, which certain large screen devices don't have, as well as newer device configurations and modes may place the device in a state where the rear camera is not available. To fix the issue, look for `FEATURE_CAMERA_ANY` instead.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                ChromeOsSourceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}
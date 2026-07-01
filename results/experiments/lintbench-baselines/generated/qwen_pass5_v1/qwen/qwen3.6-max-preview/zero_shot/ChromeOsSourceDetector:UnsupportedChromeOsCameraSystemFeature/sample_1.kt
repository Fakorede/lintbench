package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.UastScanner
import com.intellij.psi.PsiField
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UElementHandler
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UReferenceExpression

class ChromeOsSourceDetector : Detector(), UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UReferenceExpression::class.java, ULiteralExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitReferenceExpression(node: UReferenceExpression) {
                if (node.identifier != "FEATURE_CAMERA") return

                val resolved = context.evaluator.resolve(node)
                if (resolved is PsiField) {
                    val qualifiedName = context.evaluator.getQualifiedName(resolved)
                    if (qualifiedName == "android.content.pm.PackageManager.FEATURE_CAMERA") {
                        reportIssue(context, node)
                    }
                }
            }

            override fun visitLiteralExpression(node: ULiteralExpression) {
                if (node.value == "android.hardware.camera") {
                    reportIssue(context, node)
                }
            }
        }
    }

    private fun reportIssue(context: JavaContext, node: UElement) {
        context.report(
            ISSUE,
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
                You should look for the `FEATURE_CAMERA_ANY` features to include all possible \
                cameras that may be on the device. Looking for `FEATURE_CAMERA` only looks \
                for a rear facing camera, which certain large screen devices don't have, \
                as well as newer device configurations and modes may place the device \
                in a state where the rear camera is not available. To fix the issue, \
                look for `FEATURE_CAMERA_ANY` instead.
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
package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            ChromeOsSourceDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        private const val FEATURE_CAMERA_VALUE = "android.hardware.camera"

        @JvmField
        val ISSUE = Issue.create(
            id = "UnsupportedChromeOsCameraSystemFeature",
            briefDescription = "Looking for Rear Camera only feature",
            explanation = "Looking for `FEATURE_CAMERA_ANY` features to include all possible cameras that may be on the device. Looking for `FEATURE_CAMERA` only looks for a rear facing camera, which certain large screen devices don't have, as well as newer device configurations and modes may place the device in a state where the rear camera is not available. To fix the issue, look for `FEATURE_CAMERA_ANY` instead.",
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UCallExpression::class.java)

    override fun getApplicableMethodNames(): List<String> = listOf("hasSystemFeature")

    private fun checkCall(context: JavaContext, node: UCallExpression) {
        val method = node.resolve() ?: return
        if (method.containingClass?.qualifiedName != "android.content.pm.PackageManager") return

        val arg = node.valueArguments.getOrNull(0) ?: return
        val value = context.evaluator.evaluate(arg) as? String ?: return

        if (value == FEATURE_CAMERA_VALUE) {
            context.report(
                ISSUE,
                context.getLocation(arg),
                "Use `FEATURE_CAMERA_ANY` instead of `FEATURE_CAMERA` to support devices without a rear-facing camera."
            )
        }
    }

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        checkCall(context, node)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                // Not applicable for this detector
            }

            override fun visitCallExpression(node: UCallExpression) {
                checkCall(context, node)
            }
        }
}
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
import org.jetbrains.uast.evaluateString

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            ChromeOsSourceDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "UnsupportedChromeOsCameraSystemFeature",
            briefDescription = "Looking for Rear Camera only feature",
            explanation = """
                You should look for the `FEATURE_CAMERA_ANY` features to include all possible \
                cameras that may be on the device. Looking for `FEATURE_CAMERA` only looks \
                for a rear facing camera, which certain large screen devices don't have, as \
                well as newer device configurations and modes may place the device in a state \
                where the rear camera is not available. To fix the issue, look for \
                `FEATURE_CAMERA_ANY` instead.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private const val PACKAGE_MANAGER_CLASS = "android.content.pm.PackageManager"
        private const val FEATURE_CAMERA = "android.hardware.camera"
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>>? = null

    override fun getApplicableMethodNames(): List<String>? = listOf("hasSystemFeature")

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        if (method.containingClass?.qualifiedName != PACKAGE_MANAGER_CLASS) {
            return
        }

        val firstArg = node.valueArguments.firstOrNull() ?: return
        val value = firstArg.evaluateString() ?: return

        if (value == FEATURE_CAMERA) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Looking for a rear-facing camera only; use `PackageManager.FEATURE_CAMERA_ANY` instead to support devices without a rear camera.",
            )
        }
    }

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                // Not needed; method calls are handled by visitMethodCall.
            }

            override fun visitCallExpression(node: UCallExpression) {
                // Not needed; method calls are handled by visitMethodCall.
            }
        }
}
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
                You should look for the `FEATURE_CAMERA_ANY` feature to include all possible
                cameras that may be on the device. Looking for `FEATURE_CAMERA` only looks for
                a rear facing camera, which certain large screen devices don't have, as well
                as newer device configurations and modes may place the device in a state where
                the rear camera is not available. To fix the issue, look for `FEATURE_CAMERA_ANY`
                instead.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private const val METHOD_HAS_SYSTEM_FEATURE = "hasSystemFeature"
        private const val PACKAGE_MANAGER_CLASS = "android.content.pm.PackageManager"
        private const val FEATURE_CAMERA = "android.hardware.camera"
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>>? = null

    override fun getApplicableMethodNames(): List<String>? =
        listOf(METHOD_HAS_SYSTEM_FEATURE)

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        if (method.containingClass?.qualifiedName != PACKAGE_MANAGER_CLASS) {
            return
        }

        val argument = node.valueArguments.firstOrNull() ?: return
        val value = argument.evaluateString() ?: return
        if (value != FEATURE_CAMERA) {
            return
        }

        context.report(
            issue = ISSUE,
            scopeNode = node,
            location = context.getLocation(argument),
            message = "Looking for `PackageManager.FEATURE_CAMERA` only detects a rear-facing camera; "
                + "use `PackageManager.FEATURE_CAMERA_ANY` instead to support all device configurations.",
        )
    }

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                // Not used: method calls are handled via visitMethodCall.
            }

            override fun visitCallExpression(node: UCallExpression) {
                // Not used: method calls are handled via visitMethodCall.
            }
        }
}
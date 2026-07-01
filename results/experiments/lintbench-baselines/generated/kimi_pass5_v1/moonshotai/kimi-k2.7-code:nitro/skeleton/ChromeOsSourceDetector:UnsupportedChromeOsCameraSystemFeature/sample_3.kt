package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
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
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val PACKAGE_MANAGER_CLASS = "android.content.pm.PackageManager"
        private const val FEATURE_CAMERA = "android.hardware.camera"
        private const val FEATURE_CAMERA_ANY = "android.hardware.camera.any"

        private val IMPLEMENTATION = Implementation(
            ChromeOsSourceDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "UnsupportedChromeOsCameraSystemFeature",
            briefDescription = "Looking for Rear Camera only feature",
            explanation = """
                Looking for `FEATURE_CAMERA` only looks for a rear facing camera, which certain large screen
                devices don't have, as well as newer device configurations and modes may place the device
                in a state where the rear camera is not available. To fix the issue, look for
                `FEATURE_CAMERA_ANY` instead.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>>? = null

    override fun getApplicableMethodNames(): List<String>? = listOf("hasSystemFeature")

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        if (!context.evaluator.isMemberInClass(method, PACKAGE_MANAGER_CLASS)) {
            return
        }

        val arg: UExpression = node.valueArguments.firstOrNull() ?: return
        val value = ConstantEvaluator.evaluateString(arg) ?: return
        if (value != FEATURE_CAMERA) {
            return
        }

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Use PackageManager.FEATURE_CAMERA_ANY ($FEATURE_CAMERA_ANY) instead of FEATURE_CAMERA ($FEATURE_CAMERA) for compatibility with large screen devices"
        )
    }

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                // Detection is performed in visitMethodCall.
            }

            override fun visitCallExpression(node: UCallExpression) {
                // Detection is performed in visitMethodCall.
            }
        }
}
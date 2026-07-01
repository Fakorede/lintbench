package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
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
            """,
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private const val HAS_SYSTEM_FEATURE_METHOD = "hasSystemFeature"
        private const val PACKAGE_MANAGER_CLASS = "android.content.pm.PackageManager"
        private const val FEATURE_CAMERA = "android.hardware.camera"
        private const val FEATURE_CAMERA_ANY = "android.hardware.camera.any"
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>>? = null

    override fun getApplicableMethodNames(): List<String> = listOf(HAS_SYSTEM_FEATURE_METHOD)

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        val evaluator = context.evaluator
        if (!evaluator.isMemberInClass(method, PACKAGE_MANAGER_CLASS)) {
            return
        }

        val arguments = node.valueArguments
        if (arguments.isEmpty()) {
            return
        }

        val firstArg = arguments[0]
        val argValue = firstArg.evaluate()

        if (argValue == FEATURE_CAMERA) {
            context.report(
                issue = ISSUE,
                scope = node,
                location = context.getLocation(firstArg),
                message = "You should look for the `FEATURE_CAMERA_ANY` feature to include all " +
                    "possible cameras that may be on the device, not just the rear-facing camera " +
                    "(`FEATURE_CAMERA`). Use `PackageManager.FEATURE_CAMERA_ANY` instead.",
            )
        }
    }

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                // Not used; method call checking is done via visitMethodCall
            }

            override fun visitCallExpression(node: UCallExpression) {
                // Not used; method call checking is done via visitMethodCall
            }
        }
}
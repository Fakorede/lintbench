package com.android.tools.lint.checks

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
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.util.isMethodCall

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val PACKAGE_MANAGER_CLASS = "android.content.pm.PackageManager"
        private const val HAS_SYSTEM_FEATURE_METHOD = "hasSystemFeature"
        private const val FEATURE_CAMERA = "android.hardware.camera"
        private const val FEATURE_CAMERA_FIELD = "FEATURE_CAMERA"
        private const val FEATURE_CAMERA_ANY = "android.hardware.camera.any"
        private const val FEATURE_CAMERA_ANY_FIELD = "FEATURE_CAMERA_ANY"

        val UNSUPPORTED_CAMERA_FEATURE = Issue.create(
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
            category = Category.CHROME_OS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                ChromeOsSourceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            ),
            moreInfo = "https://developer.android.com/guide/topics/large-screens/large-screen-cookbook#chromebook_camera_support"
        )
    }

    override fun getApplicableMethodNames(): List<String> = listOf(HAS_SYSTEM_FEATURE_METHOD)

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val containingClass = method.containingClass ?: return
        if (!context.evaluator.inheritsFrom(containingClass, PACKAGE_MANAGER_CLASS, false)) {
            return
        }

        val arguments = node.valueArguments
        if (arguments.isEmpty()) return

        val firstArg = arguments[0]
        val featureValue = resolveFeatureValue(context, firstArg) ?: return

        if (featureValue == FEATURE_CAMERA) {
            context.report(
                UNSUPPORTED_CAMERA_FEATURE,
                node,
                context.getLocation(firstArg),
                "Use `$FEATURE_CAMERA_ANY_FIELD` instead of `$FEATURE_CAMERA_FIELD` to include " +
                        "all cameras that may be available on the device"
            )
        }
    }

    private fun resolveFeatureValue(context: JavaContext, expression: UExpression): String? {
        // Try to evaluate as a constant string first
        val evaluated = expression.evaluate()
        if (evaluated is String) {
            return evaluated
        }

        // Try to resolve field reference
        if (expression is UReferenceExpression) {
            val resolved = expression.resolve()
            if (resolved != null) {
                val evaluator = context.evaluator
                // Check if it's a field in PackageManager
                if (resolved is com.intellij.psi.PsiField) {
                    val containingClass = resolved.containingClass
                    if (containingClass != null &&
                        evaluator.inheritsFrom(containingClass, PACKAGE_MANAGER_CLASS, false)
                    ) {
                        val fieldName = resolved.name
                        if (fieldName == FEATURE_CAMERA_FIELD) {
                            return FEATURE_CAMERA
                        }
                    }
                    // Evaluate the field's initial value
                    val initializer = resolved.initializer
                    if (initializer != null) {
                        val value = context.evaluator.getConstantValue(resolved)
                        if (value is String) {
                            return value
                        }
                    }
                }
            }
        }

        return null
    }
}
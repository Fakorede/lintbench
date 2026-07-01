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
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.evaluateString

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val FEATURE_CAMERA = "android.hardware.camera"
        private const val FEATURE_CAMERA_FIELD = "FEATURE_CAMERA"
        private const val PACKAGE_MANAGER_CLASS = "android.content.pm.PackageManager"

        @JvmField
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
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                ChromeOsSourceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            ),
            moreInfo = "https://developer.android.com/guide/topics/large-screens/large-screen-cookbook#chromebook_camera_support"
        )
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf("hasSystemFeature")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val containingClass = method.containingClass ?: return
        if (!context.evaluator.inheritsFrom(containingClass, PACKAGE_MANAGER_CLASS, false)) {
            return
        }

        val arguments = node.valueArguments
        if (arguments.isEmpty()) return

        val firstArg = arguments[0]
        if (isFeatureCameraArgument(context, firstArg)) {
            context.report(
                issue = UNSUPPORTED_CAMERA_FEATURE,
                scope = node,
                location = context.getLocation(firstArg),
                message = "You should look for the `FEATURE_CAMERA_ANY` features to include all " +
                        "possible cameras that may be on the device. Looking for `FEATURE_CAMERA` " +
                        "only looks for a rear facing camera, which certain large screen devices " +
                        "don't have, as well as newer device configurations and modes may place " +
                        "the device in a state where the rear camera is not available. To fix the " +
                        "issue, look for `FEATURE_CAMERA_ANY` instead."
            )
        }
    }

    private fun isFeatureCameraArgument(context: JavaContext, expression: UExpression): Boolean {
        // Check for string literal "android.hardware.camera"
        if (expression is ULiteralExpression) {
            val value = expression.evaluateString()
            if (value != null && value.equals(FEATURE_CAMERA, ignoreCase = true)) {
                return true
            }
        }

        // Check for reference to PackageManager.FEATURE_CAMERA
        if (expression is UReferenceExpression) {
            val resolved = expression.resolve()
            if (resolved != null) {
                val evaluatedValue = expression.evaluateString()
                if (evaluatedValue != null && evaluatedValue.equals(FEATURE_CAMERA, ignoreCase = true)) {
                    return true
                }
            }

            // Check by field name reference
            val referenceName = expression.resolvedName
            if (referenceName == FEATURE_CAMERA_FIELD) {
                // Make sure it's from PackageManager
                val psiField = resolved
                if (psiField is com.intellij.psi.PsiField) {
                    val containingClass = psiField.containingClass
                    if (containingClass != null &&
                        context.evaluator.inheritsFrom(containingClass, PACKAGE_MANAGER_CLASS, false)) {
                        return true
                    }
                }
                return true
            }
        }

        // Try evaluating as string constant
        val evaluated = expression.evaluate()
        if (evaluated is String && evaluated.equals(FEATURE_CAMERA, ignoreCase = true)) {
            return true
        }

        return false
    }
}
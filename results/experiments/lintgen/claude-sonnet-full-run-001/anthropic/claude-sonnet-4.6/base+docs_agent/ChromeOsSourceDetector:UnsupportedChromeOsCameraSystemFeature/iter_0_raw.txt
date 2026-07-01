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
        private const val PACKAGE_MANAGER_CLASS = "android.content.pm.PackageManager"
        private const val HAS_SYSTEM_FEATURE_METHOD = "hasSystemFeature"
        private const val FEATURE_CAMERA = "android.hardware.camera"
        private const val FEATURE_CAMERA_FIELD = "FEATURE_CAMERA"
        private const val FEATURE_CAMERA_ANY = "android.hardware.camera.any"

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
            category = Category.CHROME_OS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                ChromeOsSourceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            ),
            moreInfo = "https://developer.android.com/guide/topics/large-screens/large-screen-cookbook#chromebook_camera_support"
        )
    }

    override fun getApplicableMethodNames(): List<String> = listOf(HAS_SYSTEM_FEATURE_METHOD)

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!context.evaluator.isMemberInClass(method, PACKAGE_MANAGER_CLASS)) {
            return
        }

        val arguments = node.valueArguments
        if (arguments.isEmpty()) {
            return
        }

        val firstArg = arguments[0]
        if (isFeatureCameraArgument(firstArg)) {
            context.report(
                issue = UNSUPPORTED_CAMERA_FEATURE,
                scope = node,
                location = context.getLocation(firstArg),
                message = "Use `PackageManager.FEATURE_CAMERA_ANY` to detect any camera on the device, not just the rear-facing camera"
            )
        }
    }

    private fun isFeatureCameraArgument(expression: UExpression): Boolean {
        // Check if it's a string literal "android.hardware.camera"
        if (expression is ULiteralExpression) {
            val value = expression.evaluateString()
            return value?.equals(FEATURE_CAMERA, ignoreCase = true) == true
        }

        // Check if it's a reference to PackageManager.FEATURE_CAMERA
        if (expression is UReferenceExpression) {
            val resolvedName = expression.resolvedName
            if (resolvedName == FEATURE_CAMERA_FIELD) {
                // Make sure it's not FEATURE_CAMERA_ANY or other FEATURE_CAMERA_* variants
                val text = expression.asSourceString()
                if (!text.contains("ANY", ignoreCase = true) &&
                    !text.contains("FRONT", ignoreCase = true) &&
                    !text.contains("EXTERNAL", ignoreCase = true) &&
                    !text.contains("FLASH", ignoreCase = true) &&
                    !text.contains("AUTOFOCUS", ignoreCase = true) &&
                    !text.contains("CAPABILITY", ignoreCase = true) &&
                    !text.contains("LEVEL", ignoreCase = true)
                ) {
                    return true
                }
            }
        }

        // Evaluate the expression to check its string value
        val evaluated = expression.evaluate()
        if (evaluated is String) {
            return evaluated.equals(FEATURE_CAMERA, ignoreCase = true)
        }

        return false
    }
}
package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.evaluateString

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val FEATURE_CAMERA = "android.hardware.camera"
        private const val FEATURE_CAMERA_ANY = "android.hardware.camera.any"
        private const val PACKAGE_MANAGER_CLASS = "android.content.pm.PackageManager"

        @JvmField
        val UNSUPPORTED_CAMERA_FEATURE: Issue = Issue.create(
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

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        if (!context.evaluator.isMemberInClass(method, PACKAGE_MANAGER_CLASS)) {
            return
        }

        val arguments = node.valueArguments
        if (arguments.isEmpty()) {
            return
        }

        val firstArg: UExpression = arguments[0]
        val featureValue = firstArg.evaluateString() ?: return

        if (featureValue == FEATURE_CAMERA) {
            context.report(
                issue = UNSUPPORTED_CAMERA_FEATURE,
                scope = node,
                location = context.getLocation(firstArg),
                message = "You should look for `$FEATURE_CAMERA_ANY` instead of `$FEATURE_CAMERA` to include all possible cameras that may be on the device"
            )
        }
    }
}
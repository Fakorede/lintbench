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
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.evaluateString
import org.jetbrains.uast.util.isMethodCall

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val FEATURE_CAMERA = "android.hardware.camera"
        private const val PACKAGE_MANAGER = "android.content.pm.PackageManager"
        private const val HAS_SYSTEM_FEATURE = "hasSystemFeature"

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "UnsupportedChromeOsCameraSystemFeature",
            briefDescription = "Looking for rear camera only feature",
            explanation = """
                You should look for the `FEATURE_CAMERA_ANY` features to include all \
                possible cameras that may be on the device. Looking for `FEATURE_CAMERA` \
                only looks for a rear facing camera, which certain large screen devices \
                don't have, as well as newer device configurations and modes may place \
                the device in a state where the rear camera is not available. To fix the \
                issue, look for `FEATURE_CAMERA_ANY` instead.
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                ChromeOsSourceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>>? {
        return listOf(UCallExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                if (!node.isMethodCall()) {
                    return
                }
                if (node.methodName != HAS_SYSTEM_FEATURE) {
                    return
                }
                if (!context.evaluator.isMemberInClass(
                        node.resolve(),
                        PACKAGE_MANAGER
                    )
                ) {
                    return
                }

                val argument = node.valueArguments.firstOrNull() ?: return
                if (argument.evaluateString() == FEATURE_CAMERA) {
                    context.report(
                        ISSUE,
                        argument,
                        context.getLocation(argument),
                        "Use `PackageManager.FEATURE_CAMERA_ANY` instead of `PackageManager.FEATURE_CAMERA`"
                    )
                }
            }
        }
    }
}
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
import org.jetbrains.uast.evaluateString
import org.jetbrains.uast.util.isMethodCall

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "UnsupportedChromeOsCameraSystemFeature",
            briefDescription = "Looking for rear camera only feature",
            explanation = """
                You should look for the `FEATURE_CAMERA_ANY` features to include all possible cameras that may be on the device. Looking for `FEATURE_CAMERA` only looks for a rear facing camera, which certain large screen devices don't have, as well as newer device configurations and modes may place the device in a state where the rear camera is not available. To fix the issue, look for `FEATURE_CAMERA_ANY` instead.
            """.trimIndent(),
            category = Category.CHROME_OS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                ChromeOsSourceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private const val FEATURE_CAMERA = "android.hardware.camera"
    }

    override fun getApplicableUastTypes() = listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                if (!node.isMethodCall()) {
                    return
                }

                if (node.methodName != "hasSystemFeature") {
                    return
                }

                val method = node.resolve() ?: return
                if (!context.evaluator.isMemberInClass(method, "android.content.pm.PackageManager")) {
                    return
                }

                val arg = node.valueArguments.firstOrNull() ?: return
                val value = arg.evaluateString() ?: return

                if (value != FEATURE_CAMERA) {
                    return
                }

                val fix = fix()
                    .replace()
                    .range(context.getLocation(arg))
                    .with("PackageManager.FEATURE_CAMERA_ANY")
                    .build()

                context.report(
                    ISSUE,
                    arg,
                    context.getLocation(arg),
                    "Use `PackageManager.FEATURE_CAMERA_ANY` instead of `PackageManager.FEATURE_CAMERA` to support all cameras on large screen devices",
                    fix
                )
            }
        }
    }
}
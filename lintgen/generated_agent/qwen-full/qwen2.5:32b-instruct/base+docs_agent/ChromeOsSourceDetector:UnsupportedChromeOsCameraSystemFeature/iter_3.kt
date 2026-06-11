package com.android.tools.lint.checks

import com.android.SdkConstants.*
import com.android.annotations.VisibleForTesting
import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.*

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "ChromeOsRearCameraUsage",
            briefDescription = "Use FEATURE_CAMERA_ANY instead of FEATURE_CAMERA for rear camera support",
            explanation = """
                Using `FEATURE_CAMERA` only looks for a rear facing camera, which certain large screen devices don't have. To ensure compatibility with all possible cameras on the device, use `FEATURE_CAMERA_ANY`.
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

    @VisibleForTesting
    internal val FEATURE_CAMERA = "FEATURE_CAMERA"
    @VisibleForTesting
    internal val FEATURE_CAMERA_ANY = "FEATURE_CAMERA_ANY"

    override fun visitMethodCall(context: JavaContext, node: UastMethodCallExpression): Boolean {
        if (node.methodName != "checkFeature") return true

        val receiverType = context.evaluator.getType(node.receiver)
        if (receiverType == null || !context.evaluator.isSubtype(receiverType, ANDROID_CONTENT_CONTEXT)) {
            return true
        }

        val argument = node.valueArguments.getOrNull(0) ?: return true
        if (argument is UastLiteralExpression && argument.asRenderString() == FEATURE_CAMERA) {
            context.report(
                issue = ISSUE,
                scope = node,
                location = context.getLocation(node),
                message = "Use FEATURE_CAMERA_ANY instead of FEATURE_CAMERA to support all possible cameras on the device"
            )
        }

        return true
    }
}
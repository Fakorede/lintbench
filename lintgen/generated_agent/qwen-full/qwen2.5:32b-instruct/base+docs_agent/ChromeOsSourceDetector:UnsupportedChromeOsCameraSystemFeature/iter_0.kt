package com.android.tools.lint.checks

import com.android.SdkConstants.*
import com.android.annotations.VisibleForTesting
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*

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

    override fun getApplicableMethodNames() = listOf(FIND_CLASS)

    @VisibleForTesting
    internal val FEATURE_CAMERA = "FEATURE_CAMERA"
    @VisibleForTesting
    internal val FEATURE_CAMERA_ANY = "FEATURE_CAMERA_ANY"

    override fun visitMethodCall(context: JavaContext, node: UastMethodInvoke): Boolean {
        if (node.methodName != FIND_CLASS) return true

        val receiverType = context.evaluator.getType(node.receiver)
        if (receiverType == null || !context.evaluator.isSubtype(receiverType, ANDROID_CONTENT_CONTEXT)) {
            return true
        }

        val argument = node.valueArguments.getOrNull(0)?.sourcePsi ?: return true
        val argumentText = argument.text

        if (argumentText == FEATURE_CAMERA) {
            context.report(
                ISSUE,
                context.getLocation(node),
                "Use FEATURE_CAMERA_ANY instead of FEATURE_CAMERA to support all possible cameras on the device"
            )
        }

        return true
    }
}
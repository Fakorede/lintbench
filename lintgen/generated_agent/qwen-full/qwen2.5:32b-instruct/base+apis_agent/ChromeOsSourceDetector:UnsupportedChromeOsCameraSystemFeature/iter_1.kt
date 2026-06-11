package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.*

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

    companion object {
        val ISSUE = Issue.create(
            "ChromeOSCameraFeature",
            "Looking for `FEATURE_CAMERA` only looks for a rear facing camera, which certain large screen devices don't have. Use `FEATURE_CAMERA_ANY` instead.",
            "Certain large screen devices and newer device configurations may not have a rear-facing camera available. Using `FEATURE_CAMERA_ANY` ensures that all possible cameras are included.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            Implementation(ChromeOsSourceDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }

    override fun getApplicableMethodNames(): List<String>? {
        return listOf("hasSystemFeature")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val arguments = node.valueArguments
        if (arguments.size == 1 && arguments[0] is UElement) {
            val argumentValue = context.evaluator.evaluateToString(arguments[0])
            if (argumentValue != null && argumentValue in listOf("android.hardware.Camera", "android.hardware.camera2.Camera")) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Use FEATURE_CAMERA_ANY instead of FEATURE_CAMERA to support all possible cameras"
                )
            }
        }
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>>? {
        return listOf(UCallExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                val method = node.resolve()
                if (method != null && method.name == "hasSystemFeature") {
                    visitMethodCall(context, node, method)
                }
            }
        }
    }
}
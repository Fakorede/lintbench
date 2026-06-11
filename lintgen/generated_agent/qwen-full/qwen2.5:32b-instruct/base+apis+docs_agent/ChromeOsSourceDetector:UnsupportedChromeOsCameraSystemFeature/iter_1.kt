package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiElement
import org.jetbrains.uast.*

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "ChromeOSCameraFeature",
            briefDescription = "Looking for `FEATURE_CAMERA` only looks for a rear facing camera, which certain large screen devices don't have. Use `FEATURE_CAMERA_ANY` instead.",
            explanation = "Certain large screen devices and newer device configurations may not have a rear-facing camera available. Using `FEATURE_CAMERA_ANY` ensures that all possible cameras are included.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(ChromeOsSourceDetector::class.java, Scope.JAVA_FILE_SCOPE),
            moreInfo = "https://developer.android.com/guide/topics/large-screens/large-screen-cookbook#chromebook_camera_support"
        )
    }

    override fun getApplicableMethodNames(): List<String>? {
        return listOf("hasSystemFeature")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiElement) {
        val arguments = node.valueArguments
        if (arguments.size == 1 && arguments[0] is UReferenceExpression) {
            val argument = arguments[0]
            val referenceName = (argument as UReferenceExpression).getReferencedName()
            if ("FEATURE_CAMERA" == referenceName) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Use FEATURE_CAMERA_ANY instead of FEATURE_CAMERA"
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
                if (method != null && "hasSystemFeature" == method.name) {
                    visitMethodCall(context, node, method)
                }
            }
        }
    }
}
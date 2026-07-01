package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("hasSystemFeature")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!context.evaluator.isMemberInSubClassOf(method, "android.content.pm.PackageManager", false)) {
            return
        }

        val arg = node.valueArguments.firstOrNull() ?: return
        val value = ConstantEvaluator.evaluate(context, arg)

        if (value == "android.hardware.camera") {
            context.report(
                ISSUE,
                node,
                context.getLocation(arg),
                "Looking for `PackageManager.FEATURE_CAMERA` only checks for a rear-facing camera; use `PackageManager.FEATURE_CAMERA_ANY` instead to support large-screen devices such as Chromebooks"
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "UnsupportedChromeOsCameraSystemFeature",
            briefDescription = "Rear camera feature check may exclude large-screen devices",
            explanation = """
                On devices such as Chromebooks, there may not be a rear-facing camera, or the device may be in a configuration where the rear camera is unavailable. Checking for `PackageManager.FEATURE_CAMERA` only looks for a rear-facing camera. Use `PackageManager.FEATURE_CAMERA_ANY` instead to detect any camera on the device.
            """.trimIndent(),
            moreInfo = "https://developer.android.com/guide/topics/large-screens/large-screen-cookbook#chromebook_camera_support",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                ChromeOsSourceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}
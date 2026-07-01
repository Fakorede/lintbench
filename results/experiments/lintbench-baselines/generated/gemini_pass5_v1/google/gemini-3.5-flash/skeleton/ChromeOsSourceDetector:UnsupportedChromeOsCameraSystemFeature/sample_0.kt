package com.android.tools.lint.checks

import com.android.tools.lint.client.api.*
import com.android.tools.lint.detector.api.*
import com.intellij.psi.*
import org.jetbrains.uast.*

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            ChromeOsSourceDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "UnsupportedChromeOsCameraSystemFeature",
            briefDescription = "Looking for Rear Camera only feature",
            explanation = """
                Looking for the `FEATURE_CAMERA` feature only looks for a rear-facing camera, \
                which certain large screen devices (like Chromebooks) don't have. Newer device \
                configurations and modes may also place the device in a state where the rear \
                camera is not available. To support all possible cameras, look for `FEATURE_CAMERA_ANY` \
                instead.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf("hasSystemFeature")
    }

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        if (!context.evaluator.isMemberInSubclassOf(method, "android.content.pm.PackageManager", false)) {
            return
        }
        val argument = node.valueArguments.firstOrNull() ?: return
        val value = argument.evaluate() as? String
        if (value == "android.hardware.camera") {
            context.report(
                ISSUE,
                node,
                context.getLocation(argument),
                "Look for `FEATURE_CAMERA_ANY` instead of `FEATURE_CAMERA` to support Chromebooks and other devices without a rear-facing camera"
            )
        }
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>>? = null

    override fun createUastHandler(context: JavaContext): UElementHandler? = null
}
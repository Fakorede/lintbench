package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "UnsupportedChromeOsCameraSystemFeature",
            briefDescription = "Looking for Rear Camera only feature",
            explanation = """
                Looking for `FEATURE_CAMERA` only looks for a rear facing camera, which certain \
                large screen devices don't have, as well as newer device configurations and modes \
                may place the device in a state where the rear camera is not available. To fix the \
                issue, look for `FEATURE_CAMERA_ANY` instead.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                ChromeOsSourceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableMethodNames(): List<String> = listOf("hasSystemFeature")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!context.evaluator.isMemberInClass(method, "android.content.pm.PackageManager")) {
            return
        }
        val argument = node.valueArguments.firstOrNull() ?: return
        val value = argument.evaluate() as? String ?: return
        if (value == "android.hardware.camera") {
            val fix = createFix(argument)
            context.report(
                ISSUE,
                node,
                context.getLocation(argument),
                "Look for `FEATURE_CAMERA_ANY` instead of `FEATURE_CAMERA` to support Chromebooks and devices without a rear camera",
                fix
            )
        }
    }

    private fun createFix(argument: UExpression): LintFix? {
        val source = argument.asSourceString()
        return if (source.contains("FEATURE_CAMERA")) {
            val replacement = source.replace("FEATURE_CAMERA", "FEATURE_CAMERA_ANY")
            fix().replace().with(replacement).build()
        } else if (source.contains("\"android.hardware.camera\"")) {
            val replacement = source.replace("\"android.hardware.camera\"", "\"android.hardware.camera.any\"")
            fix().replace().with(replacement).build()
        } else {
            null
        }
    }
}
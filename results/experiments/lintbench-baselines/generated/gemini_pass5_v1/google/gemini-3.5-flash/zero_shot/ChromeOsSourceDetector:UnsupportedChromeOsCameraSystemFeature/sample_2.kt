package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> {
        return listOf("hasSystemFeature")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        if (!evaluator.isMemberInSubclassOf(method, "android.content.pm.PackageManager", false)) {
            return
        }
        val arguments = node.valueArguments
        if (arguments.isEmpty()) return
        val argument = arguments[0]
        val evaluated = argument.evaluate() as? String
        if (evaluated == "android.hardware.camera") {
            val sourceText = argument.sourcePsi?.text ?: ""
            val fix = if (sourceText.contains("FEATURE_CAMERA")) {
                fix().replace()
                    .text("FEATURE_CAMERA")
                    .with("FEATURE_CAMERA_ANY")
                    .shortenNames()
                    .build()
            } else {
                fix().replace()
                    .with("\"android.hardware.camera.any\"")
                    .build()
            }

            context.report(
                ISSUE,
                argument,
                context.getLocation(argument),
                "Look for `FEATURE_CAMERA_ANY` instead of `FEATURE_CAMERA` to include all possible cameras",
                fix
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "UnsupportedChromeOsCameraSystemFeature",
            briefDescription = "Looking for Rear Camera only feature",
            explanation = """
                Looking for `FEATURE_CAMERA` only looks for a rear facing camera, which certain large screen devices don't have, as well as newer device configurations and modes may place the device in a state where the rear camera is not available. To fix the issue, look for `FEATURE_CAMERA_ANY` instead.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                ChromeOsSourceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}
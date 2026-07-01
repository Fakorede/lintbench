package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val UNSUPPORTED_CAMERA_FEATURE = Issue.create(
            id = "UnsupportedChromeOsCameraSystemFeature",
            briefDescription = "Looking for Rear Camera only feature",
            explanation = """
                You should look for the `FEATURE_CAMERA_ANY` features to include all possible \
                cameras that may be on the device. Looking for `FEATURE_CAMERA` only looks \
                for a rear facing camera, which certain large screen devices don't have, \
                as well as newer device configurations and modes may place the device \
                in a state where the rear camera is not available. To fix the issue, \
                look for `FEATURE_CAMERA_ANY` instead.
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

    override fun getApplicableMethodNames(): List<String> {
        return listOf("hasSystemFeature")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        if (!evaluator.isMemberInSubClassOf(method, "android.content.pm.PackageManager", false)) {
            return
        }

        val argument = node.valueArguments.firstOrNull() ?: return
        val evaluated = argument.evaluate()
        if (evaluated == "android.hardware.camera") {
            val location = context.getLocation(argument)
            val message = "Looking for `FEATURE_CAMERA` only looks for a rear facing camera, which certain " +
                    "large screen devices don't have. Look for `FEATURE_CAMERA_ANY` instead."
            context.report(
                Incident(UNSUPPORTED_CAMERA_FEATURE, argument, location, message)
            )
        }
    }
}
package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiField
import org.jetbrains.uast.UReferenceExpression

class ChromeOsSourceDetector : Detector(), UastScanner {
    override fun getApplicableReferenceNames(): List<String>? = listOf("FEATURE_CAMERA")

    override fun visitReferenceExpression(context: JavaContext, node: UReferenceExpression) {
        val resolved = node.resolve() as? PsiField ?: return
        if (resolved.qualifiedName == "android.content.pm.PackageManager.FEATURE_CAMERA") {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Use `FEATURE_CAMERA_ANY` instead of `FEATURE_CAMERA` to support devices without a rear-facing camera, such as Chromebooks and large-screen devices."
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "UnsupportedChromeOsCameraSystemFeature",
            briefDescription = "Looking for Rear Camera only feature",
            explanation = """
                You should look for the `FEATURE_CAMERA_ANY` features to include all possible cameras that may be on the device. \
                Looking for `FEATURE_CAMERA` only looks for a rear facing camera, which certain large screen devices don't have, \
                as well as newer device configurations and modes may place the device in a state where the rear camera is not available. \
                To fix the issue, look for `FEATURE_CAMERA_ANY` instead.
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
}
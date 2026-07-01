package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.UElementHandler
import com.intellij.psi.PsiField
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UReferenceExpression

class ChromeOsSourceDetector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UReferenceExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitReferenceExpression(node: UReferenceExpression) {
                val resolved = node.resolve() as? PsiField ?: return
                val containingClass = resolved.containingClass ?: return
                if (containingClass.qualifiedName == "android.content.pm.PackageManager" &&
                    resolved.name == "FEATURE_CAMERA") {
                    context.report(
                        ISSUE,
                        context.getLocation(node),
                        "Use `FEATURE_CAMERA_ANY` instead of `FEATURE_CAMERA` to support Chrome OS and large screen devices.",
                        LintFix.create()
                            .replace()
                            .text("FEATURE_CAMERA")
                            .with("FEATURE_CAMERA_ANY")
                            .build()
                    )
                }
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            "UnsupportedChromeOsCameraSystemFeature",
            "Use FEATURE_CAMERA_ANY instead of FEATURE_CAMERA",
            "Looking for `FEATURE_CAMERA` only looks for a rear facing camera, which certain large screen devices " +
            "don't have, as well as newer device configurations and modes may place the device in a state where " +
            "the rear camera is not available. To fix the issue, look for `FEATURE_CAMERA_ANY` instead.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            Implementation(ChromeOsSourceDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }
}
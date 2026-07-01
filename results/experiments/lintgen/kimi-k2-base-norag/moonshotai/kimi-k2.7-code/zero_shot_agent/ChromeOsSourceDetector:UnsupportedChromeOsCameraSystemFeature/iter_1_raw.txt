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
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import org.jetbrains.uast.UReferenceExpression

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

    override fun getApplicableReferenceNames(): List<String> = listOf("FEATURE_CAMERA")

    override fun visitReference(
        context: JavaContext,
        reference: UReferenceExpression,
        referenced: PsiElement
    ) {
        if (referenced !is PsiField) return

        val containingClass = referenced.containingClass ?: return
        if (containingClass.qualifiedName == "android.content.pm.PackageManager" &&
            referenced.name == "FEATURE_CAMERA"
        ) {
            val fix = LintFix.create()
                .replace()
                .text("FEATURE_CAMERA")
                .with("FEATURE_CAMERA_ANY")
                .build()

            context.report(
                ISSUE,
                reference,
                context.getLocation(reference),
                "Use `PackageManager.FEATURE_CAMERA_ANY` instead of `PackageManager.FEATURE_CAMERA`",
                fix
            )
        }
    }

    companion object {
        val ISSUE = Issue.create(
            id = "UnsupportedChromeOsCameraSystemFeature",
            briefDescription = "Looking for only a rear-facing camera may not work on all devices",
            explanation = """
                `PackageManager.FEATURE_CAMERA` only checks for a rear-facing camera. Many large screen
                devices, such as Chromebooks, do not have a rear-facing camera, and some newer device
                configurations or modes may make the rear camera unavailable.

                Use `PackageManager.FEATURE_CAMERA_ANY` instead to check for any camera on the device.

                For more information, see:
                https://developer.android.com/guide/topics/large-screens/large-screen-cookbook#chromebook_camera_support
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
}
package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import org.jetbrains.uast.UReferenceExpression

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

    override fun getApplicableReferenceNames(): List<String>? = listOf("FEATURE_CAMERA")

    override fun visitReference(context: JavaContext, reference: UReferenceExpression, referenced: PsiElement) {
        if (referenced is PsiField) {
            val containingClass = referenced.containingClass
            if (containingClass?.qualifiedName == "android.content.pm.PackageManager") {
                val fix = fix()
                    .replace()
                    .text("FEATURE_CAMERA")
                    .with("FEATURE_CAMERA_ANY")
                    .build()
                context.report(
                    ISSUE,
                    context.getLocation(reference),
                    "Use `FEATURE_CAMERA_ANY` instead of `FEATURE_CAMERA` to support devices without a rear-facing camera, such as Chromebooks and large-screen devices.",
                    fix
                )
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "UnsupportedChromeOsCameraSystemFeature",
            briefDescription = "Using `FEATURE_CAMERA` instead of `FEATURE_CAMERA_ANY`",
            explanation = "Looking for `FEATURE_CAMERA_ANY` features to include all possible cameras that may be on the device. Looking for `FEATURE_CAMERA` only looks for a rear facing camera, which certain large screen devices don't have, as well as newer device configurations and modes may place the device in a state where the rear camera is not available. To fix the issue, look for `FEATURE_CAMERA_ANY` instead.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(ChromeOsSourceDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }
}
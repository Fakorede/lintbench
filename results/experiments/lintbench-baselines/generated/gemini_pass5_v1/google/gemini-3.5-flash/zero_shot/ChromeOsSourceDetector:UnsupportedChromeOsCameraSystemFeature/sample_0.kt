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
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UReferenceExpression

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

    override fun getApplicableReferenceNames(): List<String>? {
        return listOf("FEATURE_CAMERA")
    }

    override fun visitReference(
        context: JavaContext,
        reference: UReferenceExpression,
        referenced: PsiElement
    ) {
        val evaluator = context.evaluator
        if (referenced is PsiField && evaluator.isMemberInClass(referenced, "android.content.pm.PackageManager")) {
            context.report(
                ISSUE,
                reference,
                context.getLocation(reference),
                "Look for `FEATURE_CAMERA_ANY` instead of `FEATURE_CAMERA` to support Chromebooks without a rear camera",
                fix().replace().with("android.content.pm.PackageManager.FEATURE_CAMERA_ANY").shortenNames().build()
            )
        }
    }

    override fun getApplicableMethodNames(): List<String>? {
        return listOf("hasSystemFeature")
    }

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        if (!context.evaluator.isMemberInSubclassOf(method, "android.content.pm.PackageManager", false)) {
            return
        }
        val argument = node.valueArguments.firstOrNull() ?: return
        val value = argument.evaluate() as? String ?: return
        if (value == "android.hardware.camera") {
            val argText = argument.sourcePsi?.text ?: ""
            if (argText.contains("FEATURE_CAMERA")) {
                return
            }
            context.report(
                ISSUE,
                argument,
                context.getLocation(argument),
                "Look for `FEATURE_CAMERA_ANY` instead of `FEATURE_CAMERA` to support Chromebooks without a rear camera",
                fix().replace().with("android.content.pm.PackageManager.FEATURE_CAMERA_ANY").shortenNames().build()
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "UnsupportedChromeOsCameraSystemFeature",
            briefDescription = "Looking for Rear Camera only feature",
            explanation = """
                Looking for `FEATURE_CAMERA` only looks for a rear facing camera, which \
                certain large screen devices don't have, as well as newer device \
                configurations and modes may place the device in a state where the \
                rear camera is not available. To fix the issue, look for \
                `FEATURE_CAMERA_ANY` instead.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = Implementation(
                ChromeOsSourceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}
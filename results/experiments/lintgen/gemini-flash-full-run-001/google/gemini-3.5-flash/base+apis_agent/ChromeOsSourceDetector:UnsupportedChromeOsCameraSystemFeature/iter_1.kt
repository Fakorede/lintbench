package com.android.tools.lint.checks

import com.android.resources.ResourceType
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.interprocedural.CallGraphResult
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UReferenceExpression
import org.w3c.dom.Node

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

    override fun getApplicableReferenceNames(): List<String>? {
        return listOf("FEATURE_CAMERA")
    }

    override fun visitReference(context: JavaContext, reference: UReferenceExpression, referenced: PsiElement) {
        if (referenced is PsiField) {
            val containingClass = referenced.containingClass
            if (containingClass?.qualifiedName == "android.content.pm.PackageManager") {
                val fix = fix()
                    .name("Switch to FEATURE_CAMERA_ANY")
                    .replace()
                    .text("FEATURE_CAMERA")
                    .with("FEATURE_CAMERA_ANY")
                    .build()

                context.report(
                    ISSUE,
                    reference,
                    context.getLocation(reference),
                    "Use `PackageManager.FEATURE_CAMERA_ANY` instead of `PackageManager.FEATURE_CAMERA` to support Chromebooks and other devices without a rear camera",
                    fix
                )
            }
        }
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>>? {
        return listOf(ULiteralExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitLiteralExpression(node: ULiteralExpression) {
                val value = node.value
                if (value == "android.hardware.camera") {
                    val fix = fix()
                        .name("Switch to \"android.hardware.camera.any\"")
                        .replace()
                        .all()
                        .with("\"android.hardware.camera.any\"")
                        .build()

                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Use `\"android.hardware.camera.any\"` instead of `\"android.hardware.camera\"` to support Chromebooks and other devices without a rear camera",
                        fix
                    )
                }
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "UnsupportedChromeOsCameraSystemFeature",
            briefDescription = "Looking for Rear Camera only feature",
            explanation = """
                Looking for `FEATURE_CAMERA_ANY` features to include all possible cameras that may be on the device. \
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
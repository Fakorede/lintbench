package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import org.jetbrains.uast.UReferenceExpression
import org.w3c.dom.Element

class ChromeOsSourceDetector : Detector(), SourceCodeScanner, XmlScanner {

    companion object {
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"

        @JvmField
        val ISSUE = Issue.create(
            id = "UnsupportedChromeOsCameraSystemFeature",
            briefDescription = "Using FEATURE_CAMERA instead of FEATURE_CAMERA_ANY",
            explanation = """
                Looking for `FEATURE_CAMERA` only looks for a rear facing camera, which certain \
                large screen devices don't have, as well as newer device configurations and modes \
                may place the device in a state where the rear camera is not available. To fix the \
                issue, look for `FEATURE_CAMERA_ANY` instead.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                ChromeOsSourceDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
                Scope.MANIFEST_SCOPE
            )
        )
    }

    override fun getApplicableReferenceNames(): List<String>? {
        return listOf("FEATURE_CAMERA")
    }

    override fun visitReference(context: JavaContext, reference: UReferenceExpression, referenced: PsiElement) {
        if (referenced is PsiField) {
            val containingClass = referenced.containingClass
            if (containingClass?.qualifiedName == "android.content.pm.PackageManager" &&
                referenced.name == "FEATURE_CAMERA") {
                context.report(
                    ISSUE,
                    context.getLocation(reference),
                    "Use `FEATURE_CAMERA_ANY` instead of `FEATURE_CAMERA` to support Chrome OS and large screen devices."
                )
            }
        }
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("uses-feature")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val name = element.getAttributeNS(ANDROID_URI, "name")
        if (name == "android.hardware.camera") {
            context.report(
                ISSUE,
                context.getLocation(element),
                "Use `android.hardware.camera.any` instead of `android.hardware.camera` to support Chrome OS and large screen devices."
            )
        }
    }
}
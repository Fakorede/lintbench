package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import org.jetbrains.uast.UReferenceExpression
import org.w3c.dom.Attr

class ChromeOsSourceDetector : Detector(), SourceCodeScanner, XmlScanner {

    override fun getApplicableReferenceNames(): List<String> = listOf(FEATURE_CAMERA)

    override fun visitReference(
        context: JavaContext,
        reference: UReferenceExpression,
        referenced: PsiElement
    ) {
        if (referenced is PsiField &&
            referenced.containingClass?.qualifiedName == PACKAGE_MANAGER
        ) {
            context.report(
                ISSUE,
                reference,
                context.getLocation(reference),
                MESSAGE
            )
        }
    }

    override fun getApplicableAttributes(): Collection<String> = listOf("name")

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        if (attribute.name == "android:name" &&
            attribute.ownerElement.tagName == USES_FEATURE &&
            attribute.value == FEATURE_CAMERA_NAME
        ) {
            context.report(
                ISSUE,
                attribute,
                context.getValueLocation(attribute),
                MESSAGE
            )
        }
    }

    companion object {
        private const val FEATURE_CAMERA = "FEATURE_CAMERA"
        private const val FEATURE_CAMERA_NAME = "android.hardware.camera"
        private const val PACKAGE_MANAGER = "android.content.pm.PackageManager"
        private const val USES_FEATURE = "uses-feature"
        private const val MESSAGE =
            "Use FEATURE_CAMERA_ANY instead of FEATURE_CAMERA to support devices that may not have a rear-facing camera, such as Chromebooks."

        val ISSUE = Issue.create(
            id = "UnsupportedChromeOsCameraSystemFeature",
            briefDescription = "Looking for Rear Camera only feature",
            explanation = """
                The `PackageManager.FEATURE_CAMERA` constant (and the `android.hardware.camera`
                manifest feature) indicates a rear-facing camera only. Many large-screen devices
                such as Chromebooks do not have a rear camera, and other device modes may make
                the rear camera unavailable. Use `PackageManager.FEATURE_CAMERA_ANY` (or
                `android.hardware.camera.any`) to check for any camera.
            """,
            category = Category.CHROME_OS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                ChromeOsSourceDetector::class.java,
                Scope.ALL
            )
        )
    }
}
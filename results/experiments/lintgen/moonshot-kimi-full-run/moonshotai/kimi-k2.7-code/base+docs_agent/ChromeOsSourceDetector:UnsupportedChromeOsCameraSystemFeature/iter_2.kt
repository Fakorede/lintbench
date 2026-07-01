package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
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
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UReferenceExpression
import org.w3c.dom.Element

class ChromeOsSourceDetector : Detector(), SourceCodeScanner, XmlScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "UnsupportedChromeOsCameraSystemFeature",
            briefDescription = "Looking for Rear Camera only feature",
            explanation = """
                You should look for the `FEATURE_CAMERA_ANY` features to include all possible
                cameras that may be on the device. Looking for `FEATURE_CAMERA` only looks
                for a rear facing camera, which certain large screen devices don't have, as
                well as newer device configurations and modes may place the device in a state
                where the rear camera is not available. To fix the issue, look for
                `FEATURE_CAMERA_ANY` instead.
            """.trimIndent(),
            moreInfo = "https://developer.android.com/guide/topics/large-screens/large-screen-cookbook#chromebook_camera_support",
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = Implementation(
                ChromeOsSourceDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
                Scope.MANIFEST_SCOPE
            )
        )

        private const val FEATURE_CAMERA = "android.hardware.camera"
        private const val FEATURE_CAMERA_ANY = "android.hardware.camera.any"
        private const val PACKAGE_MANAGER = "android.content.pm.PackageManager"
    }

    override fun getApplicableReferenceNames(): List<String> = listOf("FEATURE_CAMERA")

    override fun visitReference(context: JavaContext, node: UReferenceExpression, referenced: PsiElement) {
        val field = referenced as? PsiField ?: return
        if (field.name == "FEATURE_CAMERA" && field.containingClass?.qualifiedName == PACKAGE_MANAGER) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Use `PackageManager.FEATURE_CAMERA_ANY` instead of `PackageManager.FEATURE_CAMERA`"
            )
        }
    }

    override fun getApplicableMethodNames(): List<String> = listOf("hasSystemFeature")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (method.containingClass?.qualifiedName != PACKAGE_MANAGER) return
        for (arg in node.valueArguments) {
            if (arg is ULiteralExpression && arg.value == FEATURE_CAMERA) {
                context.report(
                    ISSUE,
                    arg,
                    context.getLocation(arg),
                    "Use `$FEATURE_CAMERA_ANY` instead of `$FEATURE_CAMERA`"
                )
            }
        }
    }

    override fun getApplicableElements(): Collection<String> = listOf("uses-feature")

    override fun visitElement(context: XmlContext, element: Element) {
        val name = element.getAttributeNS(ANDROID_URI, "name")
        if (name == FEATURE_CAMERA) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Use `$FEATURE_CAMERA_ANY` instead of `$FEATURE_CAMERA`"
            )
        }
    }
}
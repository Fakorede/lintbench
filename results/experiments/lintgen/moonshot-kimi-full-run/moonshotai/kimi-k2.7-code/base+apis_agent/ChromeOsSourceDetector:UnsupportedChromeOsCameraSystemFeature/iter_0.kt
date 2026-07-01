package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiField
import org.jetbrains.uast.UReferenceExpression
import org.w3c.dom.Attr

class ChromeOsSourceDetector : Detector(), SourceCodeScanner, XmlScanner {

    override fun getApplicableUElementTypes() = listOf(UReferenceExpression::class.java)

    override fun createUElementHandler(context: JavaContext) = object : UElementHandler() {
        override fun visitReferenceExpression(node: UReferenceExpression) {
            val resolved = node.resolve() as? PsiField ?: return
            if (resolved.name == FEATURE_CAMERA &&
                resolved.containingClass?.qualifiedName == "android.content.pm.PackageManager"
            ) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    MESSAGE
                )
            }
        }
    }

    override fun getApplicableAttributes(): Collection<String> = listOf("android:name")

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        if (attribute.value == FEATURE_CAMERA_NAME) {
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
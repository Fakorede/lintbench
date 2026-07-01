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
import com.intellij.psi.PsiField
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UElementHandler
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

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(
        UReferenceExpression::class.java,
        ULiteralExpression::class.java
    )

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitReferenceExpression(node: UReferenceExpression) {
                val resolved = node.resolve() as? PsiField ?: return
                val containingClass = resolved.containingClass ?: return
                if (containingClass.qualifiedName == PACKAGE_MANAGER &&
                    resolved.name == "FEATURE_CAMERA"
                ) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Use `PackageManager.FEATURE_CAMERA_ANY` instead of `PackageManager.FEATURE_CAMERA`"
                    )
                }
            }

            override fun visitLiteralExpression(node: ULiteralExpression) {
                if (node.value == FEATURE_CAMERA) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Use `$FEATURE_CAMERA_ANY` instead of `$FEATURE_CAMERA`"
                    )
                }
            }
        }
    }

    override fun getApplicableElements(): Collection<String> = listOf("uses-feature")

    override fun visitElement(context: XmlContext, element: Element) {
        val name = element.getAttributeNS(ANDROID_URI, "name")
        if (name.isEmpty()) return
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
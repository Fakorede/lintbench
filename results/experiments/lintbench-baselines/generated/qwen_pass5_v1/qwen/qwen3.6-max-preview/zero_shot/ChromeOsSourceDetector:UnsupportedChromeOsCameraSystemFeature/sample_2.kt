package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiField
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UElement
import org.w3c.dom.Element
import java.util.EnumSet

class ChromeOsSourceDetector : Detector(), Detector.UastScanner, Detector.XmlScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "UnsupportedChromeOsCameraSystemFeature",
            briefDescription = "Using FEATURE_CAMERA instead of FEATURE_CAMERA_ANY",
            explanation = """
                Looking for Rear Camera only feature. You should look for the `FEATURE_CAMERA_ANY` features to include all possible cameras that may be on the device. Looking for `FEATURE_CAMERA` only looks for a rear facing camera, which certain large screen devices don't have, as well as newer device configurations and modes may place the device in a state where the rear camera is not available. To fix the issue, look for `FEATURE_CAMERA_ANY` instead.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                ChromeOsSourceDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST)
            )
        )
    }

    override fun getIssues(): List<Issue> = listOf(ISSUE)

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UReferenceExpression::class.java, ULiteralExpression::class.java)
    }

    override fun visitReferenceExpression(context: JavaContext, node: UReferenceExpression) {
        val resolved = node.resolve()
        if (resolved is PsiField) {
            val cls = resolved.containingClass?.qualifiedName
            if (cls == "android.content.pm.PackageManager" && resolved.name == "FEATURE_CAMERA") {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Use `FEATURE_CAMERA_ANY` instead of `FEATURE_CAMERA` to support devices without a rear camera."
                )
            }
        }
    }

    override fun visitLiteralExpression(context: JavaContext, node: ULiteralExpression) {
        if (node.value == "android.hardware.camera") {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Use `android.hardware.camera.any` instead of `android.hardware.camera` to support devices without a rear camera."
            )
        }
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("uses-feature")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val name = element.getAttributeNS(SdkConstants.ANDROID_URI, "name")
        if (name == "android.hardware.camera") {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Use `android.hardware.camera.any` instead of `android.hardware.camera` to support devices without a rear camera."
            )
        }
    }
}
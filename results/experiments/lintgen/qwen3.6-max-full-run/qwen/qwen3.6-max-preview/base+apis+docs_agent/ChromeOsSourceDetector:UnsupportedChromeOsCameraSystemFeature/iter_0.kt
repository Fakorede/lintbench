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
import com.android.utils.SdkConstants
import com.intellij.psi.PsiField
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UElementHandler
import org.w3c.dom.Element

class ChromeOsSourceDetector : Detector(), SourceCodeScanner, XmlScanner {

    companion object {
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

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UReferenceExpression::class.java, ULiteralExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitReferenceExpression(node: UReferenceExpression) {
                val resolved = node.resolve()
                if (resolved is PsiField) {
                    val containingClass = resolved.containingClass
                    if (containingClass?.qualifiedName == "android.content.pm.PackageManager" &&
                        resolved.name == "FEATURE_CAMERA") {
                        context.report(
                            ISSUE,
                            context.getLocation(node),
                            "Use `FEATURE_CAMERA_ANY` instead of `FEATURE_CAMERA` to support Chrome OS and large screen devices."
                        )
                    }
                }
            }

            override fun visitLiteralExpression(node: ULiteralExpression) {
                if (node.value == "android.hardware.camera") {
                    context.report(
                        ISSUE,
                        context.getLocation(node),
                        "Use `android.hardware.camera.any` instead of `android.hardware.camera` to support Chrome OS and large screen devices."
                    )
                }
            }
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
                context.getLocation(element),
                "Use `android.hardware.camera.any` instead of `android.hardware.camera` to support Chrome OS and large screen devices."
            )
        }
    }
}
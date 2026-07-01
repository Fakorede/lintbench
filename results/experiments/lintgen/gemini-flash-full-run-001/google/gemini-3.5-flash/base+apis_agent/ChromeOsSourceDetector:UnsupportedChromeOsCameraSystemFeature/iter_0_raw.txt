package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiField
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UReferenceExpression

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UReferenceExpression::class.java, ULiteralExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitReferenceExpression(node: UReferenceExpression) {
                val resolved = node.resolve()
                if (resolved is PsiField) {
                    val containingClass = resolved.containingClass
                    if (containingClass?.qualifiedName == "android.content.pm.PackageManager" &&
                        resolved.name == "FEATURE_CAMERA"
                    ) {
                        val fix = fix()
                            .name("Switch to FEATURE_CAMERA_ANY")
                            .replace()
                            .pattern("(FEATURE_CAMERA)")
                            .with("FEATURE_CAMERA_ANY")
                            .build()

                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Use `PackageManager.FEATURE_CAMERA_ANY` instead of `PackageManager.FEATURE_CAMERA` to support Chromebooks and other devices without a rear camera",
                            fix
                        )
                    }
                }
            }

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
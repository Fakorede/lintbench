package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiField
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.USimpleNameReferenceExpression

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(ULiteralExpression::class.java, USimpleNameReferenceExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitLiteralExpression(node: ULiteralExpression) {
                val value = node.value
                if (value == "android.hardware.camera") {
                    val fix = LintFix.create()
                        .replace()
                        .text("\"android.hardware.camera\"")
                        .with("\"android.hardware.camera.any\"")
                        .build()

                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Look for `FEATURE_CAMERA_ANY` instead of `FEATURE_CAMERA` to support Chromebooks and devices without a rear camera",
                        fix
                    )
                }
            }

            override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) {
                if (node.identifier == "FEATURE_CAMERA") {
                    val resolved = node.resolve()
                    if (resolved is PsiField) {
                        val containingClass = resolved.containingClass
                        if (containingClass?.qualifiedName == "android.content.pm.PackageManager") {
                            val fix = LintFix.create()
                                .replace()
                                .text("FEATURE_CAMERA")
                                .with("FEATURE_CAMERA_ANY")
                                .build()

                            context.report(
                                ISSUE,
                                node,
                                context.getLocation(node),
                                "Look for `FEATURE_CAMERA_ANY` instead of `FEATURE_CAMERA` to support Chromebooks and devices without a rear camera",
                                fix
                            )
                        }
                    }
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
                Looking for `FEATURE_CAMERA` only looks for a rear facing camera, which certain \
                large screen devices don't have, as well as newer device configurations and modes \
                may place the device in a state where the rear camera is not available. To fix the \
                issue, look for `FEATURE_CAMERA_ANY` instead.
                """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                ChromeOsSourceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}
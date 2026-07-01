package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiField
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UReferenceExpression

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {
    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UReferenceExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitReferenceExpression(node: UReferenceExpression) {
                val resolved = node.resolve() as? PsiField ?: return
                val containingClass = resolved.containingClass ?: return
                if (containingClass.qualifiedName == "android.content.pm.PackageManager" && resolved.name == "FEATURE_CAMERA") {
                    val fix = fix()
                        .replace()
                        .text("FEATURE_CAMERA")
                        .with("FEATURE_CAMERA_ANY")
                        .build()
                    context.report(
                        ISSUE,
                        context.getLocation(node),
                        "Use `FEATURE_CAMERA_ANY` instead of `FEATURE_CAMERA` to support devices without a rear-facing camera, such as Chromebooks and large-screen devices.",
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
            briefDescription = "Using `FEATURE_CAMERA` instead of `FEATURE_CAMERA_ANY`",
            explanation = "Looking for `FEATURE_CAMERA_ANY` features to include all possible cameras that may be on the device. Looking for `FEATURE_CAMERA` only looks for a rear facing camera, which certain large screen devices don't have, as well as newer device configurations and modes may place the device in a state where the rear camera is not available. To fix the issue, look for `FEATURE_CAMERA_ANY` instead.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(ChromeOsSourceDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }
}
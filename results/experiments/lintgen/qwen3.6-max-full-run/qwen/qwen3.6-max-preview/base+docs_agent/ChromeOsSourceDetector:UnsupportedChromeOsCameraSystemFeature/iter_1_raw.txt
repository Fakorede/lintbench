package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiField
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.visitor.AbstractUastVisitor
import org.jetbrains.uast.visitor.UastVisitor

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UReferenceExpression::class.java)

    override fun createUastVisitor(context: JavaContext): UastVisitor {
        return object : AbstractUastVisitor() {
            override fun visitReferenceExpression(node: UReferenceExpression): Boolean {
                val resolved = node.resolve() as? PsiField ?: return super.visitReferenceExpression(node)
                val containingClass = resolved.containingClass ?: return super.visitReferenceExpression(node)
                if (containingClass.qualifiedName == "android.content.pm.PackageManager" &&
                    resolved.name == "FEATURE_CAMERA") {
                    context.report(
                        ISSUE,
                        context.getLocation(node),
                        "Use `FEATURE_CAMERA_ANY` instead of `FEATURE_CAMERA` to support Chrome OS and large screen devices.",
                        fix()
                            .replace()
                            .name("Replace with FEATURE_CAMERA_ANY")
                            .text("FEATURE_CAMERA")
                            .with("FEATURE_CAMERA_ANY")
                            .build()
                    )
                }
                return super.visitReferenceExpression(node)
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            "UnsupportedChromeOsCameraSystemFeature",
            "Use FEATURE_CAMERA_ANY instead of FEATURE_CAMERA",
            "Looking for `FEATURE_CAMERA` only looks for a rear facing camera, which certain large screen devices " +
            "don't have, as well as newer device configurations and modes may place the device in a state where " +
            "the rear camera is not available. To fix the issue, look for `FEATURE_CAMERA_ANY` instead.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            Implementation(ChromeOsSourceDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }
}
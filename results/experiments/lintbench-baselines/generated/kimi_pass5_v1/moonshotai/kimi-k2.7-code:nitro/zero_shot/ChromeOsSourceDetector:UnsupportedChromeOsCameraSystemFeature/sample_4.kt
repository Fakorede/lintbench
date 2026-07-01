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
import org.jetbrains.uast.UReferenceExpression

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "UnsupportedChromeOsCameraSystemFeature",
            briefDescription = "Looking for rear camera only feature",
            explanation = """
                Using `PackageManager.FEATURE_CAMERA` only checks for a rear-facing camera. \
                Certain large screen devices, such as Chromebooks, may not have a rear-facing \
                camera, and newer device configurations may make it unavailable. Use \
                `PackageManager.FEATURE_CAMERA_ANY` instead to check for any available camera.
            """.trimIndent(),
            category = Category.CHROME_OS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                ChromeOsSourceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private const val PACKAGE_MANAGER_CLASS = "android.content.pm.PackageManager"
        private const val FEATURE_CAMERA = "FEATURE_CAMERA"
        private const val FEATURE_CAMERA_ANY = "FEATURE_CAMERA_ANY"
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>>? {
        return listOf(UReferenceExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitReferenceExpression(node: UReferenceExpression) {
                val resolved = node.resolve() as? PsiField ?: return
                if (resolved.name != FEATURE_CAMERA) return
                if (!context.evaluator.isMemberInClass(resolved, PACKAGE_MANAGER_CLASS)) return

                val fix: LintFix = LintFix.create()
                    .replace()
                    .text(FEATURE_CAMERA)
                    .with(FEATURE_CAMERA_ANY)
                    .robot(false)
                    .independent(true)
                    .build()

                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Looking for rear camera only feature; use `PackageManager.FEATURE_CAMERA_ANY` instead",
                    fix
                )
            }
        }
    }
}
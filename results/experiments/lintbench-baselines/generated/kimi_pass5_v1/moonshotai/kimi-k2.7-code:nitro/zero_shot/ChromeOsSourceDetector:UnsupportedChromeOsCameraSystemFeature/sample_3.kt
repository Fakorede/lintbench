package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiField
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UReferenceExpression

class ChromeOsSourceDetector : Detector(), Detector.SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>>? {
        return listOf(UReferenceExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitReferenceExpression(node: UReferenceExpression) {
                val field = node.resolve() as? PsiField ?: return
                if (field.containingClass?.qualifiedName == PACKAGE_MANAGER &&
                    field.name == FEATURE_CAMERA
                ) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Use `PackageManager.FEATURE_CAMERA_ANY` instead of `PackageManager.FEATURE_CAMERA`; " +
                            "the latter only indicates a rear-facing camera, which may not be available on all large screen devices."
                    )
                }
            }
        }
    }

    companion object {
        private const val FEATURE_CAMERA = "FEATURE_CAMERA"
        private const val PACKAGE_MANAGER = "android.content.pm.PackageManager"

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "UnsupportedChromeOsCameraSystemFeature",
            briefDescription = "Looking for Rear Camera only feature",
            explanation = """
                You should look for the `FEATURE_CAMERA_ANY` features to include all possible cameras that may be on the device. Looking for `FEATURE_CAMERA` only looks for a rear facing camera, which certain large screen devices don't have, as well as newer device configurations and modes may place the device in a state where the rear camera is not available. To fix the issue, look for `FEATURE_CAMERA_ANY` instead.
            """.trimIndent(),
            moreInfo = "https://developer.android.com/guide/topics/large-screens/large-screen-cookbook#chromebook_camera_support",
            category = Category.CHROME_OS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                ChromeOsSourceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}
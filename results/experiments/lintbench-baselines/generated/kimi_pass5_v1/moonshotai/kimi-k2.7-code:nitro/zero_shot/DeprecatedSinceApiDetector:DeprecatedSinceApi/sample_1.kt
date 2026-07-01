package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.UElementHandler
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class DeprecatedSinceApiDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val DEPRECATED_SINCE_API = "androidx.annotation.DeprecatedSinceApi"

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "DeprecatedSinceApi",
            briefDescription = "Using a method deprecated in earlier SDK",
            explanation = """
                Some backport methods are only necessary until a specific version of Android.
                These have been annotated with `@DeprecatedSinceApi`, specifying the relevant API level and replacement suggestions.
                Calling these methods when the `minSdkVersion` is already at the deprecated API level or above is unnecessary.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                DeprecatedSinceApiDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableUastTypes() = listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                val method = node.resolve() as? PsiMethod ?: return
                val annotation = method.modifierList.findAnnotation(DEPRECATED_SINCE_API) ?: return

                val helper = JavaPsiFacade.getInstance(method.project).constantEvaluationHelper
                val apiAttribute = annotation.findAttributeValue("api") ?: return
                val apiLevel = helper.computeConstantExpression(apiAttribute) as? Int ?: return

                val minSdk = context.mainProject.minSdk
                if (minSdk < apiLevel) return

                val replacementAttribute = annotation.findAttributeValue("replacement")
                val replacement = replacementAttribute?.let {
                    helper.computeConstantExpression(it) as? String
                }

                val message = buildString {
                    append("This call is unnecessary because the callee is deprecated since API $apiLevel")
                    append(" while minSdkVersion is $minSdk")
                    if (!replacement.isNullOrBlank()) {
                        append(". Consider using $replacement instead")
                    }
                }

                context.report(ISSUE, node, context.getLocation(node), message)
            }
        }
    }
}
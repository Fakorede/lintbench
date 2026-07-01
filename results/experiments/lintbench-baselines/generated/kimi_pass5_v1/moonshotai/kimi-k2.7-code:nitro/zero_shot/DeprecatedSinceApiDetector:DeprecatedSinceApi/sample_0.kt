package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement

class DeprecatedSinceApiDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                val method = node.resolve() ?: return
                checkMethod(context, node, method)
            }
        }
    }

    private fun checkMethod(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val annotation = context.evaluator.getAnnotation(method, DEPRECATED_SINCE_API) ?: return
        val apiLevel = annotation.findAttributeValue("api")?.let { attr ->
            ConstantEvaluator.evaluate(context, attr) as? Number
        }?.toInt() ?: return

        if (context.project.minSdkVersion < apiLevel) {
            return
        }

        val replacement = annotation.findAttributeValue("replacement")?.let { attr ->
            ConstantEvaluator.evaluate(context, attr) as? String
        }

        val message = buildString {
            append("This call is to a method that is deprecated for apps with a minSdkVersion of $apiLevel or higher")
            if (!replacement.isNullOrEmpty()) {
                append("; consider using $replacement")
            }
        }

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            message
        )
    }

    companion object {
        private const val DEPRECATED_SINCE_API = "androidx.annotation.DeprecatedSinceApi"

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "DeprecatedSinceApi",
            briefDescription = "Using a method deprecated in earlier SDK",
            explanation = """
                Some backport methods are only necessary until a specific version of Android. \
                These methods are annotated with `@DeprecatedSinceApi`, which specifies the API \
                level at which they become unnecessary and a suggested replacement. Calling them \
                when the `minSdkVersion` is already at the deprecated API level or above is \
                unnecessary.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = Implementation(
                DeprecatedSinceApiDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}
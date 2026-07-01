package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiAnnotation
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UElementHandler

class DeprecatedSinceApiDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUElementTypes(): List<Class<out UElement>> {
        return listOf(UCallExpression::class.java)
    }

    override fun createUElementHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                val method = node.resolve() ?: return
                val annotation = context.evaluator.findAnnotation(
                    method,
                    "androidx.annotation.DeprecatedSinceApi"
                ) ?: return

                val api = getIntAttribute(annotation, "api") ?: return
                if (api > context.project.minSdkVersion) {
                    return
                }

                val methodName = method.name ?: "this method"
                val message = buildString {
                    append("Method `$methodName` is deprecated since API level $api")
                    append(" and is unnecessary when minSdkVersion is ")
                    append(context.project.minSdkVersion)
                    append(" or higher")
                    val replacement = getStringAttribute(annotation, "message")
                    if (!replacement.isNullOrEmpty()) {
                        append("; suggested replacement: $replacement")
                    }
                    append(".")
                }

                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    message
                )
            }
        }
    }

    private fun getIntAttribute(annotation: PsiAnnotation, name: String): Int? {
        val value = annotation.findAttributeValue(name) ?: return null
        return ConstantEvaluator.evaluate(value) as? Int
    }

    private fun getStringAttribute(annotation: PsiAnnotation, name: String): String? {
        val value = annotation.findAttributeValue(name) ?: return null
        return ConstantEvaluator.evaluate(value) as? String
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "DeprecatedSinceApi",
            briefDescription = "Method deprecated in earlier SDK",
            explanation = """
                Methods annotated with @DeprecatedSinceApi are backports that are only needed \
                below the specified API level. Calling them when minSdkVersion is already at or \
                above that level is unnecessary.
            """,
            category = Category.PERFORMANCE,
            priority = 4,
            severity = Severity.WARNING,
            implementation = Implementation(
                DeprecatedSinceApiDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}
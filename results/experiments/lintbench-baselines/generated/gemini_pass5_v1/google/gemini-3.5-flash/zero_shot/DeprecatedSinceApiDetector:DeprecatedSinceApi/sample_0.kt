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
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.visitor.AbstractUastVisitor

class DeprecatedSinceApiDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UCallExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): AbstractUastVisitor {
        return object : AbstractUastVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
                val method = node.resolve() ?: return false
                
                val annotation = findDeprecatedSinceApiAnnotation(method) ?: return false

                val apiValue = annotation.findAttributeValue("api")?.let {
                    ConstantEvaluator.evaluate(context, it) as? Int
                } ?: annotation.findAttributeValue("value")?.let {
                    ConstantEvaluator.evaluate(context, it) as? Int
                } ?: return false

                val replacementValue = annotation.findAttributeValue("replacement")?.let {
                    ConstantEvaluator.evaluate(context, it) as? String
                }

                val minSdk = context.project.minSdkVersion.featureLevel
                if (minSdk >= apiValue) {
                    val message = buildString {
                        append("This method is deprecated since API level $apiValue (minSdkVersion is $minSdk)")
                        if (!replacementValue.isNullOrBlank()) {
                            append("; use `$replacementValue` instead")
                        }
                    }
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        message
                    )
                }
                return false
            }
        }
    }

    private fun findDeprecatedSinceApiAnnotation(method: PsiMethod): PsiAnnotation? {
        return method.annotations.firstOrNull { annotation ->
            val qualifiedName = annotation.qualifiedName ?: return@firstOrNull false
            qualifiedName == "androidx.annotation.DeprecatedSinceApi" || 
                qualifiedName.endsWith(".DeprecatedSinceApi")
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "DeprecatedSinceApi",
            briefDescription = "Using a method deprecated in earlier SDK",
            explanation = """
                Some backport methods are only necessary until a specific version of Android. \
                These have been annotated with `@DeprecatedSinceApi`, specifying the relevant API \
                level and replacement suggestions. Calling these methods when the `minSdkVersion` \
                is already at the deprecated API level or above is unnecessary.
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
}
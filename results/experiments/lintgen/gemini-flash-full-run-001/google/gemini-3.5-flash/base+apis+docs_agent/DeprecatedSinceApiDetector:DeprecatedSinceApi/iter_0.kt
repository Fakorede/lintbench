package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiAnnotation
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement

class DeprecatedSinceApiDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "DeprecatedSinceApi",
            briefDescription = "Using a method deprecated in earlier SDK",
            explanation = """
                Some backport methods are only necessary until a specific version of Android. \
                These have been annotated with `@DeprecatedSinceApi`, specifying the relevant API level \
                and replacement suggestions. Calling these methods when the `minSdkVersion` is already \
                at the deprecated API level or above is unnecessary.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                DeprecatedSinceApiDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private const val DEPRECATED_SINCE_API_ANNOTATION = "androidx.annotation.DeprecatedSinceApi"
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UCallExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                val method = node.resolve() ?: return
                val annotation = method.getAnnotation(DEPRECATED_SINCE_API_ANNOTATION) ?: return
                
                val apiValue = annotation.findAttributeValue("api") ?: return
                val api = ConstantEvaluator.evaluate(context, apiValue) as? Int ?: return
                
                val minSdk = context.project.minSdkVersion.apiLevel
                if (minSdk >= api) {
                    val suggestedValue = annotation.findAttributeValue("suggested")
                    val suggested = ConstantEvaluator.evaluate(context, suggestedValue) as? String
                    
                    val message = if (!suggested.isNullOrEmpty()) {
                        "This method is deprecated since API level $api; use `$suggested` instead"
                    } else {
                        "This method is deprecated since API level $api; minSdkVersion is $minSdk"
                    }
                    
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        message,
                        null
                    )
                }
            }
        }
    }
}
package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UElement
import org.jetbrains.uast.tryResolve

class DeprecatedSinceApiDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "DeprecatedSinceApi",
            briefDescription = "Using a method deprecated in earlier SDK",
            explanation = """
                Some backport methods are only necessary until a specific version of Android. These have been 
                annotated with `@DeprecatedSinceApi`, specifying the relevant API level and replacement suggestions. 
                Calling these methods when the `minSdkVersion` is already at the deprecated API level or above is unnecessary.
            """,
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

    override fun applicableAnnotations(): List<String> = listOf(DEPRECATED_SINCE_API_ANNOTATION)

    override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean = true

    override fun visitAnnotationUsage(
        context: JavaContext,
        usage: UElement,
        type: AnnotationUsageType,
        annotation: UAnnotation
    ) {
        val apiValue = annotation.findAttributeValue("api") ?: return
        val api = (ConstantEvaluator.evaluate(context, apiValue) as? Number)?.toInt() ?: return

        val suggestedValue = annotation.findAttributeValue("suggested")
        val suggested = ConstantEvaluator.evaluate(context, suggestedValue) as? String

        val minSdkVersion = context.project.minSdkVersion
        if (minSdkVersion.featureLevel >= api) {
            val referenced = usage.tryResolve()
            val name = when (referenced) {
                is PsiMethod -> referenced.name
                is PsiClass -> referenced.name
                is PsiField -> referenced.name
                else -> "This API"
            }

            val message = if (!suggested.isNullOrEmpty()) {
                "Use of `$name` is unnecessary since `minSdkVersion` is $minSdkVersion. Use `$suggested` instead."
            } else {
                "Use of `$name` is unnecessary since `minSdkVersion` is $minSdkVersion."
            }

            context.report(
                Incident(ISSUE, usage, context.getLocation(usage), message)
            )
        }
    }
}
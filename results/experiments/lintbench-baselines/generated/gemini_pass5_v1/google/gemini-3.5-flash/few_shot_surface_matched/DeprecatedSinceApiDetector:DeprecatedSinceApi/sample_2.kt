package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UElement

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
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(DeprecatedSinceApiDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }

    override fun applicableAnnotations(): List<String> {
        return listOf("androidx.annotation.DeprecatedSinceApi")
    }

    override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean {
        return true
    }

    override fun visitAnnotationUsage(
        context: JavaContext,
        usage: UElement,
        type: AnnotationUsageType,
        annotation: UAnnotation,
        qualifiedName: String,
        method: PsiMethod?,
        referenced: PsiElement?,
        annotations: List<UAnnotation>,
        allMemberAnnotations: List<UAnnotation>,
        allClassAnnotations: List<UAnnotation>,
        allPackageAnnotations: List<UAnnotation>
    ) {
        if (qualifiedName != "androidx.annotation.DeprecatedSinceApi") {
            return
        }
        val apiExpression = annotation.findAttributeValue("api") ?: return
        val apiValue = (apiExpression.evaluate() as? Number)?.toInt() ?: return
        val minSdkVersion = context.project.minSdkVersion.featureLevel
        if (minSdkVersion >= apiValue) {
            val messageExpression = annotation.findAttributeValue("message")
            val messageValue = messageExpression?.evaluate() as? String
            val msg = if (!messageValue.isNullOrBlank()) {
                "This method is deprecated since API level $apiValue (current min is $minSdkVersion): $messageValue"
            } else {
                "This method is deprecated since API level $apiValue (current min is $minSdkVersion)"
            }
            context.report(Incident(ISSUE, usage, context.getLocation(usage), msg))
        }
    }
}
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
import org.jetbrains.uast.evaluate

class DeprecatedSinceApiDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "DeprecatedSinceApi",
            briefDescription = "Using a method deprecated in earlier SDK",
            explanation = """
                Some backport methods are only necessary until a specific version of Android. \
                These have been annotated with `@DeprecatedSinceApi`, specifying the relevant \
                API level and replacement suggestions. Calling these methods when the \
                `minSdkVersion` is already at the deprecated API level or above is unnecessary.
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(DeprecatedSinceApiDetector::class.java, Scope.JAVA_FILE_SCOPE),
        )
    }

    override fun applicableAnnotations(): List<String> = listOf("androidx.annotation.DeprecatedSinceApi")

    override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean =
        type == AnnotationUsageType.METHOD_CALL

    override fun visitAnnotationUsage(
        context: JavaContext,
        element: UElement,
        type: AnnotationUsageType,
        annotation: UAnnotation,
        method: PsiMethod?,
        annotated: PsiElement?,
        allMemberAnnotations: List<UAnnotation>,
        allClassAnnotations: List<UAnnotation>,
        allPackageAnnotations: List<UAnnotation>
    ) {
        if (type != AnnotationUsageType.METHOD_CALL) {
            return
        }

        val deprecatedApi = annotation.findAttributeValue("api")?.evaluate() as? Int ?: return
        val minSdk = context.project.minSdkVersion
        if (minSdk <= 0 || minSdk < deprecatedApi) {
            return
        }

        val replacement = annotation.findAttributeValue("replacement")?.evaluate() as? String
        val methodName = method?.let { "${it.containingClass?.qualifiedName}.${it.name}" } ?: "this method"

        val message = buildString {
            append("$methodName is annotated @DeprecatedSinceApi(api=$deprecatedApi). ")
            append("Since minSdkVersion is $minSdk, this call is unnecessary.")
            if (!replacement.isNullOrBlank()) {
                append(" Use $replacement instead.")
            }
        }

        val location = context.getLocation(element)
        context.report(Incident(ISSUE, element, location, message))
    }
}
package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UElement

class DeprecatedSinceApiDetector : Detector(), SourceCodeScanner {

    override fun applicableAnnotations(): List<String> = listOf(
        "androidx.annotation.DeprecatedSinceApi",
        "DeprecatedSinceApi"
    )

    override fun visitAnnotationUsage(
        context: JavaContext,
        usage: UElement,
        type: AnnotationUsageType,
        annotation: UAnnotation,
        qualifiedName: String,
        method: PsiMethod?,
        annotations: List<UAnnotation>,
        allAnnotations: List<UAnnotation>
    ) {
        val apiValue = (annotation.findAttributeValue("api") ?: annotation.findAttributeValue("value"))
            ?.evaluate() as? Int ?: return

        val minSdkVersion = context.project.minSdkVersion.featureLevel
        if (minSdkVersion >= apiValue) {
            val replacement = annotation.findAttributeValue("replacement")?.evaluate() as? String
            val name = method?.name ?: "this method"
            val msg = StringBuilder().apply {
                append("Use of `$name` is unnecessary because the `minSdkVersion` is $minSdkVersion and this method is deprecated since API $apiValue")
                if (!replacement.isNullOrEmpty()) {
                    append("; use `$replacement` instead")
                }
            }.toString()

            context.report(
                issue = ISSUE,
                scope = usage,
                location = context.getLocation(usage),
                message = msg
            )
        }
    }

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
    }
}
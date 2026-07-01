package com.android.tools.lint.checks

import com.android.resources.ResourceType
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.interprocedural.CallGraphResult
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UReferenceExpression
import org.w3c.dom.Node

class DeprecatedSinceApiDetector : Detector(), SourceCodeScanner {

    override fun applicableAnnotations(): List<String> {
        return listOf("androidx.annotation.DeprecatedSinceApi")
    }

    override fun visitAnnotationUsage(
        context: JavaContext,
        element: UElement,
        annotationInfo: AnnotationInfo,
        usageInfo: AnnotationUsageInfo
    ) {
        val annotation = annotationInfo.annotation
        val apiExpression = annotation.findAttributeValue("api") ?: return
        val apiValue = (ConstantEvaluator.evaluate(context, apiExpression) as? Number)?.toInt() ?: return

        val minSdkVersion = context.project.minSdkVersion.apiLevel
        if (minSdkVersion >= apiValue) {
            val messageAttr = ConstantEvaluator.evaluate(context, annotation.findAttributeValue("message")) as? String
            val replacementAttr = ConstantEvaluator.evaluate(context, annotation.findAttributeValue("replacement")) as? String

            val reportMessage = StringBuilder().apply {
                append("This method is deprecated since API level $apiValue (current min is $minSdkVersion)")
                if (!messageAttr.isNullOrBlank()) {
                    append(": ").append(messageAttr)
                }
                if (!replacementAttr.isNullOrBlank()) {
                    append(". Use `$replacementAttr` instead.")
                }
            }.toString()

            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                reportMessage
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "DeprecatedSinceApi",
            briefDescription = "Using a method deprecated in earlier SDK",
            explanation = """
                Some backport methods are only necessary until a specific version of Android.
                These have been annotated with `@DeprecatedSinceApi`, specifying the relevant API level
                and replacement suggestions. Calling these methods when the `minSdkVersion` is already
                at the deprecated API level or above is unnecessary.
            """,
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
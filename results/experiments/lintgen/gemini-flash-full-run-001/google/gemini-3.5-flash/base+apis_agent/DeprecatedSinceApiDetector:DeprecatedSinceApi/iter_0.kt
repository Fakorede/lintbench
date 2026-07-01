package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiModifierListOwner
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UReferenceExpression

class DeprecatedSinceApiDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UCallExpression::class.java, UReferenceExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                val resolved = node.resolve() ?: return
                checkDeprecatedSinceApi(context, node, resolved)
            }

            override fun visitReferenceExpression(node: UReferenceExpression) {
                if (node.uastParent is UCallExpression) return
                val resolved = node.resolve() ?: return
                checkDeprecatedSinceApi(context, node, resolved)
            }
        }
    }

    private fun checkDeprecatedSinceApi(context: JavaContext, node: UElement, resolved: PsiElement) {
        if (resolved !is PsiModifierListOwner) return
        val annotation = resolved.getAnnotation("androidx.annotation.DeprecatedSinceApi") ?: return

        val apiValue = annotation.findAttributeValue("api")?.let {
            context.evaluator.computeConstantValue(it)
        } as? Int ?: return

        val minSdkVersion = context.project.minSdkVersion.apiLevel
        if (minSdkVersion >= apiValue) {
            val messageAttr = annotation.findAttributeValue("message")?.let {
                context.evaluator.computeConstantValue(it)
            } as? String
            val replacementAttr = annotation.findAttributeValue("replacement")?.let {
                context.evaluator.computeConstantValue(it)
            } as? String

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
                node,
                context.getLocation(node),
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
                Some backport methods are only necessary until a specific version of Android. \
                These have been annotated with `@DeprecatedSinceApi`, specifying the relevant API level \
                and replacement suggestions. Calling these methods when the `minSdkVersion` is already \
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
package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiLiteral
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement

class DeprecatedSinceApiDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUElementTypes(): List<Class<out UElement>>? {
        return listOf(UCallExpression::class.java)
    }

    override fun createUElementHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                val method = node.resolve() ?: return
                val annotation = findDeprecatedSinceApiAnnotation(method) ?: return
                val apiLevel = getApiLevel(annotation) ?: return
                val minSdk = context.mainProject.minSdk
                if (minSdk >= apiLevel) {
                    val message = getMessage(annotation)
                        ?.takeIf { it.isNotBlank() }
                        ?: "This method is deprecated since API $apiLevel"
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        message
                    )
                }
            }
        }
    }

    private fun findDeprecatedSinceApiAnnotation(method: PsiMethod): PsiAnnotation? {
        return method.annotations.firstOrNull { annotation ->
            val qualifiedName = annotation.qualifiedName
            qualifiedName == ANDROIDX_DEPRECATED_SINCE_API ||
                qualifiedName == SUPPORT_DEPRECATED_SINCE_API ||
                qualifiedName?.substringAfterLast('.') == CLASS_NAME
        }
    }

    private fun getApiLevel(annotation: PsiAnnotation): Int? {
        val value = annotation.findAttributeValue(ATTR_API) as? PsiLiteral ?: return null
        return value.value as? Int
    }

    private fun getMessage(annotation: PsiAnnotation): String? {
        val value = annotation.findAttributeValue(ATTR_MESSAGE) as? PsiLiteral ?: return null
        return value.value as? String
    }

    companion object {
        private const val CLASS_NAME = "DeprecatedSinceApi"
        private const val ANDROIDX_DEPRECATED_SINCE_API = "androidx.annotation.DeprecatedSinceApi"
        private const val SUPPORT_DEPRECATED_SINCE_API = "android.support.annotation.DeprecatedSinceApi"
        private const val ATTR_API = "api"
        private const val ATTR_MESSAGE = "message"

        @JvmField
        val ISSUE: Issue = Issue.create(
            "DeprecatedSinceApi",
            "Using a method deprecated in earlier SDK",
            "Some backport methods are only necessary until a specific version of Android. " +
                "These have been annotated with `@DeprecatedSinceApi`, specifying the relevant " +
                "API level and replacement suggestions. Calling these methods when the " +
                "`minSdkVersion` is already at the deprecated API level or above is unnecessary.",
            Category.CORRECTNESS,
            4,
            Severity.WARNING,
            Implementation(
                DeprecatedSinceApiDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}
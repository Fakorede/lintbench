package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.UElementHandler
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifierListOwner
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UReferenceExpression
import java.util.EnumSet

class DeprecatedSinceApiDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val ANNOTATION_DEPRECATED_SINCE_API = "androidx.annotation.DeprecatedSinceApi"

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "DeprecatedSinceApi",
            briefDescription = "Using a method deprecated in earlier SDK",
            explanation = """
                Some backport methods are only necessary until a specific version of Android.
                These have been annotated with `@DeprecatedSinceApi`, specifying the relevant
                API level and replacement suggestions. Calling these methods when the
                `minSdkVersion` is already at the deprecated API level or above is unnecessary.
                """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                DeprecatedSinceApiDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE)
            )
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>>? {
        return listOf(UCallExpression::class.java, UReferenceExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                val method = node.resolve() ?: return
                checkDeprecatedSinceApi(context, node, method)
            }

            override fun visitReferenceExpression(node: UReferenceExpression) {
                when (val resolved = node.resolve()) {
                    is PsiMethod -> checkDeprecatedSinceApi(context, node, resolved)
                    is PsiField -> checkDeprecatedSinceApi(context, node, resolved)
                }
            }
        }
    }

    private fun checkDeprecatedSinceApi(
        context: JavaContext,
        node: UElement,
        member: PsiModifierListOwner
    ) {
        val annotation = member.modifierList.findAnnotation(ANNOTATION_DEPRECATED_SINCE_API) ?: return

        val apiLevel = annotation.findDeclaredAttributeValue("api")?.text?.toIntOrNull() ?: return
        if (context.project.minSdk < apiLevel) {
            return
        }

        val customMessage = annotation.findDeclaredAttributeValue("message")?.text
            ?.removeSurrounding("\"")
            ?.takeIf { it.isNotEmpty() }

        val message = if (customMessage != null) {
            "This API is deprecated as of API $apiLevel (current minSdk is ${context.project.minSdk}): $customMessage"
        } else {
            "This API is deprecated as of API $apiLevel (current minSdk is ${context.project.minSdk})"
        }

        context.report(ISSUE, node, context.getLocation(node), message)
    }
}
package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UElementHandler

class DeprecatedSinceApiDetector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                val method = node.resolve() as? PsiMethod ?: return
                checkMethod(context, node, method)
            }
        }
    }

    private fun checkMethod(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        val annotation = evaluator.getAllAnnotations(method, true).find {
            it.qualifiedName?.endsWith("DeprecatedSinceApi") == true
        } ?: return

        val apiLevelAttr = annotation.findAttributeValue("api")
            ?: annotation.findAttributeValue("value")
            ?: annotation.findAttributeValue("sdk")
        val apiLevel = apiLevelAttr?.let { evaluator.getIntValue(it) } ?: return

        val minSdk = context.mainProject.minSdkVersion
        if (minSdk >= apiLevel) {
            val messageAttr = annotation.findAttributeValue("message")
                ?: annotation.findAttributeValue("replacement")
            val replacement = messageAttr?.let { evaluator.getStringValue(it) }
            val message = if (replacement != null) {
                "Method is deprecated since API $apiLevel. Use $replacement instead."
            } else {
                "Method is deprecated since API $apiLevel and is unnecessary for minSdk $minSdk."
            }
            context.report(
                ISSUE,
                context.getLocation(node),
                message
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
                These have been annotated with `@DeprecatedSinceApi`, specifying the relevant \
                API level and replacement suggestions. Calling these methods when the \
                `minSdkVersion` is already at the deprecated API level or above is unnecessary.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                DeprecatedSinceApiDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}
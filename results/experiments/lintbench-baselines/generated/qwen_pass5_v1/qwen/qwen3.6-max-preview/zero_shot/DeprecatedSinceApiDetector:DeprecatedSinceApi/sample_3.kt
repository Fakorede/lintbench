package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class DeprecatedSinceApiDetector : Detector(), UastScanner {

    override fun getApplicableMethodNames(): List<String>? = null

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val minSdk = context.mainProject.minSdkVersion
        if (minSdk <= 0) return

        val annotation = findAnnotation(method) ?: return
        val evaluator = context.evaluator

        val apiLevelAttr = annotation.findDeclaredAttributeValue("api")
            ?: annotation.findDeclaredAttributeValue("value")
            ?: return

        val apiLevel = evaluator.getIntValue(apiLevelAttr) ?: return
        if (minSdk < apiLevel) return

        val replacementAttr = annotation.findDeclaredAttributeValue("replacement")
            ?: annotation.findDeclaredAttributeValue("message")
        val replacement = replacementAttr?.let { evaluator.getStringValue(it) }

        val message = if (!replacement.isNullOrEmpty()) {
            "This method is deprecated since API $apiLevel. Use $replacement instead."
        } else {
            "This method is deprecated since API $apiLevel and is unnecessary for minSdkVersion $minSdk."
        }

        context.report(ISSUE, node, context.getNameLocation(node), message)
    }

    private fun findAnnotation(method: PsiMethod): PsiAnnotation? {
        return method.annotations.find { ann ->
            val qName = ann.qualifiedName
            qName == "DeprecatedSinceApi" || qName?.endsWith(".DeprecatedSinceApi") == true
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
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                DeprecatedSinceApiDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}
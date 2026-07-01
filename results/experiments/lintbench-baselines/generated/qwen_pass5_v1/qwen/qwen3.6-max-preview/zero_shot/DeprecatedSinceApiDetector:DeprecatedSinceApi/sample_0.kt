package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UElementHandler

class DeprecatedSinceApiDetector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                val method = node.resolve() as? PsiMethod ?: return
                val evaluator = context.evaluator

                val annotation = method.annotations.find { ann ->
                    val qName = ann.qualifiedName ?: return@find false
                    qName == "DeprecatedSinceApi" || qName.endsWith(".DeprecatedSinceApi")
                } ?: return

                val apiAttr = annotation.findAttributeValue("api") ?: annotation.findAttributeValue("value") ?: return
                val apiLevel = (evaluator.evaluate(apiAttr) as? Number)?.toInt() ?: return

                val minSdk = context.mainProject.minSdkVersion
                if (minSdk >= apiLevel) {
                    val message = buildString {
                        append("This method is deprecated since API level $apiLevel, but the current minSdkVersion is $minSdk.")
                        val replacementMsg = annotation.findAttributeValue("message")?.let { evaluator.evaluate(it) as? String }
                            ?: annotation.findAttributeValue("replaceWith")?.let { evaluator.evaluate(it) as? String }
                        if (replacementMsg != null) {
                            append(" ")
                            append(replacementMsg)
                        }
                    }
                    context.report(ISSUE, node, context.getNameLocation(node), message)
                }
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "DeprecatedSinceApi",
            briefDescription = "Using a method deprecated in earlier SDK",
            explanation = "Some backport methods are only necessary until a specific version of Android. " +
                "These have been annotated with `@DeprecatedSinceApi`, specifying the relevant API level and replacement suggestions. " +
                "Calling these methods when the `minSdkVersion` is already at the deprecated API level or above is unnecessary.",
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
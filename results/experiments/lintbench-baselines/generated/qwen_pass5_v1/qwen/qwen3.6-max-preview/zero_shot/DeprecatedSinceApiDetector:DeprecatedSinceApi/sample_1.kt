package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.*

class DeprecatedSinceApiDetector : Detector(), Detector.UastScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "DeprecatedSinceApi",
            briefDescription = "Using a method deprecated in earlier SDK",
            explanation = "Some backport methods are only necessary until a specific version of Android. " +
                "These have been annotated with `@DeprecatedSinceApi`, specifying the relevant API level " +
                "and replacement suggestions. Calling these methods when the `minSdkVersion` is already " +
                "at the deprecated API level or above is unnecessary.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                DeprecatedSinceApiDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
        private const val ANNOTATION_NAME = "DeprecatedSinceApi"
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                if (node.kind != UastCallKind.METHOD_CALL) return

                val method = node.resolve() as? PsiMethod ?: return
                val annotation = method.annotations.find { ann ->
                    ann.qualifiedName?.endsWith(ANNOTATION_NAME) == true
                } ?: return

                val evaluator = context.evaluator
                val apiAttr = annotation.findAttributeValue("api")
                    ?: annotation.findAttributeValue("value") ?: return
                val apiLevel = (evaluator.evaluate(apiAttr) as? Number)?.toInt() ?: return

                val minSdk = context.mainProject.minSdkVersion ?: return
                if (minSdk >= apiLevel) {
                    val replacementAttr = annotation.findAttributeValue("replacement")
                    val replacement = evaluator.evaluate(replacementAttr) as? String ?: ""
                    val message = if (replacement.isNotEmpty()) {
                        "This method is deprecated since API $apiLevel. Use $replacement instead."
                    } else {
                        "This method is deprecated since API $apiLevel."
                    }
                    context.report(ISSUE, context.getLocation(node), message)
                }
            }
        }
}
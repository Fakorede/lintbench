package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression

class DeprecatedSinceApiDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val ANNOTATION_NAME = "DeprecatedSinceApi"

        val ISSUE = Issue.create(
            id = "DeprecatedSinceApi",
            briefDescription = "Using a method deprecated in earlier SDK",
            explanation = "Some backport methods are only necessary until a specific version of Android. " +
                "These have been annotated with `@DeprecatedSinceApi`, specifying the relevant API level and replacement suggestions. " +
                "Calling these methods when the `minSdkVersion` is already at the deprecated API level or above is unnecessary.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                DeprecatedSinceApiDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UCallExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                val method = node.resolve() ?: return
                val annotation = context.evaluator.findAnnotation(method, ANNOTATION_NAME) ?: return

                val apiLevelAttr: UExpression = annotation.findAttributeValue("api")
                    ?: annotation.findAttributeValue("value") ?: return

                val apiLevel = (apiLevelAttr.evaluate() as? Number)?.toInt() ?: return

                val minSdk = context.mainProject.minSdkVersion.apiLevel

                if (minSdk >= apiLevel) {
                    val message = "This method is deprecated since API $apiLevel. " +
                        "Since your minSdkVersion is $minSdk, you can use the platform API directly."
                    context.report(ISSUE, context.getLocation(node), message)
                }
            }
        }
    }
}
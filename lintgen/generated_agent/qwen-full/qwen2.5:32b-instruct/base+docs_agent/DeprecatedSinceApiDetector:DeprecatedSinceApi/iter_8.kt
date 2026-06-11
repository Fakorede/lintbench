package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.annotations.NonNull
import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.*

class DeprecatedSinceApiDetector : Detector(), SourceCodeScanner {

    companion object {
        val ISSUE_DEPRECATED_SINCE_API = Issue.create(
            "DeprecatedSinceApi",
            "Using a method deprecated in earlier SDK",
            "Some backport methods are only necessary until a specific version of Android. These have been annotated with `@DeprecatedSinceApi`, specifying the relevant API level and replacement suggestions. Calling these methods when the `minSdkVersion` is already at the deprecated API level or above is unnecessary.",
            Category.CORRECTNESS,
            5, // Priority
            Severity.WARNING,
            Implementation(DeprecatedSinceApiDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }

    override fun getApplicableMethodNames() = listOf("DeprecatedSinceApi")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression) {
        val method = node.resolve() ?: return
        val deprecatedAnnotation = method.annotations.firstOrNull { it.qualifiedName == "DeprecatedSinceApi" } ?: return

        val apiLevelAttr = deprecatedAnnotation.findAttributeValue("apiLevel")?.evaluate() as? Int ?: return

        val minSdkVersion = context.evaluator.minSdkVersion.apiLevel
        if (minSdkVersion >= apiLevelAttr) {
            val replacementAttr = deprecatedAnnotation.findAttributeValue("replacement")?.value as? String
            val message = "Method ${method.name} is deprecated since API level $apiLevelAttr. Consider using $replacementAttr instead."
            context.report(ISSUE_DEPRECATED_SINCE_API, node, context.getLocation(node), message)
        }
    }

    private fun UAnnotation.findAttributeValue(name: String): UElement? {
        return attributeValues.firstOrNull { it.name == name }?.value
    }
}
package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.annotations.NonNull
import com.android.resources.Density
import com.android.utils.ApiLevel
import com.android.utils.XmlUtils
import com.android.utils.flatten
import com.android.utils.getAttrValue
import com.android.utils.parseDensityQualifier
import com.android.utils.parseVersionedResourceName
import com.android.utils.resolveFullyQualifiedName
import com.android.utils.toQualifiedAttributeString
import com.android.utils.trimQuotes
import com.android.utils.unescapeXmlAttribute
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.java.JavaEvaluator
import org.jetbrains.uast.kotlin.KotlinEvaluator
import org.jetbrains.uast.toUElementOfType

import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UastUtils
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.java.JavaEvaluator
import org.jetbrains.uast.kotlin.KotlinEvaluator
import org.jetbrains.uast.toUElementOfType

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

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val deprecatedAnnotation = method.uastParent as? UAnnotation ?: return
        val apiLevelAttr = deprecatedAnnotation.getAttributeValue("apiLevel")?.asIntLiteral() ?: return

        val minSdkVersion = context.evaluator.minSdkVersion.apiLevel
        if (minSdkVersion >= apiLevelAttr) {
            val replacementAttr = deprecatedAnnotation.getAttributeValue("replacement")?.asStringValue()
            val message = "Method ${method.name} is deprecated since API level $apiLevelAttr. Consider using $replacementAttr instead."
            context.report(ISSUE_DEPRECATED_SINCE_API, node, context.getLocation(node), message)
        }
    }

    private fun UAnnotation.getAttributeValue(name: String): UExpression? {
        return findAttributeValue(name)?.uastInitializer
    }
}
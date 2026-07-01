package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.w3c.dom.Attr

class InternalInsetResourceDetector : Detector(), SourceCodeScanner, XmlScanner {

    override fun getApplicableAttributes(): Collection<String>? {
        return listOf("*")
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val value = attribute.nodeValue ?: return
        if (value.startsWith("@*android:dimen/") || value.startsWith("@android:dimen/")) {
            val resourceName = value.substringAfterLast("/")
            if (isInternalInsetResource(resourceName)) {
                context.report(
                    ISSUE,
                    attribute,
                    context.getValueLocation(attribute),
                    "Avoid using internal inset dimension resource `$resourceName`"
                )
            }
        }
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf("getIdentifier")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        if (!evaluator.isMemberInClass(method, "android.content.res.Resources")) {
            return
        }

        val args = node.valueArguments
        if (args.size == 3) {
            val name = ConstantEvaluator.evaluate(context, args[0]) as? String ?: return
            val defType = ConstantEvaluator.evaluate(context, args[1]) as? String ?: return
            val defPackage = ConstantEvaluator.evaluate(context, args[2]) as? String ?: return

            if (defPackage == "android" && defType == "dimen" && isInternalInsetResource(name)) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Avoid using internal inset dimension resource `$name`"
                )
            }
        } else if (args.size == 1) {
            val fullName = ConstantEvaluator.evaluate(context, args[0]) as? String ?: return
            if (fullName.startsWith("android:dimen/")) {
                val name = fullName.substringAfterLast("/")
                if (isInternalInsetResource(name)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Avoid using internal inset dimension resource `$name`"
                    )
                }
            }
        }
    }

    private fun isInternalInsetResource(name: String): Boolean {
        return name == "status_bar_height" ||
                name == "navigation_bar_height" ||
                name == "navigation_bar_width" ||
                name.startsWith("status_bar_height_") ||
                name.startsWith("navigation_bar_height_") ||
                name.startsWith("navigation_bar_width_")
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "InternalInsetResource",
            briefDescription = "Using internal inset dimension resource",
            explanation = """
                The internal inset dimension resources are not a supported way to \
                retrieve the relevant insets for your application. The insets are \
                dynamic values that can change while your app is visible, and your \
                app's window may not intersect with the system UI. To get the \
                relevant value for your app and listen to updates, use \
                `androidx.core.view.WindowInsetsCompat` and related APIs.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                InternalInsetResourceDetector::class.java,
                Scope.JAVA_AND_RESOURCE_FILES
            )
        )
    }
}
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
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class InternalInsetResourceDetector : Detector(), SourceCodeScanner {

    companion object {
        private val FORBIDDEN_NAMES = setOf(
            "status_bar_height",
            "status_bar_height_portrait",
            "status_bar_height_landscape",
            "navigation_bar_height",
            "navigation_bar_height_landscape",
            "navigation_bar_width"
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "InternalInsetResource",
            briefDescription = "Using internal inset dimension resource",
            explanation = """
                The internal inset dimension resources are not a supported way to retrieve the relevant insets \
                for your application. The insets are dynamic values that can change while your app is visible, \
                and your app's window may not intersect with the system UI. To get the relevant value for your \
                app and listen to updates, use `androidx.core.view.WindowInsetsCompat` and related APIs.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                InternalInsetResourceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf("getIdentifier")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!context.evaluator.isMemberInClass(method, "android.content.res.Resources")) {
            return
        }

        val args = node.valueArguments
        if (args.size != 3) {
            return
        }

        val name = ConstantEvaluator.evaluate(context, args[0]) as? String ?: return
        val type = ConstantEvaluator.evaluate(context, args[1]) as? String
        val pkg = ConstantEvaluator.evaluate(context, args[2]) as? String

        var resourceName = name
        var resourceType = type
        var isAndroid = pkg == "android"

        if (name.startsWith("android:")) {
            isAndroid = true
            val remainder = name.substring("android:".length)
            if (remainder.contains("/")) {
                resourceType = remainder.substringBefore("/")
                resourceName = remainder.substringAfter("/")
            } else {
                resourceName = remainder
            }
        }

        if (isAndroid && FORBIDDEN_NAMES.contains(resourceName)) {
            if (resourceType == null || resourceType == "dimen") {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Using internal inset dimension resource `$resourceName` is not supported. " +
                        "Use `androidx.core.view.WindowInsetsCompat` instead."
                )
            }
        }
    }
}
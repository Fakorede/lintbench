package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.evaluateString

class InternalInsetResourceDetector : Detector(), SourceCodeScanner {

    companion object {
        private val INTERNAL_INSET_RESOURCE_NAMES = setOf(
            "status_bar_height",
            "navigation_bar_height",
            "navigation_bar_height_landscape",
            "navigation_bar_width",
            "status_bar_height_portrait",
            "status_bar_height_landscape",
            "navigation_bar_height_car_mode",
            "navigation_bar_width_car_mode",
        )

        private const val RESOURCES_CLASS = "android.content.res.Resources"

        @JvmField
        val ISSUE = Issue.create(
            id = "InternalInsetResource",
            briefDescription = "Using internal inset dimension resource",
            explanation = """
                The internal inset dimension resources are not a supported way to \
                retrieve the relevant insets for your application. The insets are \
                dynamic values that can change while your app is visible, and your \
                app's window may not intersect with the system UI.
                To get the relevant value for your app and listen to updates, use \
                `androidx.core.view.WindowInsetsCompat` and related APIs.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                InternalInsetResourceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            ),
            androidSpecific = true,
        )
    }

    override fun getApplicableMethodNames(): List<String> = listOf(
        "getIdentifier",
        "getDimensionPixelSize",
        "getDimensionPixelOffset",
        "getDimension",
    )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator

        when (method.name) {
            "getIdentifier" -> {
                if (!evaluator.isMemberInClass(method, RESOURCES_CLASS) &&
                    !evaluator.isMemberInSubClassOf(method, RESOURCES_CLASS)
                ) {
                    return
                }
                val nameArg = node.valueArguments.firstOrNull() ?: return
                val resourceName = resolveStringValue(nameArg) ?: return
                val normalizedName = resourceName.removePrefix("android:")
                    .removePrefix("dimen/")
                    .removePrefix("@android:dimen/")
                    .removePrefix("@dimen/")
                if (INTERNAL_INSET_RESOURCE_NAMES.contains(normalizedName)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        buildMessage(normalizedName)
                    )
                }
            }

            "getDimensionPixelSize", "getDimensionPixelOffset", "getDimension" -> {
                if (!evaluator.isMemberInClass(method, RESOURCES_CLASS) &&
                    !evaluator.isMemberInSubClassOf(method, RESOURCES_CLASS)
                ) {
                    return
                }
                val resIdArg = node.valueArguments.firstOrNull() ?: return
                val resourceName = resolveResourceName(resIdArg) ?: return
                if (INTERNAL_INSET_RESOURCE_NAMES.contains(resourceName)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        buildMessage(resourceName)
                    )
                }
            }
        }
    }

    private fun resolveStringValue(expression: UExpression): String? {
        return expression.evaluateString()
    }

    private fun resolveResourceName(expression: UExpression): String? {
        // Try to resolve R.dimen.status_bar_height style references
        val text = when (expression) {
            is UQualifiedReferenceExpression -> expression.asSourceString()
            is USimpleNameReferenceExpression -> expression.identifier
            is ULiteralExpression -> return null
            else -> expression.asSourceString()
        }
        // Match patterns like R.dimen.status_bar_height or android.R.dimen.status_bar_height
        val parts = text.split(".")
        if (parts.size >= 3) {
            val lastPart = parts.last()
            if (INTERNAL_INSET_RESOURCE_NAMES.contains(lastPart)) {
                return lastPart
            }
        }
        return null
    }

    private fun buildMessage(resourceName: String): String {
        return "Using internal inset dimension resource `$resourceName` is not supported. " +
            "The insets are dynamic values that can change while your app is visible, and your " +
            "app's window may not intersect with the system UI. " +
            "Use `androidx.core.view.WindowInsetsCompat` and related APIs instead."
    }
}
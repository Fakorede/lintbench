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
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.evaluateString

class InternalInsetResourceDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            InternalInsetResourceDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "InternalInsetResource",
            briefDescription = "Using internal inset dimension resource",
            explanation = """
                The internal inset dimension resources are not a supported way to \
                retrieve the relevant insets for your application. The insets are \
                dynamic values that can change while your app is visible, and your \
                app's window may not intersect with the system UI.  To get the \
                relevant value for your app and listen to updates, use \
                `androidx.core.view.WindowInsetsCompat` and related APIs.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private val INSET_RESOURCE_NAMES = setOf(
            "status_bar_height",
            "navigation_bar_height",
            "navigation_bar_height_landscape",
            "navigation_bar_width",
            "status_bar_height_portrait",
            "status_bar_height_landscape",
        )

        private val GET_DIMENSION_METHODS = setOf(
            "getDimensionPixelSize",
            "getDimensionPixelOffset",
            "getDimension",
        )

        private const val ANDROID_RESOURCES_CLASS = "android.content.res.Resources"
        private const val GET_IDENTIFIER_METHOD = "getIdentifier"
    }

    override fun getApplicableMethodNames(): List<String> = GET_DIMENSION_METHODS.toList() +
            listOf(GET_IDENTIFIER_METHOD)

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        val methodName = method.name

        if (methodName == GET_IDENTIFIER_METHOD) {
            handleGetIdentifier(context, node, method)
            return
        }

        if (methodName in GET_DIMENSION_METHODS) {
            handleGetDimension(context, node, method)
        }
    }

    private fun handleGetIdentifier(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        val evaluator = context.evaluator
        if (!evaluator.isMemberInClass(method, ANDROID_RESOURCES_CLASS)) {
            return
        }

        val arguments = node.valueArguments
        if (arguments.isEmpty()) return

        val nameArg = arguments[0]
        val name = nameArg.evaluateString() ?: return

        if (name in INSET_RESOURCE_NAMES) {
            report(context, node)
        }
    }

    private fun handleGetDimension(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        val evaluator = context.evaluator
        if (!evaluator.isMemberInClass(method, ANDROID_RESOURCES_CLASS)) {
            return
        }

        val arguments = node.valueArguments
        if (arguments.isEmpty()) return

        val resourceIdArg = arguments[0]

        // Check for R.dimen.status_bar_height style references
        val resourceName = resolveResourceName(resourceIdArg) ?: return

        if (resourceName in INSET_RESOURCE_NAMES) {
            report(context, node)
        }
    }

    private fun resolveResourceName(expression: org.jetbrains.uast.UExpression): String? {
        if (expression is UReferenceExpression) {
            val resolved = expression.resolve()
            if (resolved != null) {
                val name = (resolved as? com.intellij.psi.PsiField)?.name
                if (name != null) {
                    return name
                }
            }
            // Try to get the name from the reference itself
            val refName = expression.resolvedName
            if (refName != null) {
                return refName
            }
        }
        if (expression is ULiteralExpression) {
            val value = expression.value
            if (value is String && value in INSET_RESOURCE_NAMES) {
                return value
            }
        }
        return null
    }

    private fun report(context: JavaContext, node: UCallExpression) {
        context.report(
            issue = ISSUE,
            scope = node,
            location = context.getLocation(node),
            message = "Using internal inset dimension resource is not supported. " +
                    "Use `androidx.core.view.WindowInsetsCompat` and related APIs instead.",
        )
    }
}
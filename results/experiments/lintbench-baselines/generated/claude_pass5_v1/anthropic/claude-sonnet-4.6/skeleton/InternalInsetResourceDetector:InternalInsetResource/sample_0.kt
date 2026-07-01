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
import org.jetbrains.uast.UResolvable
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
                app's window may not intersect with the system UI. \
                To get the relevant value for your app and listen to updates, use \
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

        private val GET_DIMENSION_METHODS = listOf(
            "getDimensionPixelSize",
            "getDimensionPixelOffset",
            "getDimension",
        )

        private const val RESOURCES_CLASS = "android.content.res.Resources"

        private fun isInternalInsetResource(name: String): Boolean {
            return INSET_RESOURCE_NAMES.contains(name)
        }
    }

    override fun getApplicableMethodNames(): List<String> = GET_DIMENSION_METHODS

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        // Check that this is a call on android.content.res.Resources
        if (!context.evaluator.isMemberInClass(method, RESOURCES_CLASS)) {
            return
        }

        // The first argument is a resource ID (int). We need to check if it resolves
        // to one of the internal inset dimension resources.
        val arguments = node.valueArguments
        if (arguments.isEmpty()) {
            return
        }

        val resourceIdArg = arguments[0]

        // Try to resolve the resource reference to get its name
        val resourceName = resolveResourceName(context, resourceIdArg) ?: return

        if (isInternalInsetResource(resourceName)) {
            context.report(
                issue = ISSUE,
                scope = node,
                location = context.getLocation(node),
                message = "Using internal inset dimension resource `$resourceName` is not supported. " +
                    "Use `androidx.core.view.WindowInsetsCompat` and related APIs instead.",
            )
        }
    }

    private fun resolveResourceName(context: JavaContext, expression: org.jetbrains.uast.UExpression): String? {
        // If it's a literal string, check directly
        if (expression is ULiteralExpression) {
            return expression.evaluateString()
        }

        // Try to resolve the reference to get the field name
        if (expression is UResolvable) {
            val resolved = expression.resolve()
            if (resolved is com.intellij.psi.PsiField) {
                val containingClass = resolved.containingClass ?: return null
                val className = containingClass.qualifiedName ?: return null

                // Check for android.R.dimen references
                if (className == "android.R.dimen" || className.endsWith(".R.dimen")) {
                    val fieldName = resolved.name
                    if (isInternalInsetResource(fieldName)) {
                        return fieldName
                    }
                }

                // Also handle inner class pattern: R.dimen
                if (containingClass.name == "dimen") {
                    val outerClass = containingClass.containingClass
                    if (outerClass?.name == "R") {
                        val fieldName = resolved.name
                        if (isInternalInsetResource(fieldName)) {
                            return fieldName
                        }
                    }
                }
            }
        }

        // Try to evaluate the resource reference via resource evaluator
        val resourceUrl = context.evaluator.let {
            try {
                val client = context.client
                val resources = client.getResources(context.project, true)
                null
            } catch (e: Exception) {
                null
            }
        }

        return null
    }
}
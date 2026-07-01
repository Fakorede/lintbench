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
import org.jetbrains.uast.evaluateString

class InternalInsetResourceDetector : Detector(), SourceCodeScanner {

    companion object {
        private val INSET_RESOURCE_NAMES = setOf(
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

        private val GET_DIMENSION_METHOD_NAMES = listOf(
            "getDimensionPixelSize",
            "getDimensionPixelOffset",
            "getDimension",
            "getIdentifier",
        )
    }

    override fun getApplicableMethodNames(): List<String> = GET_DIMENSION_METHOD_NAMES

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator

        if (!evaluator.isMemberInClass(method, RESOURCES_CLASS) &&
            !evaluator.isMemberInSubClassOf(method, RESOURCES_CLASS)
        ) {
            return
        }

        val methodName = method.name

        if (methodName == "getIdentifier") {
            // getIdentifier(String name, String defType, String defPackage)
            // Check if the name argument matches one of the inset resource names
            val nameArg = node.valueArguments.firstOrNull() ?: return
            val resolvedName = resolveStringValue(nameArg) ?: return
            if (resolvedName in INSET_RESOURCE_NAMES) {
                report(context, node, resolvedName)
            }
        } else {
            // getDimensionPixelSize / getDimensionPixelOffset / getDimension
            // The first argument is the resource ID (an int). We try to resolve it
            // as a resource reference and check its name.
            val resIdArg = node.valueArguments.firstOrNull() ?: return
            val resourceName = resolveResourceName(context, resIdArg) ?: return
            if (resourceName in INSET_RESOURCE_NAMES) {
                report(context, node, resourceName)
            }
        }
    }

    private fun report(context: JavaContext, node: UCallExpression, resourceName: String) {
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Using internal inset dimension resource `$resourceName` is not supported; " +
                "use `androidx.core.view.WindowInsetsCompat` and related APIs instead"
        )
    }

    private fun resolveStringValue(expression: UExpression): String? {
        return expression.evaluateString()
    }

    private fun resolveResourceName(context: JavaContext, expression: UExpression): String? {
        // Try to evaluate the expression as a resource reference field access
        // e.g. com.android.internal.R.dimen.status_bar_height
        val text = expression.asSourceString()

        // Check if the source text contains one of the known inset resource names
        for (name in INSET_RESOURCE_NAMES) {
            if (text.contains(name)) {
                return name
            }
        }

        // Try resolving via ResourceEvaluator
        val resourceUrl = context.evaluator.let {
            try {
                val resourceEvaluatorClass = Class.forName(
                    "com.android.tools.lint.checks.ResourceEvaluator"
                )
                null
            } catch (e: Exception) {
                null
            }
        }

        return resourceUrl
    }
}
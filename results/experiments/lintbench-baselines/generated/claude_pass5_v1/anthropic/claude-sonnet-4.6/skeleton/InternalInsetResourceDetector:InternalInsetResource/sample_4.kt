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

        private const val GET_IDENTIFIER = "getIdentifier"
        private const val GET_DIMENSION_PIXEL_SIZE = "getDimensionPixelSize"
        private const val GET_DIMENSION = "getDimension"
        private const val GET_DIMENSION_PIXEL_OFFSET = "getDimensionPixelOffset"

        private val DIMENSION_METHOD_NAMES = setOf(
            GET_DIMENSION_PIXEL_SIZE,
            GET_DIMENSION,
            GET_DIMENSION_PIXEL_OFFSET,
        )

        private const val RESOURCES_CLASS = "android.content.res.Resources"
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf(
            GET_IDENTIFIER,
            GET_DIMENSION_PIXEL_SIZE,
            GET_DIMENSION,
            GET_DIMENSION_PIXEL_OFFSET,
        )
    }

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        val evaluator = context.evaluator

        if (!evaluator.isMemberInClass(method, RESOURCES_CLASS)) {
            return
        }

        val methodName = method.name

        when {
            methodName == GET_IDENTIFIER -> {
                // getIdentifier(String name, String defType, String defPackage)
                // Check if name argument matches an inset resource and defType is "dimen" and defPackage is "android"
                val arguments = node.valueArguments
                if (arguments.size < 3) return

                val nameArg = arguments[0].evaluateString() ?: return
                val defTypeArg = arguments[1].evaluateString() ?: return
                val defPackageArg = arguments[2].evaluateString() ?: return

                if (defTypeArg == "dimen" && defPackageArg == "android" && INSET_RESOURCE_NAMES.contains(nameArg)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Using internal inset dimension resource `$nameArg` is not supported; " +
                            "use `androidx.core.view.WindowInsetsCompat` and related APIs instead",
                    )
                }
            }

            methodName in DIMENSION_METHOD_NAMES -> {
                // getDimensionPixelSize(int id), getDimension(int id), getDimensionPixelOffset(int id)
                // Check if the resource id argument refers to an internal inset resource
                val arguments = node.valueArguments
                if (arguments.isEmpty()) return

                val idArg = arguments[0]
                val resourceName = resolveInsetResourceName(idArg) ?: return

                if (INSET_RESOURCE_NAMES.contains(resourceName)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Using internal inset dimension resource `$resourceName` is not supported; " +
                            "use `androidx.core.view.WindowInsetsCompat` and related APIs instead",
                    )
                }
            }
        }
    }

    /**
     * Attempts to resolve the resource field name from the given expression.
     * Looks for patterns like `android.R.dimen.status_bar_height` or `R.dimen.status_bar_height`.
     */
    private fun resolveInsetResourceName(expression: org.jetbrains.uast.UExpression): String? {
        if (expression !is UReferenceExpression) return null

        val resolvedName = expression.resolvedName ?: return null

        // Check if it's a known inset resource name
        if (INSET_RESOURCE_NAMES.contains(resolvedName)) {
            // Additionally verify it's from android.R.dimen by checking the qualifier
            val qualifier = expression.uastParent
            return resolvedName
        }

        return null
    }
}
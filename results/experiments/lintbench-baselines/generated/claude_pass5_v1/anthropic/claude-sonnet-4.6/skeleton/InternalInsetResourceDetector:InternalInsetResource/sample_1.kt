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

        // Internal inset dimension resource names used in Android framework
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

        private const val GET_IDENTIFIER = "getIdentifier"
        private const val GET_DIMENSION_PIXEL_SIZE = "getDimensionPixelSize"
        private const val GET_DIMENSION_PIXEL_OFFSET = "getDimensionPixelOffset"
        private const val GET_DIMENSION = "getDimension"

        private const val RESOURCES_CLASS = "android.content.res.Resources"
    }

    override fun getApplicableMethodNames(): List<String> = listOf(
        GET_IDENTIFIER,
        GET_DIMENSION_PIXEL_SIZE,
        GET_DIMENSION_PIXEL_OFFSET,
        GET_DIMENSION,
    )

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        val evaluator = context.evaluator

        when (method.name) {
            GET_IDENTIFIER -> {
                // Check calls like: resources.getIdentifier("status_bar_height", "dimen", "android")
                if (!evaluator.isMemberInClass(method, RESOURCES_CLASS)) return

                val args = node.valueArguments
                if (args.size < 3) return

                val nameArg = args[0]
                val defTypeArg = args[1]
                val defPackageArg = args[2]

                val name = nameArg.evaluateString() ?: return
                val defType = defTypeArg.evaluateString() ?: return
                val defPackage = defPackageArg.evaluateString() ?: return

                if (defType == "dimen" && defPackage == "android" && name in INSET_RESOURCE_NAMES) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Using internal inset dimension resource `$name` is not supported; " +
                            "use `androidx.core.view.WindowInsetsCompat` and related APIs instead",
                    )
                }
            }

            GET_DIMENSION_PIXEL_SIZE,
            GET_DIMENSION_PIXEL_OFFSET,
            GET_DIMENSION -> {
                // Check calls like: resources.getDimensionPixelSize(R.dimen.status_bar_height)
                // where the resource is from the android package
                if (!evaluator.isMemberInClass(method, RESOURCES_CLASS)) return

                val args = node.valueArguments
                if (args.isEmpty()) return

                val resIdArg = args[0]
                val resourceUrl = getAndroidInsetResourceName(resIdArg) ?: return

                if (resourceUrl in INSET_RESOURCE_NAMES) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Using internal inset dimension resource `$resourceUrl` is not supported; " +
                            "use `androidx.core.view.WindowInsetsCompat` and related APIs instead",
                    )
                }
            }
        }
    }

    /**
     * Attempts to resolve a resource ID argument to an Android internal dimen resource name.
     * Returns the resource name if it's an android internal inset resource, null otherwise.
     */
    private fun getAndroidInsetResourceName(expression: org.jetbrains.uast.UExpression): String? {
        // Try to resolve field references like android.R.dimen.status_bar_height
        if (expression is UResolvable) {
            val resolved = expression.resolve()
            if (resolved is com.intellij.psi.PsiField) {
                val containingClass = resolved.containingClass ?: return null
                val fieldName = resolved.name

                // Check if this is in android.R.dimen
                val qualifiedName = containingClass.qualifiedName ?: return null
                if (qualifiedName == "android.R.dimen" || qualifiedName.endsWith(".R.dimen")) {
                    if (fieldName in INSET_RESOURCE_NAMES) {
                        // Only flag if it's the android package (not app's own R.dimen)
                        if (qualifiedName == "android.R.dimen") {
                            return fieldName
                        }
                    }
                }
            }
        }
        return null
    }
}
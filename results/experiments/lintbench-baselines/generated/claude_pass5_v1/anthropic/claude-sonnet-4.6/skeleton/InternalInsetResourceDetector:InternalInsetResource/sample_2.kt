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
            "status_bar_height_landscape",
        )

        private const val GET_IDENTIFIER = "getIdentifier"
        private const val GET_DIMENSION_PIXEL_SIZE = "getDimensionPixelSize"
        private const val GET_DIMENSION_PIXEL_OFFSET = "getDimensionPixelOffset"
        private const val GET_DIMENSION = "getDimension"

        private val DIMENSION_METHOD_NAMES = setOf(
            GET_DIMENSION_PIXEL_SIZE,
            GET_DIMENSION_PIXEL_OFFSET,
            GET_DIMENSION,
        )

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

        if (!evaluator.isMemberInClass(method, RESOURCES_CLASS)) {
            return
        }

        val methodName = method.name

        if (methodName == GET_IDENTIFIER) {
            // getIdentifier(String name, String defType, String defPackage)
            // Check if the name argument matches one of the internal inset resource names
            // and the defType is "dimen" and defPackage is "android"
            val arguments = node.valueArguments
            if (arguments.size < 3) return

            val nameArg = arguments[0].evaluateString() ?: return
            val defTypeArg = arguments[1].evaluateString() ?: return
            val defPackageArg = arguments[2].evaluateString() ?: return

            if (nameArg in INSET_RESOURCE_NAMES &&
                defTypeArg == "dimen" &&
                defPackageArg == "android"
            ) {
                reportIssue(context, node)
            }
        } else if (methodName in DIMENSION_METHOD_NAMES) {
            // getDimensionPixelSize(int id), etc.
            // We can't easily check the resource ID at lint time unless it's a known
            // R.dimen reference. However, we can check if the argument is a resource
            // field reference like android.R.dimen.status_bar_height
            val arguments = node.valueArguments
            if (arguments.isEmpty()) return

            val idArg = arguments[0]

            // Try to resolve the argument as a field reference
            if (idArg is UResolvable) {
                val resolved = idArg.resolve()
                if (resolved != null) {
                    val containingClass = (resolved as? com.intellij.psi.PsiField)
                        ?.containingClass
                    val containingClassName = containingClass?.qualifiedName ?: ""

                    // Check if this is android.R.dimen
                    if (containingClassName == "android.R.dimen" ||
                        containingClassName.endsWith(".R.dimen")
                    ) {
                        val fieldName = (resolved as? com.intellij.psi.PsiField)?.name ?: ""
                        if (fieldName in INSET_RESOURCE_NAMES) {
                            reportIssue(context, node)
                        }
                    }
                }
            }
        }
    }

    private fun reportIssue(context: JavaContext, node: UCallExpression) {
        context.report(
            issue = ISSUE,
            scope = node,
            location = context.getLocation(node),
            message = "Using internal inset dimension resource is not supported. " +
                "Use `androidx.core.view.WindowInsetsCompat` and related APIs instead.",
        )
    }
}
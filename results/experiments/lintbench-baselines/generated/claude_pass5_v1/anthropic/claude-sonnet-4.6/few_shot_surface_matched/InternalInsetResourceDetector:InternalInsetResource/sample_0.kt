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
            "status_bar_height_landscape",
            "navigation_bar_height_car_mode",
            "navigation_bar_width_car_mode",
            "status_bar_height_portrait",
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

        private val APPLICABLE_METHOD_NAMES = listOf(
            "getIdentifier",
            "getDimensionPixelSize",
            "getDimensionPixelOffset",
            "getDimension",
        )
    }

    override fun getApplicableMethodNames(): List<String> = APPLICABLE_METHOD_NAMES

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator

        if (isGetIdentifierCall(method, evaluator)) {
            checkGetIdentifierCall(context, node)
        } else if (isGetDimensionCall(method, evaluator)) {
            checkGetDimensionCall(context, node)
        }
    }

    private fun isGetIdentifierCall(method: PsiMethod, evaluator: com.android.tools.lint.client.api.JavaEvaluator): Boolean {
        return evaluator.isMemberInClass(method, RESOURCES_CLASS) &&
                method.name == "getIdentifier"
    }

    private fun isGetDimensionCall(method: PsiMethod, evaluator: com.android.tools.lint.client.api.JavaEvaluator): Boolean {
        return evaluator.isMemberInClass(method, RESOURCES_CLASS) &&
                (method.name == "getDimensionPixelSize" ||
                        method.name == "getDimensionPixelOffset" ||
                        method.name == "getDimension")
    }

    private fun checkGetIdentifierCall(context: JavaContext, node: UCallExpression) {
        val args = node.valueArguments
        if (args.isEmpty()) return

        val nameArg: UExpression = args[0]
        val nameValue = nameArg.evaluateString() ?: return

        // The name may be prefixed with "android:" or just the bare name
        val bareName = nameValue.removePrefix("android:")

        if (INSET_RESOURCE_NAMES.contains(bareName)) {
            reportIssue(context, node, bareName)
        }
    }

    private fun checkGetDimensionCall(context: JavaContext, node: UCallExpression) {
        val args = node.valueArguments
        if (args.isEmpty()) return

        // Look for R.dimen.<inset_name> patterns by inspecting the resolved field name
        val resIdArg = args[0]
        val resolved = resIdArg.tryResolve() ?: return

        if (resolved is com.intellij.psi.PsiField) {
            val fieldName = resolved.name
            val containingClass = resolved.containingClass?.name ?: return

            // Check it's a dimen resource field
            if (containingClass == "dimen" && INSET_RESOURCE_NAMES.contains(fieldName)) {
                // Verify it's from the android package (android.R.dimen.*)
                val qualifiedName = resolved.containingClass?.containingClass?.qualifiedName
                if (qualifiedName == "android.R") {
                    reportIssue(context, node, fieldName)
                }
            }
        }
    }

    private fun reportIssue(context: JavaContext, node: UCallExpression, resourceName: String) {
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Using internal inset dimension resource `$resourceName` is not supported. " +
                    "Use `androidx.core.view.WindowInsetsCompat` and related APIs instead to " +
                    "get inset values that are correct for your app's window."
        )
    }
}
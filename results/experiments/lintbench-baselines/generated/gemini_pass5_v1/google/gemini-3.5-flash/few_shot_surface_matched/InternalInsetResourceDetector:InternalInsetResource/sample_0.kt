package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class InternalInsetResourceDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "InternalInsetResource",
            briefDescription = "Using internal inset dimension resource",
            explanation = """
                The internal inset dimension resources are not a supported way to retrieve the relevant insets for your application. \
                The insets are dynamic values that can change while your app is visible, and your app's window may not intersect with the system UI. \
                To get the relevant value for your app and listen to updates, use `androidx.core.view.WindowInsetsCompat` and related APIs.
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(InternalInsetResourceDetector::class.java, Scope.JAVA_FILE_SCOPE),
            androidSpecific = true
        )

        private val TARGET_RESOURCES = setOf(
            "status_bar_height",
            "navigation_bar_height",
            "navigation_bar_height_landscape",
            "navigation_bar_width",
            "status_bar_height_portrait",
            "status_bar_height_landscape"
        )
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf("getIdentifier")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        if (!evaluator.isMemberInSubClassOf(method, "android.content.res.Resources")) {
            return
        }

        val args = node.valueArguments
        if (args.size < 3) return

        val name = ConstantEvaluator.evaluateString(context, args[0], false) ?: return
        val defType = ConstantEvaluator.evaluateString(context, args[1], false)
        val defPackage = ConstantEvaluator.evaluateString(context, args[2], false)

        val isAndroid = defPackage == "android" || name.startsWith("android:")
        val isDimen = defType == "dimen" || name.contains(":dimen/") || name.contains("dimen/")

        if (isAndroid && isDimen) {
            val resourceName = name.substringAfterLast("/")
            if (TARGET_RESOURCES.contains(resourceName)) {
                val message = "Use `WindowInsetsCompat` to retrieve insets instead of looking up internal dimension resources."
                context.report(
                    Incident(ISSUE, node, context.getLocation(node), message)
                )
            }
        }
    }
}
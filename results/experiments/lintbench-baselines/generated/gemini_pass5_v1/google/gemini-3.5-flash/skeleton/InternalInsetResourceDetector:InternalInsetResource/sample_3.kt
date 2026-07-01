package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.*
import org.jetbrains.uast.*

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
                app's window may not intersect with the system UI. To get the \
                relevant value for your app and listen to updates, use \
                `androidx.core.view.WindowInsetsCompat` and related APIs.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf("getIdentifier")
    }

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        val evaluator = context.evaluator
        if (!evaluator.isMemberInClass(method, "android.content.res.Resources")) {
            return
        }

        val args = node.valueArguments
        if (args.size != 3) {
            return
        }

        val name = ConstantEvaluator.evaluate(context, args[0]) as? String ?: return
        val defType = ConstantEvaluator.evaluate(context, args[1]) as? String
        val defPackage = ConstantEvaluator.evaluate(context, args[2]) as? String

        val isInternalInset = (name == "status_bar_height" || name == "navigation_bar_height") &&
                defType == "dimen" &&
                defPackage == "android"

        val isFullyQualified = (name == "android:dimen/status_bar_height" || name == "android:dimen/navigation_bar_height")

        if (isInternalInset || isFullyQualified) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Avoid using internal inset dimension resources; use `WindowInsetsCompat` instead"
            )
        }
    }
}
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
            explanation = "The internal inset dimension resources are not a supported way to " +
                    "retrieve the relevant insets for your application. The insets are " +
                    "dynamic values that can change while your app is visible, and your " +
                    "app's window may not intersect with the system UI. To get the relevant " +
                    "value for your app and listen to updates, use `androidx.core.view.WindowInsetsCompat` " +
                    "and related APIs.",
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
            "status_bar_height_landscape"
        )
    }

    override fun getApplicableMethodNames(): List<String>? = listOf("getIdentifier")

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        if (!context.evaluator.isMemberInClass(method, "android.content.res.Resources")) {
            return
        }

        val args = node.valueArguments
        if (args.size < 3) return

        val name = args[0].evaluate() as? String ?: return
        val type = args[1].evaluate() as? String
        val pkg = args[2].evaluate() as? String

        var finalPkg = pkg
        var finalType = type
        var finalName = name

        if (finalName.contains(":")) {
            val parts = finalName.split(":")
            if (parts.size == 2) {
                finalPkg = parts[0]
                finalName = parts[1]
            }
        }
        if (finalName.contains("/")) {
            val parts = finalName.split("/")
            if (parts.size == 2) {
                finalType = parts[0]
                finalName = parts[1]
            }
        }

        if (finalPkg == "android" && finalType == "dimen" && INSET_RESOURCE_NAMES.contains(finalName)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Use `WindowInsetsCompat` to retrieve insets instead of using internal resources"
            )
        }
    }
}
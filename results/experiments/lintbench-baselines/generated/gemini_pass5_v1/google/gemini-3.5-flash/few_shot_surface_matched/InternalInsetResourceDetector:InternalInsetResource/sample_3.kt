package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
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
                The internal inset dimension resources are not a supported way to retrieve the relevant insets for your application. The insets are dynamic values that can change while your app is visible, and your app's window may not intersect with the system UI. To get the relevant value for your app and listen to updates, use `androidx.core.view.WindowInsetsCompat` and related APIs.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                InternalInsetResourceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private val FORBIDDEN_NAMES = setOf(
            "status_bar_height",
            "navigation_bar_height",
            "navigation_bar_height_landscape",
            "navigation_bar_width"
        )
    }

    override fun getApplicableMethodNames(): List<String> = listOf("getIdentifier")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!context.evaluator.isMemberInClass(method, "android.content.res.Resources")) {
            return
        }

        val args = node.valueArguments
        if (args.size != 3) return

        val nameString = args[0].evaluate() as? String ?: return
        val defTypeString = args[1].evaluate() as? String
        val defPackageString = args[2].evaluate() as? String

        val isInternalInset = when {
            defPackageString == "android" && defTypeString == "dimen" -> nameString in FORBIDDEN_NAMES
            defPackageString == null && defTypeString == null -> {
                nameString == "android:dimen/status_bar_height" ||
                nameString == "android:dimen/navigation_bar_height" ||
                nameString == "android:dimen/navigation_bar_height_landscape" ||
                nameString == "android:dimen/navigation_bar_width"
            }
            else -> false
        }

        if (isInternalInset) {
            context.report(
                Incident(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Use `WindowInsetsCompat` instead of retrieving internal inset dimension resources"
                )
            )
        }
    }
}
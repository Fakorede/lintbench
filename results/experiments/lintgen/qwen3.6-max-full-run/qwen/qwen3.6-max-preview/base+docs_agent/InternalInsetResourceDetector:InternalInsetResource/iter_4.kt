package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.w3c.dom.Attr

class InternalInsetResourceDetector : Detector(), Detector.XmlScanner, Detector.UastScanner {

    companion object {
        private val INTERNAL_INSET_DIMENS = setOf(
            "status_bar_height",
            "navigation_bar_height",
            "status_bar_height_portrait",
            "navigation_bar_height_landscape",
            "system_bar_height"
        )

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "InternalInsetResource",
            briefDescription = "Using internal inset dimension resource",
            explanation = """
                The internal inset dimension resources are not a supported way to retrieve the relevant insets for your application. The insets are dynamic values that can change while your app is visible, and your app's window may not intersect with the system UI. To get the relevant value for your app and listen to updates, use `androidx.core.view.WindowInsetsCompat` and related APIs.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                InternalInsetResourceDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun getApplicableAttributes(): Collection<String>? = listOf("*")

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val value = attribute.value
        if (value.startsWith("@android:dimen/") || value.startsWith("@*android:dimen/")) {
            val name = value.substringAfterLast('/')
            if (name in INTERNAL_INSET_DIMENS) {
                context.report(
                    ISSUE,
                    context.getLocation(attribute),
                    "Use `WindowInsetsCompat` instead of internal inset dimension resources"
                )
            }
        }
    }

    override fun getApplicableMethodNames(): List<String>? = listOf("getIdentifier")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!context.evaluator.isMemberInClass(method, "android.content.res.Resources")) return

        val args = node.valueArguments
        if (args.size != 3) return

        val name = args[0].evaluate() as? String ?: return
        val type = args[1].evaluate() as? String ?: return
        val pkg = args[2].evaluate() as? String ?: return

        if (name in INTERNAL_INSET_DIMENS && type == "dimen" && pkg == "android") {
            context.report(
                ISSUE,
                context.getLocation(node),
                "Use `WindowInsetsCompat` instead of internal inset dimension resources"
            )
        }
    }
}
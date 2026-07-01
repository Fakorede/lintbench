package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.w3c.dom.Attr
import java.util.EnumSet

class InternalInsetResourceDetector : Detector(), SourceCodeScanner, XmlScanner {

    companion object {
        private val forbiddenNames = setOf(
            "status_bar_height",
            "navigation_bar_height",
            "navigation_bar_height_landscape",
            "navigation_bar_width",
            "status_bar_height_portrait",
            "status_bar_height_landscape"
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "InternalInsetResource",
            briefDescription = "Using internal inset dimension resource",
            explanation = """
                The internal inset dimension resources are not a supported way to retrieve the relevant insets for your application. \
                The insets are dynamic values that can change while your app is visible, and your app's window may not intersect \
                with the system UI. To get the relevant value for your app and listen to updates, use \
                `androidx.core.view.WindowInsetsCompat` and related APIs.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                InternalInsetResourceDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE)
            )
        )
    }

    // --- SourceCodeScanner ---

    override fun getApplicableMethodNames(): List<String> = listOf("getIdentifier")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!context.evaluator.isMemberInClass(method, "android.content.res.Resources")) {
            return
        }

        val args = node.valueArguments
        if (args.size >= 3) {
            val name = ConstantEvaluator.evaluate(context, args[0]) as? String ?: return
            val type = ConstantEvaluator.evaluate(context, args[1]) as? String
            val pkg = ConstantEvaluator.evaluate(context, args[2]) as? String

            if (isInternalInsetResource(name, type, pkg)) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Use `WindowInsetsCompat` instead of looking up internal dimension resources"
                )
            }
        }
    }

    private fun isInternalInsetResource(name: String, type: String?, pkg: String?): Boolean {
        val resolvedPkg = pkg ?: if (name.contains(":")) name.substringBefore(":") else null
        val remaining = if (name.contains(":")) name.substringAfter(":") else name
        val resolvedType = type ?: if (remaining.contains("/")) remaining.substringBefore("/") else null
        val resolvedName = if (remaining.contains("/")) remaining.substringAfter("/") else remaining

        val isAndroid = resolvedPkg == "android" || (pkg == null && name.startsWith("android:"))
        val isDimen = resolvedType == "dimen" || (type == null && name.contains("dimen/"))

        return isAndroid && isDimen && forbiddenNames.contains(resolvedName)
    }

    // --- XmlScanner ---

    override fun getApplicableAttributes(): Collection<String>? = null

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val value = attribute.value
        if (value.startsWith("@android:dimen/") || value.startsWith("@*android:dimen/")) {
            val name = value.substringAfter("dimen/")
            if (forbiddenNames.contains(name)) {
                context.report(
                    ISSUE,
                    attribute,
                    context.getValueLocation(attribute),
                    "Use `WindowInsetsCompat` instead of referencing internal dimension resources"
                )
            }
        }
    }
}
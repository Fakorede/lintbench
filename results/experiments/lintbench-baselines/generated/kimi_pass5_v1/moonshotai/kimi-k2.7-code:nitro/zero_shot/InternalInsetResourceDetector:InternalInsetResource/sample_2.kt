package com.android.tools.lint.checks

import com.android.resources.ResourceUrl
import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.w3c.dom.Attr

class InternalInsetResourceDetector : ResourceXmlDetector(), SourceCodeScanner {

    override fun getApplicableAttributes(): Collection<String>? = ALL

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val value = attribute.value ?: return
        val url = ResourceUrl.parse(value) ?: return
        if (url.type != "dimen" || url.packageName != "android") return
        if (url.name !in INTERNAL_INSET_RESOURCES) return

        context.report(
            ISSUE,
            attribute,
            context.getValueLocation(attribute),
            "Using internal inset dimension resource `${url.name}` is not a supported way to " +
                    "retrieve insets. Use androidx.core.view.WindowInsetsCompat instead."
        )
    }

    override fun getApplicableMethodNames(): List<String> = listOf("getIdentifier")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!context.evaluator.isMemberInClass(method, "android.content.res.Resources")) return

        val args = node.valueArguments
        val name = args.getOrNull(0)?.evaluateString() ?: return
        val type = args.getOrNull(1)?.evaluateString() ?: return
        val pkg = args.getOrNull(2)?.evaluateString()

        if (type != "dimen") return
        if (pkg != null && pkg != "android" && pkg != "com.android.internal") return
        if (name !in INTERNAL_INSET_RESOURCES) return

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Using internal inset dimension resource `$name` is not a supported way to " +
                    "retrieve insets. Use androidx.core.view.WindowInsetsCompat instead."
        )
    }

    companion object {
        private val INTERNAL_INSET_RESOURCES = setOf(
            "status_bar_height",
            "navigation_bar_height",
            "navigation_bar_width",
            "navigation_bar_height_landscape",
            "system_bar_height"
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "InternalInsetResource",
            briefDescription = "Using internal inset dimension resource",
            explanation = """
                The internal inset dimension resources are not a supported way to retrieve the
                relevant insets for your application. The insets are dynamic values that can
                change while your app is visible, and your app's window may not intersect with
                the system UI. To get the relevant value for your app and listen to updates,
                use <code>androidx.core.view.WindowInsetsCompat</code> and related APIs.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                InternalInsetResourceDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}
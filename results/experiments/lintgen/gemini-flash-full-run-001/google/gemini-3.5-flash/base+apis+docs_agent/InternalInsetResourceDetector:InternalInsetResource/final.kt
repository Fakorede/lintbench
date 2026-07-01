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
import org.w3c.dom.Element

class InternalInsetResourceDetector : Detector(), SourceCodeScanner, XmlScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("getIdentifier")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!context.evaluator.isMemberInClass(method, "android.content.res.Resources")) return

        val args = node.valueArguments
        if (args.size == 3) {
            val nameArg = ConstantEvaluator.evaluate(context, args[0]) as? String ?: return
            val typeArg = ConstantEvaluator.evaluate(context, args[1]) as? String
            val pkgArg = ConstantEvaluator.evaluate(context, args[2]) as? String

            if (typeArg == "dimen" && pkgArg == "android" && FORBIDDEN_NAMES.contains(nameArg)) {
                report(context, node, nameArg)
            } else if ((typeArg == null || pkgArg == null) && nameArg.contains(':') && nameArg.contains('/')) {
                val pkg = nameArg.substringBefore(':')
                val rest = nameArg.substringAfter(':')
                val type = rest.substringBefore('/')
                val entry = rest.substringAfter('/')
                if (pkg == "android" && type == "dimen" && FORBIDDEN_NAMES.contains(entry)) {
                    report(context, node, entry)
                }
            }
        }
    }

    private fun report(context: JavaContext, node: UCallExpression, name: String) {
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Using internal inset dimension resource `$name` is not supported"
        )
    }

    override fun getApplicableElements(): Collection<String>? = null

    override fun visitElement(context: XmlContext, element: Element) {
        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as Attr
            val value = attr.value
            if (value.startsWith("@android:dimen/") ||
                value.startsWith("@*android:dimen/") ||
                value.startsWith("?android:dimen/") ||
                value.startsWith("?*android:dimen/")
            ) {
                val name = value.substringAfter('/')
                if (FORBIDDEN_NAMES.contains(name)) {
                    context.report(
                        ISSUE,
                        attr,
                        context.getValueLocation(attr),
                        "Using internal inset dimension resource `$name` is not supported"
                    )
                }
            }
        }
    }

    companion object {
        private val FORBIDDEN_NAMES = setOf(
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
                The internal inset dimension resources are not a supported way to \
                retrieve the relevant insets for your application. The insets are \
                dynamic values that can change while your app is visible, and your \
                app's window may not intersect with the system UI. To get the relevant \
                value for your app and listen to updates, use `androidx.core.view.WindowInsetsCompat` \
                and related APIs.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                InternalInsetResourceDetector::class.java,
                Scope.JAVA_AND_RESOURCE_FILES
            )
        )
    }
}
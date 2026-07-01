package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
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

class InternalInsetResourceDetector : Detector(), XmlScanner, SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
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
                EnumSet.of(Scope.RESOURCE_FILE, Scope.JAVA_FILE)
            )
        )

        private fun isInsetResourceName(name: String): Boolean {
            return name == "status_bar_height" ||
                   name == "navigation_bar_height" ||
                   name == "navigation_bar_height_landscape" ||
                   name == "navigation_bar_width" ||
                   name == "status_bar_height_portrait" ||
                   name == "status_bar_height_landscape"
        }
    }

    // XML Scanning
    override fun getApplicableAttributes(): Collection<String> = XmlScanner.ALL

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val value = attribute.value
        if (value.startsWith("@android:dimen/") || value.startsWith("@*android:dimen/")) {
            val name = value.substringAfterLast("/")
            if (isInsetResourceName(name)) {
                context.report(
                    ISSUE,
                    attribute,
                    context.getValueLocation(attribute),
                    "Avoid using internal inset dimension resource `$value`"
                )
            }
        }
    }

    // Java/Kotlin Scanning
    override fun getApplicableMethodNames(): List<String> = listOf("getIdentifier")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        if (!evaluator.isMemberInSubClassOf(method, "android.content.res.Resources", false)) {
            return
        }

        val args = node.valueArguments
        if (args.size == 3) {
            val name = args[0].evaluate() as? String ?: return
            val type = args[1].evaluate() as? String ?: return
            val pkg = args[2].evaluate() as? String ?: return

            if (pkg == "android" && type == "dimen" && isInsetResourceName(name)) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Avoid using internal inset dimension resource `$name`"
                )
            }
        }
    }
}
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
import com.android.tools.lint.detector.api.XmlScannerConstants
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.evaluateString
import org.w3c.dom.Attr

class InternalInsetResourceDetector : Detector(), XmlScanner, SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            InternalInsetResourceDetector::class.java,
            Scope.JAVA_AND_RESOURCE_FILES
        )

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "InternalInsetResource",
            briefDescription = "Using internal inset dimension resource",
            explanation = """
                The internal inset dimension resources are not a supported way to \
                retrieve the relevant insets for your application. The insets are \
                dynamic values that can change while your app is visible, and your \
                app's window may not intersect with the system UI. To get the \
                relevant value for your app and listen to updates, use \
                `androidx.core.view.WindowInsetsCompat` and related APIs.
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION
        )

        private val INSET_RESOURCE_NAMES = setOf(
            "status_bar_height",
            "status_bar_height_landscape",
            "status_bar_height_portrait",
            "navigation_bar_height",
            "navigation_bar_width",
            "navigation_bar_height_landscape",
            "navigation_bar_height_portrait",
            "system_bars_height"
        )

        private val RESOURCE_REFERENCE_REGEX = Regex(
            """@\*?android:dimen/(${INSET_RESOURCE_NAMES.joinToString("|")})"""
        )

        private const val MESSAGE =
            "Using internal inset dimension resource. Use WindowInsetsCompat instead."
    }

    override fun getApplicableAttributes(): Collection<String>? = XmlScannerConstants.ALL

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val value = attribute.value ?: return
        if (!value.startsWith("@")) return
        if (RESOURCE_REFERENCE_REGEX.find(value) == null) return

        context.report(
            ISSUE,
            attribute,
            context.getValueLocation(attribute),
            MESSAGE
        )
    }

    override fun getApplicableMethodNames(): List<String> = listOf("getIdentifier")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val containingClass = method.containingClass ?: return
        if (!context.evaluator.extendsClass(containingClass, "android.content.res.Resources", false)) {
            return
        }
        if (node.valueArgumentCount < 3) return

        val name = node.valueArguments[0].evaluateString() ?: return
        val defType = node.valueArguments[1].evaluateString() ?: return
        val packageName = node.valueArguments[2].evaluateString() ?: return

        if (defType == "dimen" && packageName == "android" && name in INSET_RESOURCE_NAMES) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                MESSAGE
            )
        }
    }
}
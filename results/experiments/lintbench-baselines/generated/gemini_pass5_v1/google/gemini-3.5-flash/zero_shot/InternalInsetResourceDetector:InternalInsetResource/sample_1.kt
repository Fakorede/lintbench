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
import java.util.EnumSet

class InternalInsetResourceDetector : Detector(), SourceCodeScanner, XmlScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("getIdentifier")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!context.evaluator.isMemberInClass(method, "android.content.res.Resources")) {
            return
        }
        val args = node.valueArguments
        if (args.size < 3) return

        val name = ConstantEvaluator.evaluate(context, args[0]) as? String ?: return
        val type = ConstantEvaluator.evaluate(context, args[1]) as? String
        val pkg = ConstantEvaluator.evaluate(context, args[2]) as? String

        if (checkResource(name, type, pkg)) {
            context.report(
                ISSUE,
                node,
                context.getCallLocation(node, includeReceiver = false, includeArguments = true),
                "Using internal inset dimension resource"
            )
        }
    }

    private fun checkResource(name: String, type: String?, pkg: String?): Boolean {
        var finalName = name
        var finalType = type
        var finalPkg = pkg

        if (finalName.contains(':')) {
            val parts = finalName.split(':')
            finalPkg = parts[0]
            finalName = parts[1]
        }
        if (finalName.contains('/')) {
            val parts = finalName.split('/')
            finalType = parts[0]
            finalName = parts[1]
        }

        return finalPkg == "android" && finalType == "dimen" && INVALID_RESOURCE_NAMES.contains(finalName)
    }

    override fun getApplicableAttributes(): Collection<String> = ALL

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val value = attribute.value ?: return
        if (isForbiddenResourceRef(value)) {
            context.report(
                ISSUE,
                attribute,
                context.getValueLocation(attribute),
                "Using internal inset dimension resource"
            )
        }
    }

    override fun getApplicableElements(): Collection<String> = ALL

    override fun visitElement(context: XmlContext, element: Element) {
        val tagName = element.tagName
        if (tagName == "dimen" || tagName == "item") {
            val text = element.textContent ?: return
            if (isForbiddenResourceRef(text.trim())) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Using internal inset dimension resource"
                )
            }
        }
    }

    private fun isForbiddenResourceRef(value: String): Boolean {
        if (!value.startsWith("@")) return false
        val clean = value.removePrefix("@").removePrefix("+").removePrefix("*")
        if (!clean.startsWith("android:dimen/")) return false
        val name = clean.substringAfter("android:dimen/")
        return INVALID_RESOURCE_NAMES.contains(name)
    }

    companion object {
        private val INVALID_RESOURCE_NAMES = setOf(
            "status_bar_height",
            "navigation_bar_height",
            "navigation_bar_height_landscape",
            "navigation_bar_width",
            "navigation_bar_width_car_mode",
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
                EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE)
            )
        )
    }
}
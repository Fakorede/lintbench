package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.util.EnumSet

class InternalInsetResourceDetector : Detector(), SourceCodeScanner, XmlScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("getIdentifier")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        if (!evaluator.isMemberInClass(method, "android.content.res.Resources")) {
            return
        }

        val args = node.valueArguments
        if (args.size != 3) return

        val name = ConstantEvaluator.evaluate(context, args[0]) as? String ?: return
        val defType = ConstantEvaluator.evaluate(context, args[1]) as? String
        val defPackage = ConstantEvaluator.evaluate(context, args[2]) as? String

        val isStatusBarOrNavBar = name.contains("status_bar_height") || 
                name.contains("navigation_bar_height") || 
                name.contains("navigation_bar_width")

        if (isStatusBarOrNavBar) {
            val hasAndroidPackage = defPackage == "android" || name.startsWith("android:") || name.contains(":android:") || name.contains("@android:") || name.contains("@*android:")
            val hasDimenType = defType == "dimen" || name.contains("dimen/")
            if (hasAndroidPackage && hasDimenType) {
                reportInternalInset(context, node)
            }
        }
    }

    private fun reportInternalInset(context: JavaContext, node: UCallExpression) {
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Using internal inset dimension resource"
        )
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        checkElement(context, root)
    }

    private fun checkElement(context: XmlContext, element: Element) {
        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as Attr
            checkValue(context, attr.value, context.getValueLocation(attr))
        }
        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                checkElement(context, child as Element)
            } else if (child.nodeType == Node.TEXT_NODE) {
                checkValue(context, child.nodeValue, context.getLocation(child))
            }
        }
    }

    private fun checkValue(context: XmlContext, value: String, location: Location) {
        val trimmed = value.trim()
        if (trimmed.startsWith("@") && (trimmed.contains("status_bar_height") || trimmed.contains("navigation_bar_height") || trimmed.contains("navigation_bar_width"))) {
            if (trimmed.contains("android:dimen")) {
                context.report(
                    ISSUE,
                    location,
                    "Using internal inset dimension resource"
                )
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "InternalInsetResource",
            briefDescription = "Using internal inset dimension resource",
            explanation = """
                The internal inset dimension resources are not a supported way to retrieve the relevant insets for your application. \
                The insets are dynamic values that can change while your app is visible, and your app's window may not intersect with the system UI. \
                To get the relevant value for your app and listen to updates, use `androidx.core.view.WindowInsetsCompat` and related APIs.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                InternalInsetResourceDetector::class.java,
                EnumSet.of(Scope.RESOURCE_FILE, Scope.JAVA_FILE)
            )
        )
    }
}
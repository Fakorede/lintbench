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
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Attr
import org.w3c.dom.Node
import java.util.EnumSet

class InternalInsetResourceDetector : Detector(), SourceCodeScanner, XmlScanner {

    companion object {
        private val FORBIDDEN_NAMES = setOf(
            "status_bar_height",
            "navigation_bar_height",
            "navigation_bar_height_landscape",
            "navigation_bar_width"
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "InternalInsetResource",
            briefDescription = "Using internal inset dimension resource",
            explanation = """
                The internal inset dimension resources are not a supported way to \
                retrieve the relevant insets for your application. The insets are \
                dynamic values that can change while your app is visible, and your \
                app's window may not intersect with the system UI. To get the \
                relevant value for your app and listen to updates, use \
                `androidx.core.view.WindowInsetsCompat` and related APIs.
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

    // --- SourceCodeScanner implementation ---

    override fun getApplicableMethodNames(): List<String> = listOf("getIdentifier")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        if (!evaluator.isMemberInClass(method, "android.content.res.Resources")) {
            return
        }

        val args = node.valueArguments
        if (args.size != 3) return

        val name = ConstantEvaluator.evaluate(context, args[0]) as? String ?: return
        val type = ConstantEvaluator.evaluate(context, args[1]) as? String
        val pkg = ConstantEvaluator.evaluate(context, args[2]) as? String

        val resolvedName: String
        val resolvedType: String?
        val resolvedPkg: String?

        if (name.contains(':') && name.contains('/')) {
            val parts = name.split(":", "/")
            if (parts.size == 3) {
                resolvedPkg = parts[0]
                resolvedType = parts[1]
                resolvedName = parts[2]
            } else {
                return
            }
        } else {
            resolvedName = name
            resolvedType = type
            resolvedPkg = pkg
        }

        if (resolvedName in FORBIDDEN_NAMES && resolvedType == "dimen" && resolvedPkg == "android") {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Using internal inset dimension resource"
            )
        }
    }

    // --- XmlScanner implementation ---

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        checkElement(context, root)
    }

    private fun checkElement(context: XmlContext, element: Element) {
        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as Attr
            val value = attr.value
            if (isForbiddenResourceRef(value)) {
                context.report(
                    ISSUE,
                    attr,
                    context.getValueLocation(attr),
                    "Using internal inset dimension resource"
                )
            }
        }

        val firstChild = element.firstChild
        if (firstChild != null && firstChild.nodeType == Node.TEXT_NODE) {
            val text = firstChild.nodeValue?.trim()
            if (text != null && isForbiddenResourceRef(text)) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Using internal inset dimension resource"
                )
            }
        }

        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child is Element) {
                checkElement(context, child)
            }
        }
    }

    private fun isForbiddenResourceRef(value: String): Boolean {
        if (value.startsWith("@*android:dimen/") || value.startsWith("@android:dimen/")) {
            val name = value.substringAfterLast('/')
            if (name in FORBIDDEN_NAMES) {
                return true
            }
        }
        return false
    }
}
package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiField
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UElementHandler
import org.jetbrains.uast.UReferenceExpression
import org.w3c.dom.Element
import org.w3c.dom.Node

class InternalInsetResourceDetector : Detector(), XmlScanner, SourceCodeScanner {
    companion object {
        private val INTERNAL_INSET_DIMENS = setOf(
            "status_bar_height",
            "navigation_bar_height",
            "system_bar_height",
            "status_bar_height_reduced",
            "navigation_bar_height_reduced",
            "quick_qs_offset_height",
            "quick_qs_total_height",
            "navigation_bar_frame_height",
            "navigation_bar_gesture_height"
        )

        val ISSUE = Issue.create(
            id = "InternalInsetResource",
            briefDescription = "Using internal inset dimension resource",
            explanation = """
                The internal inset dimension resources are not a supported way to \
                retrieve the relevant insets for your application. The insets are \
                dynamic values that can change while your app is visible, and your \
                app's window may not intersect with the system UI. \
                To get the relevant value for your app and listen to updates, use \
                `androidx.core.view.WindowInsetsCompat` and related APIs.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                InternalInsetResourceDetector::class.java,
                Scope.RESOURCE_AND_SOURCE_SCOPE
            )
        )
    }

    override fun getApplicableElements(): Collection<String>? = ALL

    override fun visitElement(context: XmlContext, element: Element) {
        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val value = attributes.item(i).nodeValue
            checkXmlValue(context, element, value)
        }
        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val node = childNodes.item(i)
            if (node.nodeType == Node.TEXT_NODE) {
                checkXmlValue(context, element, node.nodeValue)
            }
        }
    }

    private fun checkXmlValue(context: XmlContext, element: Element, value: String?) {
        if (value.isNullOrEmpty()) return
        val matches = Regex("@android:dimen/(\\w+)").findAll(value)
        for (match in matches) {
            val dimenName = match.groupValues[1]
            if (INTERNAL_INSET_DIMENS.contains(dimenName)) {
                context.report(
                    ISSUE,
                    context.getLocation(element),
                    "Using internal inset dimension resource `@android:dimen/$dimenName`. " +
                        "Use `WindowInsetsCompat` instead."
                )
            }
        }
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UReferenceExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitReferenceExpression(node: UReferenceExpression) {
                val resolved = node.resolve() as? PsiField ?: return
                if (resolved.containingClass?.qualifiedName == "android.R.dimen") {
                    val dimenName = resolved.name
                    if (INTERNAL_INSET_DIMENS.contains(dimenName)) {
                        context.report(
                            ISSUE,
                            context.getLocation(node),
                            "Using internal inset dimension resource `android.R.dimen.$dimenName`. " +
                                "Use `WindowInsetsCompat` instead."
                        )
                    }
                }
            }
        }
    }
}
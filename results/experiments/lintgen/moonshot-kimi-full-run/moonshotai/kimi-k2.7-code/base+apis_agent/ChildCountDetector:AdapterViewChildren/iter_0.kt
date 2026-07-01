package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element
import org.w3c.dom.Node

class ChildCountDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String> = ADAPTER_VIEW_TAGS

    override fun visitElement(context: XmlContext, element: Element) {
        for (i in 0 until element.childNodes.length) {
            if (element.childNodes.item(i).nodeType == Node.ELEMENT_NODE) {
                context.report(
                    ISSUE,
                    element,
                    context.getElementLocation(element),
                    "An AdapterView such as a `${element.tagName}` should not have children in XML; configure its data via an adapter in code."
                )
                return
            }
        }
    }

    companion object {
        private val ADAPTER_VIEW_TAGS = listOf(
            "ListView",
            "GridView",
            "Spinner",
            "Gallery",
            "StackView",
            "AdapterViewFlipper",
            "AdapterViewAnimator",
            "ExpandableListView"
        )

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "AdapterViewChildren",
            briefDescription = "AdapterView cannot have children in XML",
            explanation = """
                `AdapterView` subclasses such as `ListView`, `GridView`, and `Spinner` are
                populated from code using an adapter. Adding child views directly in the
                XML layout has no effect and is almost certainly a mistake.

                Reference: https://developer.android.com/reference/android/widget/AdapterView.html
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                ChildCountDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}
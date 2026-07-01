package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element
import org.w3c.dom.Node

class ChildCountDetector : LayoutDetector() {

    companion object {
        private val IMPLEMENTATION = Implementation(
            ChildCountDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "AdapterViewChildren",
            briefDescription = "`AdapterView` cannot have children in XML",
            explanation =
                "An `AdapterView` such as a `ListView` must be configured with data from " +
                    "Java code, such as a `ListAdapter`. It cannot have child views declared " +
                    "in XML. If you need a non-empty `AdapterView` for layout purposes, " +
                    "consider using a `LinearLayout` instead.",
            category = Category.CORRECTNESS,
            priority = 10,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        /** AdapterView subclasses that are commonly used in XML layouts */
        private val ADAPTER_VIEW_TYPES = listOf(
            "AdapterView",
            "ListView",
            "GridView",
            "Spinner",
            "Gallery",
            "ExpandableListView",
            "AbsListView",
            "AbsSpinner",
            "AdapterViewAnimator",
            "AdapterViewFlipper",
            "StackView",
        )
    }

    override fun getApplicableElements(): Collection<String> = ADAPTER_VIEW_TYPES

    override fun visitElement(context: XmlContext, element: Element) {
        val childNodes = element.childNodes
        val childCount = childNodes.length
        if (childCount == 0) return

        // Check whether there are any actual element children (ignore text/whitespace nodes)
        var hasElementChild = false
        for (i in 0 until childCount) {
            val child = childNodes.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                hasElementChild = true
                break
            }
        }

        if (hasElementChild) {
            context.report(
                issue = ISSUE,
                element = element,
                location = context.getNameLocation(element),
                message = "A `${element.localName ?: element.tagName}` should have no children " +
                    "declared in XML: `AdapterView` children are instead supplied dynamically " +
                    "using an `Adapter`",
            )
        }
    }
}
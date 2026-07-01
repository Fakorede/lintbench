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

    override fun getApplicableElements(): Collection<String> {
        return listOf(
            "AdapterView",
            "ListView",
            "GridView",
            "Spinner",
            "Gallery",
            "ExpandableListView",
            "StackView",
            "AdapterViewFlipper"
        )
    }

    override fun visitElement(context: XmlContext, element: Element) {
        var child = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                val childElement = child as Element
                context.report(
                    ISSUE,
                    childElement,
                    context.getNameLocation(childElement),
                    "A list view or spinner cannot have children defined in XML"
                )
            }
            child = child.nextSibling
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "AdapterViewChildren",
            briefDescription = "AdapterViews cannot have children in XML",
            explanation = "An `AdapterView` such as a `ListView` must be configured with data " +
                    "from Java code, such as a `ListAdapter`. Defining children in XML " +
                    "is not supported and will result in a runtime exception.",
            category = Category.CORRECTNESS,
            priority = 10,
            severity = Severity.WARNING,
            implementation = Implementation(
                ChildCountDetector::class.java,
                Scope.LAYOUT_RESOURCE_SCOPE
            )
        )
    }
}
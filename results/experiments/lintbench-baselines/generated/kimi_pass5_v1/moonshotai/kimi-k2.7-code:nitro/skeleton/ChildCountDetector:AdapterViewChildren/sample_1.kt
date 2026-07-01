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
            explanation = "AdapterView subclasses (such as ListView, GridView, Spinner, " +
                    "Gallery, StackView, and AdapterViewFlipper) must be populated with data " +
                    "from code, for example using a ListAdapter. They cannot declare child " +
                    "views directly in the XML layout.",
            category = Category.CORRECTNESS,
            priority = 10,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private val ADAPTER_VIEWS = listOf(
            "ListView",
            "GridView",
            "Spinner",
            "Gallery",
            "StackView",
            "AdapterViewFlipper",
            "ExpandableListView",
        )
    }

    override fun getApplicableElements(): Collection<String>? = ADAPTER_VIEWS

    override fun visitElement(context: XmlContext, element: Element) {
        if (hasElementChild(element)) {
            context.report(
                ISSUE,
                context.getElementLocation(element),
                "A list/grid should have no children in XML",
            )
        }
    }

    private fun hasElementChild(element: Element): Boolean {
        val children = element.childNodes
        for (i in 0 until children.length) {
            if (children.item(i).nodeType == Node.ELEMENT_NODE) {
                return true
            }
        }
        return false
    }
}
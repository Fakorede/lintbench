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
        @JvmField
        val ISSUE = Issue.create(
            id = "AdapterViewChildren",
            briefDescription = "AdapterView cannot have children in XML",
            explanation = """
                An `AdapterView` such as a `ListView` must be configured with data \
                from Java code, such as a `ListAdapter`.
                """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 10,
            severity = Severity.ERROR,
            implementation = Implementation(
                ChildCountDetector::class.java,
                Scope.LAYOUT_RESOURCE_FILES
            )
        )
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf(
            "AdapterView",
            "android.widget.AdapterView",
            "ListView",
            "android.widget.ListView",
            "GridView",
            "android.widget.GridView",
            "Spinner",
            "android.widget.Spinner",
            "Gallery",
            "android.widget.Gallery",
            "StackView",
            "android.widget.StackView",
            "AdapterViewAnimator",
            "android.widget.AdapterViewAnimator",
            "AdapterViewFlipper",
            "android.widget.AdapterViewFlipper",
            "ExpandableListView",
            "android.widget.ExpandableListView",
            "AbsListView",
            "android.widget.AbsListView",
            "AbsSpinner",
            "android.widget.AbsSpinner"
        )
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                val childElement = child as Element
                val tagName = childElement.tagName
                if (tagName != "requestFocus" && tagName != "tag") {
                    context.report(
                        ISSUE,
                        childElement,
                        context.getNameLocation(childElement),
                        "A list view or spinner should not have any children declared in XML"
                    )
                }
            }
        }
    }
}
package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class ChildCountDetector : LayoutDetector() {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "AdapterViewChildren",
            briefDescription = "AdapterView cannot have children in XML",
            explanation = "An `AdapterView` such as a `ListView` must be configured with data from Java code, such as a `ListAdapter`.",
            category = Category.CORRECTNESS,
            priority = 10,
            severity = Severity.WARNING,
            implementation = Implementation(
                ChildCountDetector::class.java,
                Scope.LAYOUT_SCOPE
            )
        )
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(
            "AdapterView",
            "ListView",
            "GridView",
            "Spinner",
            "Gallery",
            "AdapterViewFlipper",
            "StackView",
            "ExpandableListView",
            "android.widget.AdapterView",
            "android.widget.ListView",
            "android.widget.GridView",
            "android.widget.Spinner",
            "android.widget.Gallery",
            "android.widget.AdapterViewFlipper",
            "android.widget.StackView",
            "android.widget.ExpandableListView"
        )
    }

    override fun visitElement(context: XmlContext, element: Element) {
        var childCount = 0
        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child is Element) {
                val tagName = child.tagName
                if (tagName != "requestFocus" && tagName != "tag") {
                    childCount++
                }
            }
        }

        if (childCount > 0) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "A list view or spinner should not have children declared in XML"
            )
        }
    }
}
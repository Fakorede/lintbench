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
        private val IMPLEMENTATION = Implementation(
            ChildCountDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "AdapterViewChildren",
            briefDescription = "`AdapterView` cannot have children in XML",
            explanation = "An `AdapterView` such as a `ListView` must be configured with data from Java code, such as a `ListAdapter`.",
            category = Category.CORRECTNESS,
            priority = 10,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf(
            "ListView",
            "GridView",
            "Spinner",
            "Gallery",
            "ExpandableListView",
            "StackView",
            "AdapterViewFlipper",
            "AdapterViewAnimator",
            "android.widget.ListView",
            "android.widget.GridView",
            "android.widget.Spinner",
            "android.widget.Gallery",
            "android.widget.ExpandableListView",
            "android.widget.StackView",
            "android.widget.AdapterViewFlipper",
            "android.widget.AdapterViewAnimator"
        )
    }

    override fun visitElement(context: XmlContext, element: Element) {
        var child = element.firstChild
        while (child != null) {
            if (child is Element) {
                val tagName = element.tagName
                context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "A $tagName cannot have children in XML"
                )
                return
            }
            child = child.nextSibling
        }
    }
}
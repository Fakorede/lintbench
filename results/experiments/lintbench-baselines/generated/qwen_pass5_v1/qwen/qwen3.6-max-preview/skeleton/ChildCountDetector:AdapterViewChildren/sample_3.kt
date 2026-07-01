package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
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

        private val ADAPTER_VIEW_TAGS = setOf(
            "AdapterView", "ListView", "GridView", "Spinner", "Gallery",
            "StackView", "AdapterViewFlipper", "ExpandableListView",
            "android.widget.AdapterView", "android.widget.ListView",
            "android.widget.GridView", "android.widget.Spinner",
            "android.widget.Gallery", "android.widget.StackView",
            "android.widget.AdapterViewFlipper", "android.widget.ExpandableListView"
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "AdapterViewChildren",
            briefDescription = "`AdapterView` cannot have children in XML",
            explanation = "An `AdapterView` such as a `ListView` must be configured with data from Java code, " +
                    "such as a `ListAdapter`. Adding child views directly in XML layout files is not supported " +
                    "and will result in a runtime exception or the children being ignored.",
            category = Category.CORRECTNESS,
            priority = 10,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableElements(): Collection<String>? = ADAPTER_VIEW_TAGS

    override fun visitElement(context: XmlContext, element: Element) {
        val children = element.childNodes
        for (i in 0 until children.length) {
            if (children.item(i) is Element) {
                context.report(
                    ISSUE,
                    context.getLocation(element),
                    "`AdapterView` cannot have children in XML"
                )
                return
            }
        }
    }
}
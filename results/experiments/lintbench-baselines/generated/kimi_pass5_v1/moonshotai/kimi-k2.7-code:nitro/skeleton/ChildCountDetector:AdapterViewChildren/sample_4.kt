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
import org.w3c.dom.Node

class ChildCountDetector : LayoutDetector() {

    companion object {
        private const val LIST_VIEW = "android.widget.ListView"
        private const val GRID_VIEW = "android.widget.GridView"
        private const val SPINNER = "android.widget.Spinner"
        private const val GALLERY = "android.widget.Gallery"
        private const val EXPANDABLE_LIST_VIEW = "android.widget.ExpandableListView"
        private const val ADAPTER_VIEW_FLIPPER = "android.widget.AdapterViewFlipper"
        private const val STACK_VIEW = "android.widget.StackView"

        private val ADAPTER_VIEW_CLASSES = listOf(
            LIST_VIEW,
            GRID_VIEW,
            SPINNER,
            GALLERY,
            EXPANDABLE_LIST_VIEW,
            ADAPTER_VIEW_FLIPPER,
            STACK_VIEW
        )

        private val IMPLEMENTATION = Implementation(
            ChildCountDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "AdapterViewChildren",
            briefDescription = "`AdapterView` cannot have children in XML",
            explanation = "An `AdapterView` such as a `ListView` must be configured with data from code, such as a `ListAdapter`. Defining static child views in XML is not supported.",
            category = Category.CORRECTNESS,
            priority = 10,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION
        )
    }

    override fun getApplicableElements(): Collection<String> = ADAPTER_VIEW_CLASSES

    override fun visitElement(context: XmlContext, element: Element) {
        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                val message = "`${element.tagName}` cannot have children in XML"
                context.report(ISSUE, context.getLocation(child as Element), message)
            }
        }
    }
}